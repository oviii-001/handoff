# HandOff — System Design (Zero-Cost, Solo-Buildable Edition)

*Based on your proposal, adapted for: Android/Kotlin/Jetpack Compose skillset, $0 infrastructure budget, open-source, global users, solo/small-team build.*

---

## 1. Research findings that change the plan

Before designing anything, three things I found change the original proposal materially:

**1. The space is already crowded — with simple solutions, not complex ones.**
Searching current open-source projects turned up at least seven active tools solving this exact problem: `claude-remote-approver` (push via ntfy.sh), `claude-board` (local web UI + WebSocket), `steerd` (PreToolUse hook + relay), `leashd` (PWA + daemon), Telegram-bridge approvers, an Apple Watch approver, and the `ai-approve` project your proposal cites. **None of them use WebRTC, Rust, or a custom cloud control plane.** Almost all of them use one of two patterns: (a) a free pub-sub push service like `ntfy.sh`, or (b) a small self-hosted web server the phone opens as a PWA over Tailscale/local network. This tells you the market has already converged on "boringly simple" as good enough. Your differentiation shouldn't be "we built the hardest version" — it should be "we built the most polished, cross-agent, native version of the simple thing that already works."

**2. Cloudflare's free tier is real and durable for this use case.** Workers + Durable Objects on the Free plan currently give you roughly 100k requests/day on Workers, ~1M requests/month and 400K GB-seconds/month on Durable Objects, and 5GB storage — with **no egress/bandwidth charges**. That's enough to run signaling/coordination for a project with thousands of active users at literally $0, as long as you never route bulk payloads (like TURN relay traffic) through it. This confirms the proposal's Cloudflare pick, but reinforces that Durable Objects should carry *only* small JSON messages, never relayed command output.

**3. TURN relay is the one piece that isn't free at scale — so design it out of the MVP entirely.** Free TURN options exist (Open Relay: 20GB/month, ExpressTURN: 1000GB/month) but they're rate-limited and third-party; they're fine for a fallback path, not a foundation. Since your payloads are tiny (a permission request is a few hundred bytes of JSON, not video), you likely don't need TURN or even WebRTC for the MVP. A relayed WebSocket message costs the same few hundred bytes whether it goes through Cloudflare or "P2P" — the P2P architecture in the original proposal was solving a bandwidth-cost problem that a permission-approval payload doesn't actually have. Save WebRTC for a v2 feature (e.g., if you add live log streaming), not the MVP.

This lets us collapse the original 6-layer, multi-language architecture into something you can build alone in Kotlin end-to-end (Compose for Android, Kotlin for the desktop bridge via Kotlin/JVM or Kotlin/Native) plus a thin serverless relay, at genuinely $0 fixed cost.

---

## 2. Revised architecture

```
┌────────────────────────┐        ┌───────────────────────────┐
│   DESKTOP BRIDGE        │        │   CLOUDFLARE (free tier)  │
│   (Kotlin/JVM, tray app)│        │                           │
│                         │        │  Worker (HTTP + WS)       │
│  Agent adapters ────────┼───────▶│  Durable Object per       │
│   (hooks/CLI wrappers)  │  WSS   │   "pairing" (device pair) │
│  Policy engine (YAML)   │◀───────┤  Relays JSON messages only│
│  Local queue + TTL      │        │  No payload storage       │
│  Device keypair (Ed25519)│       │  (D1 only for audit meta) │
└────────────────────────┘        └─────────────┬─────────────┘
                                                  │ WSS
                                                  ▼
                                   ┌───────────────────────────┐
                                   │   ANDROID APP (Compose)    │
                                   │                            │
                                   │  Firebase Cloud Messaging  │
                                   │   (wake-up only, free,     │
                                   │    unlimited)               │
                                   │  Approval UI               │
                                   │  Local history (Room)      │
                                   │  Device keypair (Ed25519)  │
                                   └────────────────────────────┘
```

**Why a Durable Object per pairing instead of one big server:** each paired desktop+phone gets its own tiny stateful WebSocket "room." This is exactly the coordination pattern Durable Objects are built for, it scales horizontally for free, and it means one user's traffic never touches another's object — good for both cost isolation and privacy.

**Why FCM stays even though you're avoiding cost elsewhere:** FCM is free and unlimited for any volume, and it's the only reliable way to wake a backgrounded Android app when a request arrives while the app's WebSocket isn't connected (Doze mode, app killed, etc.). The push payload itself should be a bare `{"requestId": "..."}` — the app then pulls the actual request over the authenticated WebSocket/HTTPS channel, so FCM (and Google) never sees command content.

---

## 3. Why not P2P/WebRTC for the MVP

