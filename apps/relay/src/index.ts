/**
 * HandOff relay.
 *
 * One Durable Object per pair id acts as the rendezvous point between a desktop daemon and a
 * phone. Three properties matter more than anything else here, and none of them held in v1:
 *
 *  1. Requests survive an offline phone. Every request is written to Durable Object storage before
 *     it is forwarded, and replayed when a phone attaches. v1 was broadcast-only, so a request
 *     sent while the phone was asleep was gone forever.
 *  2. Only the paired devices can join. Both sockets must present the pairing token minted by
 *     `handoff --pair`. v1 accepted anyone who knew the pair id, and let any client claiming to be
 *     a phone overwrite the pair's push token.
 *  3. Nothing waits forever. An alarm sweeps requests past their deadline and tells the desktop,
 *     so the agent is released instead of blocking on an abandoned approval.
 *
 * The object also uses the WebSocket Hibernation API so an idle pair costs no memory, and caches
 * its Google OAuth token so a push does not pay for a fresh RS256 assertion every time.
 */

import { SignJWT, importPKCS8 } from 'jose';

export interface Env {
	RELAY_ROOM: DurableObjectNamespace;
	FIREBASE_SERVICE_ACCOUNT?: string;
}

type ClientType = 'desktop' | 'mobile';

const PROTOCOL_VERSION = '2.0';

const FrameType = {
	REQUEST: 'request',
	DECISION: 'decision',
	SESSION_INFO: 'session_info',
	FCM_REGISTER: 'fcm_register',
	PAIR_HELLO: 'pair_hello',
	ABORT: 'abort',
	ACK: 'ack',
	EXPIRED: 'expired',
	CANCEL: 'cancel',
	PRESENCE: 'presence',
} as const;

/** Storage keys. Prefixes are load-bearing: the sweep and replay paths both list by prefix. */
const StorageKey = {
	AUTH: 'auth',
	FCM_TOKEN: 'fcmToken',
	MOBILE_KEY: 'mobileKey',
	OAUTH: 'oauth',
	PIN_DATA: 'pin_data',
	PENDING_REQUEST_PREFIX: 'req:',
	PENDING_DECISION_PREFIX: 'dec:',
} as const;

const Limits = {
	/** Largest frame accepted. A frame carries a diff, so the ceiling is generous but finite. */
	MAX_FRAME_BYTES: 512 * 1024,
	/** Bound on undelivered requests per pair, so a runaway agent cannot fill storage. */
	MAX_PENDING_REQUESTS: 200,
	/** Frames per socket per window. */
	RATE_LIMIT_FRAMES: 40,
	RATE_LIMIT_WINDOW_MS: 1_000,
	/** Requests with no explicit deadline still get swept eventually. */
	DEFAULT_TTL_MS: 300_000,
	/** Google access tokens live an hour; refresh a little early. */
	OAUTH_SKEW_MS: 5 * 60_000,
} as const;

interface AuthRecord {
	tokenHash: string;
	claimedAt: number;
}

interface PendingRequest {
	requestId: string;
	frame: string;
	createdAt: number;
	expiresAt: number;
	risk: string;
	kind: string;
	agent: string;
	workspace: string;
}

interface PendingDecision {
	requestId: string;
	frame: string;
	storedAt: number;
}

interface SocketMeta {
	type: ClientType;
	pairId: string;
}

