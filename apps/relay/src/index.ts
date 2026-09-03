import { SignJWT, importPKCS8 } from 'jose';

export interface Env {
	RELAY_ROOM: DurableObjectNamespace;
	DB: D1Database;
	FIREBASE_SERVICE_ACCOUNT: string;
}

type ClientType = "desktop" | "mobile";

interface Session {
	socket: WebSocket;
	type: ClientType;
}

export class RelayRoom implements DurableObject {
	private state: DurableObjectState;
	private sessions: Set<Session>;
	private env: Env;
	private rateLimits: Map<Session, { count: number, lastReset: number }>;

	constructor(state: DurableObjectState, env: Env) {
		this.state = state;
		this.env = env;
		this.sessions = new Set();
		this.rateLimits = new Map();
	}

	async fetch(request: Request): Promise<Response> {
		if (request.headers.get("Upgrade") !== "websocket") {
			return new Response("Expected Upgrade: websocket", { status: 426 });
		}

		const url = new URL(request.url);
		const clientType = url.searchParams.get("type") as ClientType || "desktop";
		const pairId = url.searchParams.get("pairId");

		const [client, server] = Object.values(new WebSocketPair());
		const session: Session = { socket: server, type: clientType };
		this.sessions.add(session);
		server.accept();

		server.addEventListener("message", async event => {
			// Basic Rate Limiting
			const now = Date.now();
			let limit = this.rateLimits.get(session) || { count: 0, lastReset: now };
			if (now - limit.lastReset > 1000) {
				limit = { count: 0, lastReset: now };
			}
			if (limit.count >= 20) {
				server.close(1008, "Rate limit exceeded");
				return;
			}
			limit.count++;
			this.rateLimits.set(session, limit);

			try {
				const data = JSON.parse(event.data as string);
				
				// Handle mobile registration
				if (clientType === "mobile" && data.type === "fcm_register" && data.fcmToken && pairId) {
					await this.env.DB.prepare(
						"INSERT INTO push_tokens (pair_id, token) VALUES (?, ?) ON CONFLICT(pair_id) DO UPDATE SET token = excluded.token, updated_at = CURRENT_TIMESTAMP"
					).bind(pairId, data.fcmToken).run();
					return;
				}

				// If desktop sends a permission request, check if mobile is connected
				if (clientType === "desktop" && data.id && data.permission) {
					let mobileConnected = false;
					for (const s of this.sessions) {
						if (s.type === "mobile") {
							mobileConnected = true;
							break;
						}
					}

					if (!mobileConnected && pairId) {
						const result = await this.env.DB.prepare(
							"SELECT token FROM push_tokens WHERE pair_id = ?"
						).bind(pairId).first<{token: string}>();
						
						if (result?.token && this.env.FIREBASE_SERVICE_ACCOUNT) {
							await this.sendPushNotification(result.token, data.id);
						}
					}
				}
			} catch (e) {
				// Ignore non-JSON messages or just pass them through
			}

			// Broadcast to all other connections in this room
			for (const s of this.sessions) {
				if (s !== session) {
					s.socket.send(event.data);
				}
			}
		});

		server.addEventListener("close", () => {
			this.sessions.delete(session);
			this.rateLimits.delete(session);
		});

		return new Response(null, {
			status: 101,
			webSocket: client,
		});
	}

	private async sendPushNotification(fcmToken: string, requestId: string) {
		try {
			const credentials = JSON.parse(this.env.FIREBASE_SERVICE_ACCOUNT);
			const privateKey = await importPKCS8(credentials.private_key, 'RS256');

			const jwt = await new SignJWT({
				iss: credentials.client_email,
				sub: credentials.client_email,
				aud: "https://oauth2.googleapis.com/token",
				scope: "https://www.googleapis.com/auth/firebase.messaging"
			})
			.setProtectedHeader({ alg: 'RS256' })
			.setIssuedAt()
			.setExpirationTime('1h')
			.sign(privateKey);

			const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
				method: "POST",
				headers: { "Content-Type": "application/x-www-form-urlencoded" },
				body: new URLSearchParams({
					grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
					assertion: jwt
				})
			});

			const tokenData = await tokenRes.json() as { access_token?: string };
			if (!tokenData.access_token) return;

			const url = `https://fcm.googleapis.com/v1/projects/${credentials.project_id}/messages:send`;
			const payload = {
				message: {
					token: fcmToken,
					data: {
						requestId: requestId
					}
				}
			};

			await fetch(url, {
				method: "POST",
				headers: {
					"Authorization": `Bearer ${tokenData.access_token}`,
					"Content-Type": "application/json"
				},
				body: JSON.stringify(payload)
			});
		} catch (e) {
			console.error("Failed to send push notification", e);
		}
	}
}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		const url = new URL(request.url);
		
		if (url.pathname.startsWith("/ws/desktop/") || url.pathname.startsWith("/ws/mobile/")) {
			const pairId = url.pathname.split("/").pop();
			if (!pairId) {
				return new Response("Missing pair ID", { status: 400 });
			}
			
			const clientType = url.pathname.startsWith("/ws/desktop/") ? "desktop" : "mobile";
			// Rewrite URL to pass client type and pair ID to the Durable Object
			const doUrl = new URL(request.url);
			doUrl.searchParams.set("type", clientType);
			doUrl.searchParams.set("pairId", pairId);
			
			const id = env.RELAY_ROOM.idFromName(pairId);
			const stub = env.RELAY_ROOM.get(id);
			return stub.fetch(new Request(doUrl.toString(), request));
		}
		
		return new Response("AgentApprove Relay OK", { status: 200 });
	}
};