This is a deliberate, correctable-later decision, so it's worth stating plainly:

- A permission request/response pair is under 1KB. At even 100,000 daily active users firing 20 requests/day, that's ~2GB/month of relay traffic — comfortably inside Cloudflare's free bandwidth-uncapped Workers tier. WebRTC's main value (avoiding server bandwidth cost) doesn't apply at this payload size.
- WebRTC + STUN/TURN + signaling is the single biggest source of complexity, flaky-connection bugs, and platform-specific pain (especially on Android background/Doze restrictions) in the entire system. For a solo/small open-source team, that complexity tax is better spent on adapter quality and UX polish.
- The protocol you specify in Section 5 doesn't care about transport. If you outgrow the relay (e.g., you add live terminal streaming as a v2 feature, where bandwidth actually matters), you can add a WebRTC data-channel transport later as an alternative path *without breaking the protocol or the clients that don't need it*. Keep the door open; don't build the room now.

---

## 4. Component breakdown

### 4.1 Desktop Bridge — Kotlin/JVM
Given your skillset, Kotlin/JVM (packaged with `jpackage` or GraalVM native-image for a smaller footprint) is a much shorter path than learning Rust for the MVP, while still being a single reused language across your whole stack (shared `PermissionRequest`/`PermissionDecision` data classes as a Kotlin Multiplatform module, shared literally between desktop and Android).