interface OAuthCache {
	accessToken: string;
	expiresAt: number;
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

async function sha256Hex(input: string): Promise<string> {
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Length-independent comparison, so a wrong token cannot be narrowed down by timing. */
function timingSafeEqual(a: string, b: string): boolean {
	if (a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) {
		diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	}
	return diff === 0;
}

function envelope(type: string, payload: unknown, requestId?: string): string {
	const frame: Record<string, unknown> = { v: PROTOCOL_VERSION, type };
	if (requestId !== undefined) frame.requestId = requestId;
	if (payload !== undefined && payload !== null) frame.payload = payload;
	return JSON.stringify(frame);
}

function readString(source: unknown, ...path: string[]): string | undefined {
	let cursor: any = source;
	for (const key of path) {
		if (cursor === null || typeof cursor !== 'object') return undefined;
		cursor = cursor[key];
	}
	return typeof cursor === 'string' ? cursor : undefined;
}

/**
 * Extracts the request id and the metadata a push notification needs.
 *
 * Reads both the v2 envelope shape and a bare v1 request object, so a desktop mid-upgrade is not
 * silently dropped.
 */
function describeRequest(parsed: unknown): Omit<PendingRequest, 'frame' | 'createdAt'> | null {
	const payload: any =
		(parsed as any)?.payload && typeof (parsed as any).payload === 'object' ? (parsed as any).payload : parsed;

	const requestId = readString(parsed, 'requestId') ?? readString(payload, 'id');
	if (!requestId) return null;
	if (!payload || typeof payload !== 'object' || typeof payload.permission !== 'object') return null;

	const explicitExpiry = typeof payload.expiresAtEpochMs === 'number' ? payload.expiresAtEpochMs : undefined;
	const parsedIsoExpiry = typeof payload.expiresAt === 'string' ? Date.parse(payload.expiresAt) : NaN;
	const expiresAt =
		explicitExpiry ??
		(Number.isFinite(parsedIsoExpiry) ? parsedIsoExpiry : Date.now() + Limits.DEFAULT_TTL_MS);

	return {
		requestId,
		expiresAt,
		risk: readString(payload, 'risk', 'level') ?? 'unknown',
		kind: readString(payload, 'permission', 'type') ?? 'unknown',
		agent: readString(payload, 'agent', 'name') ?? 'Agent',
		workspace: readString(payload, 'session', 'project') ?? '',
	};
}

// ---------------------------------------------------------------------------------------------
// Durable Object
// ---------------------------------------------------------------------------------------------

export class RelayRoom implements DurableObject {
	private state: DurableObjectState;
	private env: Env;

	/**
	 * Per-socket frame counters. Intentionally in memory: hibernation only happens while the room
	 * is idle, and a flood keeps it awake, so a reset on eviction cannot be used to bypass the cap.
	 */
	private rateLimits: WeakMap<WebSocket, { count: number; windowStart: number }>;

	constructor(state: DurableObjectState, env: Env) {
		this.state = state;
		this.env = env;
		this.rateLimits = new WeakMap();

		// Answers a text "ping" without waking the object, so keepalives cost nothing.
		this.state.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
	}

	async fetch(request: Request): Promise<Response> {
		const url = new URL(request.url);
		const pairId = url.searchParams.get('pairId');
		const token = url.searchParams.get('token') ?? '';

		if (!pairId) {
			return Response.json({ error: 'missing_pair_id', message: 'Missing pair id' }, { status: 400 });
		}

		// Plain-HTTP status route. A rejected WebSocket upgrade reaches Ktor and okhttp as an opaque
		// exception, so the *reason* a client cannot join — nobody has claimed this pair, or the
		// token does not match — was unreachable by the code that has to explain it to the user.
		if (url.searchParams.get('probe') === 'status') {
			return this.statusResponse(token);
		}

		if (url.searchParams.get('probe') === 'pin_resolve') {
			const pinData = await this.state.storage.get<any>(StorageKey.PIN_DATA);
			if (!pinData) {
				return Response.json({ ok: false, error: 'pin_not_found', message: 'PIN expired or not found' }, { status: 404 });
			}
			return Response.json({ ok: true, payload: pinData });
		}

		if (request.method === 'POST' && url.searchParams.get('action') === 'pin_register') {
			try {
				const body = (await request.json()) as any;
				await this.state.storage.put(StorageKey.PIN_DATA, body);
				await this.state.storage.setAlarm(Date.now() + 10 * 60 * 1000);
				return Response.json({ ok: true });
			} catch (err: any) {
				return Response.json({ ok: false, error: err?.message }, { status: 400 });
			}
		}

		if (request.headers.get('Upgrade') !== 'websocket') {
			return new Response('Expected Upgrade: websocket', { status: 426 });
		}

		const clientType = (url.searchParams.get('type') as ClientType) ?? 'desktop';

		const authorized = await this.authorize(clientType, token);
		if (!authorized.ok) {
			return Response.json({ error: authorized.error, message: authorized.message }, { status: 401 });
		}

		const pair = new WebSocketPair();
		const client = pair[0];
		const server = pair[1];

		// Only one socket per side. A phone that changed networks would otherwise be locked out by
		// its own zombie socket, so the newcomer wins and the stale peer is closed.
		for (const existing of this.state.getWebSockets(clientType)) {
			try {
				existing.close(1012, 'Replaced by a newer connection');
			} catch {
				// Already gone.
			}
		}

		const meta: SocketMeta = { type: clientType, pairId };
		this.state.acceptWebSocket(server, [clientType]);
		server.serializeAttachment(meta);

		// Replay happens after the handshake returns, so the client is listening when it arrives.
		this.state.waitUntil(this.onSocketReady(server, meta));

		return new Response(null, { status: 101, webSocket: client });
	}

	/**
	 * Reports what this pair room knows, for `handoff --doctor` and for the phone's pairing check.
	 *
	 * An unclaimed room answers 200 rather than 401: "no desktop has claimed this pair yet" is a
	 * legitimate, actionable answer, and it is exactly the state a user hits when they scan a code
	 * without a daemon running.
	 */
	private async statusResponse(token: string): Promise<Response> {
		const existing = await this.state.storage.get<AuthRecord>(StorageKey.AUTH);
		if (!existing) {
			return Response.json({ claimed: false, phoneOnline: false, desktopOnline: false });
		}
		if (!token || !timingSafeEqual(await sha256Hex(token), existing.tokenHash)) {
			return Response.json(
				{
					error: 'invalid_token',
					message: 'This pair id is claimed by a different device or an older pairing secret.',
				},
				{ status: 401 }
			);
		}
		return Response.json({
			claimed: true,
			phoneOnline: this.isOnline('mobile'),
			desktopOnline: this.isOnline('desktop'),
		});
	}

	/**
	 * Trust-on-first-use for the pairing token.
	 *
	 * The first desktop to connect claims the room by recording a hash of its token; every later
	 * socket, desktop or phone, must present the same token. The token is 256 bits and is minted at
	 * the same moment the pair id is, so the unclaimed window is the gap between generating a QR
	 * code and the daemon's first connection.
	 */
	private async authorize(
		clientType: ClientType,
		token: string
	): Promise<{ ok: true } | { ok: false; error: string; message: string }> {
		const existing = await this.state.storage.get<AuthRecord>(StorageKey.AUTH);

		if (!existing) {
			if (clientType !== 'desktop') {
				return {
					ok: false,
					error: 'unclaimed',
					message:
						'No desktop has claimed this pair yet. Run `handoff --pair` on your computer and keep it open while you scan.',
				};
			}
			if (token.length < 32) {
				return {
					ok: false,
					error: 'weak_token',
					message: 'Pairing token is too short to be a pairing secret.',
				};
			}
			await this.state.storage.put<AuthRecord>(StorageKey.AUTH, {
				tokenHash: await sha256Hex(token),
				claimedAt: Date.now(),
			});
			return { ok: true };
		}

		if (!token) {
			return { ok: false, error: 'missing_token', message: 'This pairing code carries no relay token.' };
		}
		if (!timingSafeEqual(await sha256Hex(token), existing.tokenHash)) {
			return {
				ok: false,
				error: 'invalid_token',
				message:
					'This pair id is claimed by a different device or an older pairing secret. Run `handoff --rotate-pair`, then pair again.',
			};
		}
		return { ok: true };
	}

	/** Hands a freshly attached socket whatever it missed while it was away. */
	private async onSocketReady(socket: WebSocket, meta: SocketMeta): Promise<void> {
		try {
			if (meta.type === 'mobile') {
				await this.replayPendingRequests(socket);
			} else {
				await this.replayForDesktop(socket);
			}
			// Both sides need to know the other is there: the desktop so it can stop waiting on an
			// absent phone, the phone so it can say whether a workstation is actually listening.
			this.announcePresence();
		} catch (error) {
			console.error('replay failed', error);
		}
	}

	private isOnline(target: ClientType, excluding?: WebSocket): boolean {
		return this.state.getWebSockets(target).some((socket) => socket !== excluding);
	}

	/**
	 * Tells each side whether its peer currently holds a socket.
	 *
	 * [excluding] is the socket that is in the middle of closing. The runtime still lists it while
	 * its close handler runs, so counting it would announce a peer that has just left — exactly
	 * inverting the signal at the one moment it matters.
	 */
	private announcePresence(excluding?: WebSocket): void {
		const phoneOnline = this.isOnline('mobile', excluding);
		const desktopOnline = this.isOnline('desktop', excluding);
		const frame = envelope(FrameType.PRESENCE, { phoneOnline, desktopOnline });
		for (const socket of [...this.state.getWebSockets('desktop'), ...this.state.getWebSockets('mobile')]) {
			if (socket === excluding) continue;
			this.trySend(socket, frame);
		}
	}

	private async replayPendingRequests(socket: WebSocket): Promise<void> {
		const now = Date.now();
		const pending = await this.state.storage.list<PendingRequest>({
			prefix: StorageKey.PENDING_REQUEST_PREFIX,
		});

		const expired: string[] = [];
		const live: PendingRequest[] = [];
		for (const [key, record] of pending) {
			if (record.expiresAt <= now) {
				expired.push(key);
			} else {
				live.push(record);
			}
		}

		if (expired.length > 0) {
			await this.state.storage.delete(expired);
		}

		live.sort((a, b) => a.createdAt - b.createdAt);
		for (const record of live) {
			this.trySend(socket, record.frame);
		}
	}

	/** A decision taken while the desktop was disconnected, plus the phone's signing key. */
	private async replayForDesktop(socket: WebSocket): Promise<void> {
		const mobileKey = await this.state.storage.get<string>(StorageKey.MOBILE_KEY);
		if (mobileKey) {
			this.trySend(socket, mobileKey);
		}

		const decisions = await this.state.storage.list<PendingDecision>({
			prefix: StorageKey.PENDING_DECISION_PREFIX,
		});
		if (decisions.size === 0) return;

		const keys: string[] = [];
		for (const [key, record] of decisions) {
			this.trySend(socket, record.frame);
			keys.push(key);
		}
		await this.state.storage.delete(keys);
	}

	async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
		const meta = socket.deserializeAttachment() as SocketMeta | null;
		if (!meta) {
			socket.close(1011, 'Unknown socket');
			return;
		}

		if (typeof message !== 'string') {
			return; // The protocol is text-only.
		}
		if (message.length > Limits.MAX_FRAME_BYTES) {
			socket.close(1009, 'Frame too large');
			return;
		}
		if (!this.allowFrame(socket)) {
			socket.close(1008, 'Rate limit exceeded');
			return;
		}

		let parsed: unknown;
		try {
			parsed = JSON.parse(message);
		} catch {
			return; // Not our protocol; drop rather than relay unparsed bytes.
		}

		const type = readString(parsed, 'type');

		if (meta.type === 'mobile') {
			await this.handleMobileFrame(socket, type, parsed, message);
		} else {
			await this.handleDesktopFrame(socket, type, parsed, message);
		}
	}

	private async handleDesktopFrame(
		socket: WebSocket,
		type: string | undefined,
		parsed: unknown,
		raw: string
	): Promise<void> {
		// The agent abandoned this request: stop storing it, and tell the phone to drop the card so
		// nobody is invited to authorize something no longer being waited on.
		if (type === FrameType.CANCEL) {
			const requestId = readString(parsed, 'requestId') ?? readString(parsed, 'payload', 'requestId');
			if (requestId) {
				await this.state.storage.delete(StorageKey.PENDING_REQUEST_PREFIX + requestId);
			}
			this.broadcast('mobile', raw);
			return;
		}

		// A request must be durable before it is forwarded, so an offline phone cannot lose it.
		const looksLikeRequest = type === FrameType.REQUEST || (type === undefined && (parsed as any)?.permission);
		if (looksLikeRequest) {
			const described = describeRequest(parsed);
			if (!described) return;

			const stored = await this.storePendingRequest(described, raw);
			if (!stored) {
				this.trySend(
					socket,
					envelope(
						FrameType.ACK,
						{ requestId: described.requestId, status: 'rejected', delivered: false, pushQueued: false },
						described.requestId
					)
				);
				return;
			}

			const phones = this.state.getWebSockets('mobile');
			let delivered = false;
			for (const phone of phones) {
				delivered = this.trySend(phone, raw) || delivered;
			}

			// Whether a push can even be attempted is decided here so the ack can carry it. Without
			// this the desktop could not distinguish "nobody will ever answer" from "the phone is
			// asleep and about to be woken", and had to assume the latter for the request's full
			// five-minute deadline in both cases.
			const pushable = !delivered && (await this.canPush());
			this.trySend(
				socket,
				envelope(
					FrameType.ACK,
					{
						requestId: described.requestId,
						status: 'stored',
						delivered,
						pushQueued: pushable,
						phoneOnline: delivered,
					},
					described.requestId
				)
			);

			if (!delivered && pushable) {
				this.state.waitUntil(this.notifyPhone(described));
			}
			return;
		}

		// Everything else (session_info, abort) is transient state for the phone.
		this.broadcast('mobile', raw);
	}

	/** Whether a push notification could actually be sent for this pair. */
	private async canPush(): Promise<boolean> {
		if (!this.env.FIREBASE_SERVICE_ACCOUNT) return false;
		const fcmToken = await this.state.storage.get<string>(StorageKey.FCM_TOKEN);
		return Boolean(fcmToken);
	}

	private async handleMobileFrame(
		socket: WebSocket,
		type: string | undefined,
		parsed: unknown,
		raw: string
	): Promise<void> {
		if (type === FrameType.FCM_REGISTER) {
			const fcmToken = readString(parsed, 'payload', 'fcmToken') ?? readString(parsed, 'fcmToken');
			if (fcmToken) {
				await this.state.storage.put(StorageKey.FCM_TOKEN, fcmToken);
			}
			return;
		}

		if (type === FrameType.PAIR_HELLO) {
			// Persisted so a desktop that reconnects later still learns the phone's signing key.
			await this.state.storage.put(StorageKey.MOBILE_KEY, raw);
			this.broadcast('desktop', raw);
			return;
		}

		const looksLikeDecision = type === FrameType.DECISION || (type === undefined && (parsed as any)?.decision);
		if (looksLikeDecision) {
			const requestId = readString(parsed, 'requestId') ?? readString(parsed, 'payload', 'requestId');
			if (requestId) {
				await this.state.storage.delete(StorageKey.PENDING_REQUEST_PREFIX + requestId);
			}

			const delivered = this.broadcast('desktop', raw);
			if (!delivered && requestId) {
				// The daemon is momentarily gone; hold the decision so the agent is still released.
				await this.state.storage.put<PendingDecision>(StorageKey.PENDING_DECISION_PREFIX + requestId, {
					requestId,
					frame: raw,
					storedAt: Date.now(),
				});
			}
			this.trySend(
				socket,
				envelope(
					FrameType.ACK,
					{
						requestId: requestId ?? '',
						status: delivered ? 'delivered' : 'queued',
						delivered,
						desktopOnline: delivered,
					},
					requestId
				)
			);
			return;
		}

		this.broadcast('desktop', raw);
	}

	private async storePendingRequest(
		described: Omit<PendingRequest, 'frame' | 'createdAt'>,
		frame: string
	): Promise<boolean> {
		const existing = await this.state.storage.list<PendingRequest>({
			prefix: StorageKey.PENDING_REQUEST_PREFIX,
			limit: Limits.MAX_PENDING_REQUESTS + 1,
		});
		if (existing.size > Limits.MAX_PENDING_REQUESTS) {
			return false;
		}

		await this.state.storage.put<PendingRequest>(StorageKey.PENDING_REQUEST_PREFIX + described.requestId, {
			...described,
			frame,
			createdAt: Date.now(),
		});
		await this.scheduleSweep(described.expiresAt);
		return true;
	}

	/** Keeps a single alarm armed at the earliest deadline we still care about. */
	private async scheduleSweep(candidateAt: number): Promise<void> {
		const current = await this.state.storage.getAlarm();
		if (current === null || candidateAt < current) {
			await this.state.storage.setAlarm(candidateAt);
		}
	}

	/**
	 * Releases every request whose deadline has passed.
	 *
	 * Without this the agent would block on its tool call indefinitely whenever the user simply
	 * never looked at their phone: `expiresAt` was written by the desktop and read by nobody.
	 */
	async alarm(): Promise<void> {
		const now = Date.now();
		const pending = await this.state.storage.list<PendingRequest>({
			prefix: StorageKey.PENDING_REQUEST_PREFIX,
		});

		const doomed: string[] = [];
		let nextDeadline: number | null = null;

		for (const [key, record] of pending) {
			if (record.expiresAt <= now) {
				doomed.push(key);
				const frame = envelope(FrameType.EXPIRED, { requestId: record.requestId, reason: 'expired' }, record.requestId);
				const delivered = this.broadcast('desktop', frame);
				if (!delivered) {
					await this.state.storage.put<PendingDecision>(
						StorageKey.PENDING_DECISION_PREFIX + record.requestId,
						{ requestId: record.requestId, frame, storedAt: now }
					);
				}
				this.broadcast('mobile', frame);
			} else if (nextDeadline === null || record.expiresAt < nextDeadline) {
				nextDeadline = record.expiresAt;
			}
		}

		if (doomed.length > 0) {
			await this.state.storage.delete(doomed);
		}
		if (nextDeadline !== null) {
			await this.state.storage.setAlarm(nextDeadline);
		}
	}

	async webSocketClose(socket: WebSocket): Promise<void> {
		this.rateLimits.delete(socket);
		this.announcePresence(socket);
	}

	async webSocketError(socket: WebSocket): Promise<void> {
		this.rateLimits.delete(socket);
		this.announcePresence(socket);
	}

	private allowFrame(socket: WebSocket): boolean {
		const now = Date.now();
		const bucket = this.rateLimits.get(socket);
		if (!bucket || now - bucket.windowStart > Limits.RATE_LIMIT_WINDOW_MS) {
			this.rateLimits.set(socket, { count: 1, windowStart: now });
			return true;
		}
		bucket.count += 1;
		return bucket.count <= Limits.RATE_LIMIT_FRAMES;
	}

	/** Sends to every socket of one side. Returns whether at least one send succeeded. */
	private broadcast(target: ClientType, frame: string): boolean {
		let delivered = false;
		for (const socket of this.state.getWebSockets(target)) {
			delivered = this.trySend(socket, frame) || delivered;
		}
		return delivered;
	}

	private trySend(socket: WebSocket, frame: string): boolean {
		try {
			socket.send(frame);
			return true;
		} catch {
			return false;
		}
	}

	// -----------------------------------------------------------------------------------------
	// Push
	// -----------------------------------------------------------------------------------------

	private async notifyPhone(described: Omit<PendingRequest, 'frame' | 'createdAt'>): Promise<void> {
		if (!this.env.FIREBASE_SERVICE_ACCOUNT) return;

		const fcmToken = await this.state.storage.get<string>(StorageKey.FCM_TOKEN);
		if (!fcmToken) return;

		try {
			const credentials = JSON.parse(this.env.FIREBASE_SERVICE_ACCOUNT);
			const accessToken = await this.accessToken(credentials);
			if (!accessToken) return;

			const ttlSeconds = Math.max(60, Math.floor((described.expiresAt - Date.now()) / 1000));

			const response = await fetch(`https://fcm.googleapis.com/v1/projects/${credentials.project_id}/messages:send`, {
				method: 'POST',
				headers: {
					Authorization: `Bearer ${accessToken}`,
					'Content-Type': 'application/json',
				},
				body: JSON.stringify({
					message: {
						token: fcmToken,
						// Data-only, so the app renders the notification with real actions rather
						// than the system drawing a second, action-less one.
						data: {
							requestId: described.requestId,
							risk: described.risk,
							kind: described.kind,
							agent: described.agent,
							workspace: described.workspace,
						},
						android: {
							// An approval is worthless once Doze has deferred it past the deadline.
							priority: 'HIGH',
							ttl: `${ttlSeconds}s`,
						},
					},
				}),
			});

			if (!response.ok) {
				// A 404 or 403 here means the token is dead; drop it so we stop paying for retries.
				if (response.status === 404 || response.status === 403) {
					await this.state.storage.delete(StorageKey.FCM_TOKEN);
				}
				console.error('fcm send failed', response.status, await response.text());
			}
		} catch (error) {
			console.error('push notification failed', error);
		}
	}

	/**
	 * Returns a cached Google access token, minting one only when it is missing or near expiry.
	 *
	 * v1 signed a fresh RS256 assertion and made two extra round trips for every single push.
	 */
	private async accessToken(credentials: any): Promise<string | null> {
		const cached = await this.state.storage.get<OAuthCache>(StorageKey.OAUTH);
		if (cached && cached.expiresAt - Limits.OAUTH_SKEW_MS > Date.now()) {
			return cached.accessToken;
		}

		const privateKey = await importPKCS8(credentials.private_key, 'RS256');
		const assertion = await new SignJWT({
			iss: credentials.client_email,
			sub: credentials.client_email,
			aud: 'https://oauth2.googleapis.com/token',
			scope: 'https://www.googleapis.com/auth/firebase.messaging',
		})
			.setProtectedHeader({ alg: 'RS256' })
			.setIssuedAt()
			.setExpirationTime('1h')
			.sign(privateKey);

		const response = await fetch('https://oauth2.googleapis.com/token', {
			method: 'POST',
			headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
			body: new URLSearchParams({
				grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
				assertion,
			}),
		});

		const body = (await response.json()) as { access_token?: string; expires_in?: number };
		if (!body.access_token) return null;

		await this.state.storage.put<OAuthCache>(StorageKey.OAUTH, {
			accessToken: body.access_token,
			expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
		});
		return body.access_token;
	}
}

// ---------------------------------------------------------------------------------------------
// Worker entry
// ---------------------------------------------------------------------------------------------

const WS_ROUTE = /^\/ws\/(desktop|mobile)\/([A-Za-z0-9._-]{1,128})$/;
const PAIR_ROUTE = /^\/pair\/([A-Za-z0-9._-]{1,128})$/;

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);

		if (url.pathname === '/health') {
			return Response.json({ ok: true, protocol: PROTOCOL_VERSION });
		}

		// The token may arrive as a header (preferred) or a query parameter, because some WebSocket
		// clients cannot set headers on the upgrade request.
		const bearer = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '') ?? '';
		const token = bearer || url.searchParams.get('token') || '';

		// Plain-HTTP view of one pair room, so a client that cannot open a socket can still be told
		// why. Same token check as the socket, so it leaks nothing a joinable client cannot see.
		const pairRoute = PAIR_ROUTE.exec(url.pathname);
		if (pairRoute) {
			const pairId = pairRoute[1];
			const target = new URL(request.url);
			target.searchParams.set('pairId', pairId);
			target.searchParams.set('token', token);
			target.searchParams.set('probe', 'status');

			const stub = env.RELAY_ROOM.get(env.RELAY_ROOM.idFromName(pairId));
			return stub.fetch(new Request(target.toString(), { method: 'GET' }));
		}

		if (url.pathname === '/pin/register' && request.method === 'POST') {
			try {
				const clone = request.clone();
				const body = (await clone.json()) as any;
				const pin = body?.pin ? String(body.pin).trim() : null;
				if (!pin || !/^\d{6}$/.test(pin)) {
					return Response.json({ error: 'invalid_pin', message: 'PIN must be 6 digits' }, { status: 400 });
				}
				const target = new URL(request.url);
				target.searchParams.set('pairId', 'pin:' + pin);
				target.searchParams.set('action', 'pin_register');
				const stub = env.RELAY_ROOM.get(env.RELAY_ROOM.idFromName('pin:' + pin));
				return stub.fetch(new Request(target.toString(), request));
			} catch (e: any) {
				return Response.json({ error: 'bad_request', message: e?.message }, { status: 400 });
			}
		}

		if (url.pathname === '/pin/resolve') {
			const pin = url.searchParams.get('code')?.trim();
			if (!pin || !/^\d{6}$/.test(pin)) {
				return Response.json({ error: 'invalid_pin', message: 'PIN must be 6 digits' }, { status: 400 });
			}
			const target = new URL(request.url);
			target.searchParams.set('pairId', 'pin:' + pin);
			target.searchParams.set('probe', 'pin_resolve');
			const stub = env.RELAY_ROOM.get(env.RELAY_ROOM.idFromName('pin:' + pin));
			return stub.fetch(new Request(target.toString(), { method: 'GET' }));
		}

		const route = WS_ROUTE.exec(url.pathname);
		if (!route) {
			return new Response('HandOff relay', { status: 200 });
		}

		const clientType = route[1] as ClientType;
		const pairId = route[2];

		const target = new URL(request.url);
		target.searchParams.set('type', clientType);
		target.searchParams.set('pairId', pairId);
		target.searchParams.set('token', token);

		const stub = env.RELAY_ROOM.get(env.RELAY_ROOM.idFromName(pairId));
		return stub.fetch(new Request(target.toString(), request));
	},
};
