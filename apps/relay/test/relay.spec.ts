import { SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

const TOKEN = 'a'.repeat(43);
const OTHER_TOKEN = 'b'.repeat(43);

/** Collects text frames from a socket and lets a test await the next one. */
class SocketReader {
	private queue: string[] = [];
	private waiters: ((frame: string) => void)[] = [];

	constructor(readonly socket: WebSocket) {
		socket.addEventListener('message', (event) => {
			const frame = event.data as string;
			const waiter = this.waiters.shift();
			if (waiter) waiter(frame);
			else this.queue.push(frame);
		});
	}

	next(timeoutMs = 2_000): Promise<string> {
		const queued = this.queue.shift();
		if (queued !== undefined) return Promise.resolve(queued);
		return new Promise((resolve, reject) => {
			const timer = setTimeout(() => reject(new Error('timed out waiting for a frame')), timeoutMs);
			this.waiters.push((frame) => {
				clearTimeout(timer);
				resolve(frame);
			});
		});
	}

	/** Reads frames until one matches, so an interleaved ack cannot fail an assertion. */
	async nextOfType(type: string, timeoutMs = 2_000): Promise<any> {
		const deadline = Date.now() + timeoutMs;
		while (Date.now() < deadline) {
			const parsed = JSON.parse(await this.next(deadline - Date.now()));
			if (parsed.type === type) return parsed;
		}
		throw new Error(`no ${type} frame arrived`);
	}
}

async function connect(
	side: 'desktop' | 'mobile',
	pairId: string,
	token: string
): Promise<{ response: Response; reader?: SocketReader }> {
	const response = await SELF.fetch(`https://relay.test/ws/${side}/${pairId}?token=${token}`, {
		headers: { Upgrade: 'websocket' },
	});
	if (!response.webSocket) return { response };
	response.webSocket.accept();
	return { response, reader: new SocketReader(response.webSocket) };
}

function requestFrame(requestId: string, expiresInMs = 300_000) {
	return JSON.stringify({
		v: '2.0',
		type: 'request',
		requestId,
		payload: {
			id: requestId,
			protocolVersion: '2.0',
			agent: { id: 'antigravity', name: 'Antigravity' },
			session: { id: 'pair', project: 'handoff', workspace: '/home/dev/handoff' },
			permission: { type: 'terminal', command: 'npm run build' },
			risk: { level: 'high', reasons: ['builds artifacts'] },
			options: ['approve', 'deny'],
			createdAt: new Date().toISOString(),
			expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
			expiresAtEpochMs: Date.now() + expiresInMs,
		},
	});
}

function decisionFrame(requestId: string) {
	return JSON.stringify({
		v: '2.0',
		type: 'decision',
		requestId,
		payload: {
			requestId,
			decision: 'approve_once',
			issuedAt: new Date().toISOString(),
			nonce: 'nonce-1',
			deviceId: 'device-1',
			requestHash: '0'.repeat(64),
			signature: 'signature',
		},
	});
}

describe('relay health', () => {
	it('reports its protocol version', async () => {
		const response = await SELF.fetch('https://relay.test/health');
		expect(response.status).toBe(200);
		await expect(response.json()).resolves.toMatchObject({ ok: true, protocol: '2.0' });
	});

	it('rejects a non-websocket request to a ws route', async () => {
		const response = await SELF.fetch('https://relay.test/ws/desktop/pair-1');
		expect(response.status).toBe(426);
	});
});

describe('pairing token', () => {
	it('refuses a phone before any desktop has claimed the pair', async () => {
		const { response } = await connect('mobile', 'pair-unclaimed', TOKEN);
		expect(response.status).toBe(401);
	});

	it('refuses a token that does not match the claim', async () => {
		const claim = await connect('desktop', 'pair-claim', TOKEN);
		expect(claim.response.status).toBe(101);

		const intruder = await connect('mobile', 'pair-claim', OTHER_TOKEN);
		expect(intruder.response.status).toBe(401);

		const legitimate = await connect('mobile', 'pair-claim', TOKEN);
		expect(legitimate.response.status).toBe(101);
	});

	it('refuses a claim attempt with a token that is too short to be a secret', async () => {
		const { response } = await connect('desktop', 'pair-weak', 'short');
		expect(response.status).toBe(401);
	});
});

describe('request durability', () => {
	it('stores a request sent while the phone is offline and replays it on connect', async () => {
		const pairId = 'pair-replay';
		const desktop = await connect('desktop', pairId, TOKEN);
		expect(desktop.response.status).toBe(101);

		desktop.response.webSocket!.send(requestFrame('req-offline'));

		// The desktop is told the request is durable even though nothing delivered it yet.
		const ack = await desktop.reader!.nextOfType('ack');
		expect(ack.payload).toMatchObject({ requestId: 'req-offline', status: 'stored' });

		const phone = await connect('mobile', pairId, TOKEN);
		const replayed = await phone.reader!.nextOfType('request');
		expect(replayed.requestId).toBe('req-offline');
	});

	it('does not replay a request that has already been decided', async () => {
		const pairId = 'pair-decided';
		const desktop = await connect('desktop', pairId, TOKEN);
		desktop.response.webSocket!.send(requestFrame('req-decided'));
		await desktop.reader!.nextOfType('ack');

		const phone = await connect('mobile', pairId, TOKEN);
		await phone.reader!.nextOfType('request');
		phone.response.webSocket!.send(decisionFrame('req-decided'));

		const decision = await desktop.reader!.nextOfType('decision');
		expect(decision.requestId).toBe('req-decided');

		// A phone reconnecting must not be shown the answered request again.
		const rejoined = await connect('mobile', pairId, TOKEN);
		await expect(rejoined.reader!.next(500)).rejects.toThrow(/timed out/);
	});

	it('holds a decision taken while the desktop is away and delivers it on reconnect', async () => {
		const pairId = 'pair-queued';
		const desktop = await connect('desktop', pairId, TOKEN);
		desktop.response.webSocket!.send(requestFrame('req-queued'));
		await desktop.reader!.nextOfType('ack');

		const phone = await connect('mobile', pairId, TOKEN);
		await phone.reader!.nextOfType('request');

		desktop.response.webSocket!.close();
		phone.response.webSocket!.send(decisionFrame('req-queued'));

		const ack = await phone.reader!.nextOfType('ack');
		expect(ack.payload.status).toBe('queued');

		const rejoined = await connect('desktop', pairId, TOKEN);
		const decision = await rejoined.reader!.nextOfType('decision');
		expect(decision.requestId).toBe('req-queued');
	});

	it('forwards a request straight through when the phone is already attached', async () => {
		const pairId = 'pair-live';
		const desktop = await connect('desktop', pairId, TOKEN);
		const phone = await connect('mobile', pairId, TOKEN);

		desktop.response.webSocket!.send(requestFrame('req-live'));
		const received = await phone.reader!.nextOfType('request');
		expect(received.requestId).toBe('req-live');
	});
});

describe('frame handling', () => {
	it('replaces a stale socket on the same side rather than locking out the newcomer', async () => {
		const pairId = 'pair-replace';
		const first = await connect('desktop', pairId, TOKEN);
		const second = await connect('desktop', pairId, TOKEN);
		expect(second.response.status).toBe(101);

		second.response.webSocket!.send(requestFrame('req-after-replace'));
		const ack = await second.reader!.nextOfType('ack');
		expect(ack.payload.status).toBe('stored');
		expect(first.response.status).toBe(101);
	});

	it('ignores a frame that is not JSON instead of relaying it', async () => {
		const pairId = 'pair-garbage';
		const desktop = await connect('desktop', pairId, TOKEN);
		const phone = await connect('mobile', pairId, TOKEN);

		desktop.response.webSocket!.send('not json');
		await expect(phone.reader!.next(500)).rejects.toThrow(/timed out/);
	});

	it('accepts a bare v1 request frame from an older desktop', async () => {
		const pairId = 'pair-legacy';
		const desktop = await connect('desktop', pairId, TOKEN);
		const phone = await connect('mobile', pairId, TOKEN);

		desktop.response.webSocket!.send(
			JSON.stringify({
				id: 'req-legacy',
				protocolVersion: '1.0',
				agent: { id: 'cursor', name: 'Cursor' },
				session: { id: 'pair' },
				permission: { type: 'terminal', command: 'ls' },
				risk: { level: 'low', reasons: [] },
				options: ['approve', 'deny'],
				createdAt: new Date().toISOString(),
				expiresAt: new Date(Date.now() + 300_000).toISOString(),
			})
		);

		const relayed = JSON.parse(await phone.reader!.next());
		expect(relayed.id).toBe('req-legacy');
	});
});