Responsibilities:
- Runs as a background process (system tray icon optional, via a lightweight lib rather than a full Compose Desktop UI initially).
- Hosts adapter processes that watch for agent permission events (see 4.2).
- Evaluates local YAML policy before ever contacting the network (proposal's precedence: deny → allow → ask → default — keep this).
- Holds the device's Ed25519 keypair in an OS keystore (Windows DPAPI / macOS Keychain / Linux Secret Service via a JNA binding, or fall back to an encrypted local file with a warning).
- Opens one persistent WebSocket to its Cloudflare Durable Object per active pairing.
- Applies a TTL to every request (default 5 min) and fails closed on expiry — never resolves silently to "approved."

### 4.2 Agent Adapter Layer
Keep this exactly as your proposal specifies — it's the right abstraction regardless of transport:
```kotlin
interface AgentAdapter {
    val capabilities: Capabilities
    suspend fun connect()
    suspend fun nextPermission(): PermissionRequest
    suspend fun submitDecision(requestId: String, decision: PermissionDecision)
}
```
Start with **one** adapter for the MVP. Claude Code's documented `PreToolUse` hook (a shell command Claude Code invokes and waits on) is arguably an *easier* first integration than Cursor's ACP, because it's just "your process reads JSON on stdin, writes a decision on stdout" — no long-lived protocol client needed. Recommend flipping the proposal's build order: **Claude Code adapter first, Cursor ACP second** — it de-risks the whole pipeline fastest, and several competing projects have already proven the hook approach works reliably.

### 4.3 Android App — Jetpack Compose
This is your strongest area, so lean into it:
- **Pairing:** CameraX + ML Kit barcode scanning for the QR flow; QR payload is only a short-lived pairing-session ID + Cloudflare Worker URL + one-time challenge, per your original security design.
- **Approval UI:** a single `PermissionRequestScreen` composable driven by a sealed-class UI state, plus a compact notification with `RemoteInput`-free action buttons (Approve/Deny directly on the notification, matching your mockup).
- **Background reliability:** request FCM high-priority messages for permission requests specifically (Google allows this for latency-sensitive, user-visible notifications); guide users through the manufacturer-specific battery-optimization exemption flow (Xiaomi/Huawei/Samsung all throttle background sockets aggressively — this is the #1 reliability complaint you'll get, worth a dedicated onboarding screen).
- **Local history:** Room database, encrypted at rest via SQLCipher or Jetpack Security's `EncryptedFile`, storing decision metadata only (never full command text by default, matching your privacy model).
- **Signing:** Android Keystore-backed Ed25519 (or ECDSA P-256, since Keystore's Ed25519 support varies by API level — verify on your minimum SDK target) for signing decisions.

### 4.4 Cloudflare relay (control plane)
- **Worker**: stateless HTTP endpoints for device registration, pairing initiation, and health checks.
- **Durable Object**: one instance per pairing, holding the WebSocket connections for that desktop+phone pair, forwarding messages, enforcing TTL-based cleanup of stale pending requests. This is pure message-passing — it should never need to parse or store command content.
- **D1** (SQLite, free tier): device public keys, pairing metadata, and audit-log rows (timestamps, decision type, risk level) — never raw commands.
- **No TURN, no WebRTC signaling** in the MVP, per Section 3.

### 4.5 Policy & Risk Engines
Keep exactly as specified in your proposal — deterministic YAML rules, local-only evaluation, no LLM in the trust path. This part of your original design is already correctly scoped and doesn't need simplification.

---

## 5. Protocol (unchanged from your proposal, transport-agnostic)

Your `PermissionRequest`/`PermissionDecision` schema and the CREATED → PENDING → APPROVED/DENIED/EXPIRED → DELIVERED → EXECUTED lifecycle are well-designed and transport-independent — keep them verbatim. Model them as a shared Kotlin Multiplatform module (`:protocol`) so the desktop bridge (JVM) and Android app (Android target) use the *same* generated serialization code (kotlinx.serialization) instead of hand-syncing a JSON schema across two codebases. This also gives you a natural place to put protocol version negotiation (`handoff/1.0`) as a sealed class hierarchy that fails to compile if a client and server drift.

---

## 6. Security model — what to keep, what to tighten

Keep from your proposal: device keypairs never leaving the device, short-lived QR pairing tokens, signed+nonce'd decisions, replay protection, fail-closed on any ambiguity, least-privilege (approve a specific action, never grant shell access).

Two additions worth making explicit for the simplified relay:
- **The Durable Object is an untrusted relay, not a trust anchor.** Every decision must be verified against the phone's public key *by the desktop bridge*, not trusted because "it came through our server." Treat a compromised or malicious relay as part of your threat model from day one — self-hosters will run their own relay, and the protocol must be safe even if the relay is fully adversarial (it can drop or delay messages, but must not be able to forge an approval).
- **Rate-limit pairing attempts and request creation per device** at the Worker level (Cloudflare's free tier includes basic abuse protections, but add your own device-level quotas in D1) — this is your main defense against the "public service abuse" risk your own risk table already flags.

---

## 7. What this costs, concretely

| Component | Free tier used | Realistic ceiling before you'd pay anything |
|---|---|---|
| Cloudflare Workers | 100k req/day | Tens of thousands of daily active pairs |
| Cloudflare Durable Objects | ~1M req/mo, 400K GB-s/mo, 5GB storage | Thousands of concurrent paired connections |
| Cloudflare D1 | 5GB, 5M reads/mo | Hundreds of thousands of devices' metadata |
| Firebase Cloud Messaging | Unlimited, free | No ceiling |
| GitHub Actions (public repo) | Unlimited minutes | No ceiling |
| GitHub Releases | Unlimited | No ceiling |
| Google Play one-time registration | N/A | **This is the one unavoidable real cost: a $25 one-time Play Console fee.** Everything else above is genuinely $0 recurring. |

If/when you outgrow Cloudflare's free tier, the protocol's relay boundary means you can move only the relay to a paid tier (or ask users to self-host it) without touching the Android app or desktop bridge at all.

---

## 8. Revised build order (solo-dev friendly)

```
1. Define protocol as a Kotlin Multiplatform module (shared by desktop + Android)
2. Build Claude Code adapter (stdin/stdout hook — fastest path to a working demo)
3. Build desktop bridge core: policy engine, risk engine, request queue, TTL
4. Stand up the Cloudflare Worker + Durable Object relay (message-passing only)
5. Build Android approval UI (Compose) against a mocked relay first
6. Wire FCM wake-up notifications
7. Implement QR pairing + Ed25519 device identity end-to-end
8. Connect Android app to the real relay; end-to-end test with Claude Code
9. Add policy config UI / YAML editor
10. Add Cursor ACP adapter
11. Add audit history screen + D1-backed history sync
12. Harden: rate limiting, replay tests, clock-skew handling, reconnection backoff
13. Self-hosting docs (docker-compose for the Worker equivalent, e.g. via `workerd` or a small Node/Kotlin relay reimplementation for non-Cloudflare self-hosters)
14. iOS, Codex adapter, WebRTC data-channel transport (v2+)
```

Steps 1–8 get you a genuinely usable, demoable, open-sourceable product using only languages and platforms you already know.

---

## 9. What to keep from the original proposal unchanged

To be clear about scope: the product vision, non-goals, functional requirements (FR-01–FR-14), data model, policy engine design, risk classification tiers, privacy model, and monetization/sustainability plan in your original document are all sound and don't need revision — they're transport- and language-agnostic. What this document changes is specifically: **the mobile framework (Flutter → Kotlin/Compose, matching your actual skillset), the desktop language (Rust → Kotlin/JVM, for solo-build speed), and the transport (WebRTC P2P-first → Cloudflare relay-only for MVP, since payload size makes P2P's cost benefit moot at this stage).**
