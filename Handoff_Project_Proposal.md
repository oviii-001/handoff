# HandOff

## Production-Grade Project Proposal

**Project Type:** Open-source developer infrastructure / AI-agent tooling  
**Primary Goal:** Allow developers to approve or deny AI coding-agent permission requests from their phone, without returning to their computer.  
**License Recommendation:** Apache-2.0  
**Initial Target:** Windows + Android + Cursor CLI  
**Long-Term Target:** Windows/macOS/Linux + Android/iOS + Cursor/Codex/Claude Code/Antigravity and other compatible agents

---

## 1. Executive Summary

AI coding agents are increasingly capable of running multi-step development workflows. The limiting factor is often not generation speed but human approval: an agent reaches a tool-permission boundary, pauses, and waits for the developer to approve or deny the action.

HandOff is an open-source, agent-agnostic permission bridge that moves those approval decisions from the developer's computer to their phone.

The developer installs HandOff on the computer, installs the mobile app, scans a QR code, and starts using their coding agent normally. When an agent requests permission, the desktop bridge receives the request, evaluates local policies, and—when human approval is required—delivers an encrypted approval request to the paired phone. The user can approve or deny the request from a notification or the mobile app. The decision returns to the desktop bridge and is forwarded to the waiting agent.

The system is designed around four principles:

1. **Zero-friction onboarding** — install, pair, use.
2. **Agent-agnostic core** — adapters translate different agent permission formats into one normalized protocol.
3. **Privacy and security by default** — end-to-end encryption, device keys, signed decisions, least-privilege server access, and fail-closed behavior.
4. **Free for end users** — the official service is intended to operate as a lightweight shared control plane, while actual device traffic uses peer-to-peer transport where possible and self-hosting remains available.

---

## 2. Problem Statement

Modern coding agents frequently require permission to perform actions such as:

- running shell commands;
- installing packages;
- modifying files;
- accessing the network;
- using external tools or MCP servers;
- executing potentially destructive commands.

Developers commonly keep agents in approval-required mode because unrestricted agent access can be unsafe. This creates a frustrating workflow:

```text
Developer starts agent
        ↓
Developer walks away
        ↓
Agent reaches permission boundary
        ↓
Agent waits
        ↓
Developer must return to computer
        ↓
Approve / deny
        ↓
Agent continues
```

This becomes especially painful during long-running development sessions, overnight tasks, builds, tests, migrations, or autonomous debugging.

AgentApprove removes the physical-location dependency:

```text
Agent requests permission
        ↓
Desktop bridge captures request
        ↓
Phone notification
        ↓
Approve / deny from phone
        ↓
Agent continues or stops
```

---

## 3. Product Vision

### Vision

> **Make AI coding agents safely operable from anywhere, without requiring developers to give them unrestricted access.**

### Product Positioning

AgentApprove is not another AI coding agent. It is a **remote permission and control layer for AI coding agents**.

The long-term product is composed of:

```text
AI coding agents
      ↓
Agent adapter layer
      ↓
AgentApprove protocol
      ↓
Desktop bridge + policy engine
      ↓
Secure transport
      ↓
Mobile approval/control surface
```

---

## 4. Competitive Context

The core problem has already appeared in small open-source projects and commercial tools, which validates the use case but leaves room for a more polished, cross-agent, remotely accessible, open protocol.

One relevant open-source project is `jzethar/ai-approve`, described as a phone approval hook for Claude Code and Codex CLI. It currently emphasizes local-network operation and a shared daemon/protocol with thin agent adapters. [GitHub](https://github.com/jzethar/ai-approve)

Cursor's current ACP documentation is particularly relevant to AgentApprove because ACP clients can receive `session/request_permission` events and return `allow-once`, `allow-always`, or `reject-once`; an unanswered permission request can block tool execution. [Cursor ACP documentation](https://prod.cursor.com/docs/cli/acp)

Claude Code also exposes a structured permission model built around allow/ask/deny behavior. [Claude Code permissions](https://code.claude.com/docs/en/permissions)

These capabilities make a standardized remote approval layer technically realistic. AgentApprove's differentiation should therefore focus on the complete product experience: cross-agent support, reliable remote connectivity, polished notifications, security, policy automation, simple installation, public free infrastructure, and an open protocol.

---

## 5. Goals

### Primary Goals

- Approve or deny AI-agent permission requests from Android and later iOS.
- Support remote use outside the local network.
- Require no technical server setup for normal users.
- Remain free to use for end users under the project's public service model.
- Make the desktop bridge lightweight and cross-platform.
- Support multiple agents through adapters.
- Provide local automatic approval/denial policies.
- Use end-to-end encryption for sensitive permission data.
- Fail closed when approval cannot be obtained.
- Keep the central infrastructure lightweight and scalable.
- Provide a self-hostable deployment for advanced users and organizations.

### Secondary Goals

- Approval history and audit logs.
- Multi-computer support.
- Multiple phone/device support.
- Risk classification.
- Emergency stop.
- Session monitoring.
- An open integration protocol for third-party agents.

---

## 6. Non-Goals for the MVP

The first production milestone should **not** attempt to:

- remotely edit source code;
- stream entire IDE sessions;
- replace remote desktop software;
- proxy all terminal traffic through the cloud;
- permanently store complete agent conversations;
- support every coding agent immediately;
- build a large enterprise control plane before product-market validation.

The MVP should focus on one job:

> **A permission prompt appears on the developer's computer, and the developer can resolve it securely from their phone.**

---

## 7. Target Users

### Primary Persona

Software developers who run coding agents with approval required and regularly leave their machines while agents work.

### Secondary Personas

- AI-assisted development teams.
- Developers running long autonomous coding tasks.
- Developers using multiple machines.
- Security-conscious developers who do not want unrestricted agent privileges.
- Organizations that want to self-host the permission infrastructure.

---

## 8. User Experience

### Initial Setup

The intended normal user flow is:

```text
1. Install AgentApprove Desktop
2. Install AgentApprove Mobile
3. Open Desktop app
4. Show QR pairing code
5. Scan QR from phone
6. Confirm pairing
7. AgentApprove detects supported agents
8. Done
```

No Docker. No database setup. No API keys. No manually configured WebRTC servers.

### Approval Experience

Example notification:

```text
🤖 AgentApprove
Cursor needs permission

Project: Lumina
Action: npm install sharp
Risk: Medium

[ DENY ]       [ APPROVE ]
```

Tapping the notification opens a richer detail view when necessary:

```text
Cursor

Permission requested

Type: Shell command
Project: Lumina
Workspace: ~/projects/lumina

npm install sharp

Why:
Install the image-processing dependency requested by the agent.

Risk: MEDIUM

[ Deny ] [ Approve Once ] [ Approve Always ]
```

---

## 9. Functional Requirements

### FR-01 — Device Pairing

The system shall allow a user to pair a computer and phone using a short-lived QR/code-based pairing flow.

### FR-02 — Agent Detection

The desktop bridge shall discover supported local agents and expose their connection status.

### FR-03 — Permission Capture

The desktop bridge shall receive permission events from supported agent adapters.

### FR-04 — Permission Normalization

All adapter-specific permission events shall be converted into a common `PermissionRequest` model.

### FR-05 — Policy Evaluation

The desktop bridge shall evaluate configured policies before sending a request to the phone.

### FR-06 — Remote Approval

A pending permission request shall be visible on the paired mobile device.

### FR-07 — Notification Approval

The mobile client shall support fast approve/deny actions from supported notification surfaces.

### FR-08 — Decision Delivery

The decision shall be returned to the desktop bridge and mapped into the originating agent's native decision format.

### FR-09 — Expiration

Permission requests shall have an expiration time.

### FR-10 — Fail Closed

If communication fails or a request expires, the agent shall remain unapproved rather than being implicitly authorized.

### FR-11 — Audit Trail

The system shall record enough metadata to reconstruct the request/decision lifecycle without requiring persistent storage of sensitive command contents.

### FR-12 — Multi-Device Support

A user shall eventually be able to pair multiple computers and mobile devices.

### FR-13 — Emergency Stop

The system shall provide an optional mechanism to stop/pause managed agent sessions on a paired computer.

### FR-14 — Self-Hosting

The cloud/control-plane layer shall be replaceable by a user-operated deployment.

---

## 10. High-Level Architecture

```text
                                      ┌───────────────────────┐
                                      │ AgentApprove Cloud    │
                                      │                       │
                                      │ Auth / device registry│
                                      │ Signaling             │
                                      │ Realtime coordination │
                                      │ Push coordination     │
                                      │ Minimal metadata      │
                                      └───────────┬───────────┘
                                                  │
                                       WebRTC / signaling
                                                  │
                           ┌──────────────────────┴──────────────────────┐
                           │                                             │
                           ▼                                             ▼
                  ┌─────────────────┐                           ┌──────────────────┐
                  │ Desktop Bridge  │◀════ encrypted P2P ═════▶│ Mobile App       │
                  │                 │                           │                  │
                  │ Agent adapters  │                           │ Notifications    │
                  │ Policy engine   │                           │ Approval UI      │
                  │ Crypto          │                           │ History          │
                  │ Request queue   │                           │ Device control   │
                  └────────┬────────┘                           └──────────────────┘
                           │
                           ▼
                 ┌────────────────────┐
                 │ AI Coding Agent(s) │
                 │                    │
                 │ Cursor             │
                 │ Codex              │
                 │ Claude Code        │
                 │ Antigravity        │
                 │ Other adapters     │
                 └────────────────────┘
```

The architectural principle is **server-assisted, P2P-first**. The shared cloud provides discovery, signaling, authentication and push coordination; the actual permission payload should use a secure device-to-device path whenever feasible.

---

## 11. System Components

### 11.1 Desktop Bridge

A persistent background service installed on the developer's computer.

Responsibilities:

- maintain authenticated connection to AgentApprove infrastructure;
- connect to supported agents through adapters;
- normalize permission requests;
- evaluate local policies;
- maintain pending request state;
- establish secure peer connections;
- verify mobile decisions;
- return decisions to the originating agent;
- provide a local CLI and diagnostics.

Recommended implementation: **Rust**.

Rationale:

- low memory footprint;
- strong concurrency model;
- excellent cross-platform support;
- strong cryptographic ecosystem;
- suitable for a long-running system daemon.

---

### 11.2 Agent Adapter Layer

The adapter layer prevents vendor-specific agent protocols from leaking into the rest of the system.

Interface concept:

```rust
trait AgentAdapter {
    fn capabilities(&self) -> Capabilities;
    async fn connect(&mut self) -> Result<()>;
    async fn next_permission(&mut self) -> Result<PermissionRequest>;
    async fn submit_decision(
        &mut self,
        request_id: RequestId,
        decision: PermissionDecision,
    ) -> Result<()>;
}
```

Initial adapters:

```text
adapters/
├── cursor/
├── codex/
├── claude-code/
├── antigravity/
└── generic/
```

Cursor is the preferred first adapter because its current ACP interface explicitly exposes permission requests and decisions. [Cursor ACP documentation](https://prod.cursor.com/docs/cli/acp)

---

## 12. Normalized Permission Protocol

The central protocol should be independent of any vendor.

### PermissionRequest

```typescript
interface PermissionRequest {
  id: string;
  protocolVersion: string;

  agent: {
    id: string;
    name: string;
    version?: string;
  };

  session: {
    id: string;
    project?: string;
    workspace?: string;
  };

  permission: {
    type:
      | 'shell'
      | 'file_read'
      | 'file_write'
      | 'network'
      | 'mcp'
      | 'other';

    command?: string;
    target?: string;
    description?: string;
  };

  risk: {
    level: 'low' | 'medium' | 'high' | 'critical';
    reasons: string[];
  };

  options: PermissionOption[];

  createdAt: string;
  expiresAt: string;
}
```

### PermissionDecision

```typescript
interface PermissionDecision {
  requestId: string;
  decision:
    | 'approve_once'
    | 'approve_always'
    | 'deny'
    | 'cancel';

  issuedAt: string;
  nonce: string;
  deviceId: string;
  signature: string;
}
```

The protocol should be versioned from the start:

```text
agentapprove/1.0
agentapprove/1.1
...
```

---

## 13. Permission Lifecycle

```text
                 ┌─────────────┐
                 │   CREATED   │
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │   PENDING   │
                 └──────┬──────┘
                        │
              ┌─────────┼─────────┐
              │         │         │
              ▼         ▼         ▼
          APPROVED    DENIED    EXPIRED
              │
              ▼
         DELIVERED
              │
              ▼
          EXECUTED
              │
           ┌──┴──┐
           ▼     ▼
       SUCCESS  FAILED
```

Every state transition should be idempotent.

For example, receiving the same `approve_once` message twice must not cause two approvals.

---

## 14. Policy Engine

Policies should be evaluated **locally on the desktop** before a request reaches the cloud.

Example:

```yaml
rules:
  - name: allow-tests
    match:
      command: "npm test"
    action: allow

  - name: allow-read-only-git
    match:
      command: "git status"
    action: allow

  - name: package-installs
    match:
      command_prefix: "npm install"
    action: ask

  - name: git-push
    match:
      command_prefix: "git push"
    action: ask

  - name: force-push
    match:
      command_contains: "--force"
    action: deny

  - name: privileged-commands
    match:
      command_prefix: "sudo"
    action: ask
```

Policy precedence should be deterministic:

```text
explicit deny
    ↓
explicit allow
    ↓
ask
    ↓
default policy
```

The exact precedence may be adjusted in the protocol specification, but must never depend on network latency or cloud availability.

---

## 15. Risk Engine

The first release should use deterministic local heuristics rather than an LLM.

Example baseline:

```text
LOW
- git status
- git diff
- npm test
- cargo test

MEDIUM
- npm install
- pip install
- docker build
- package downloads

HIGH
- git push
- docker run with mounts
- network access
- modifying deployment configuration

CRITICAL
- sudo
- destructive recursive deletion
- force-push
- raw disk operations
- secret/keychain access
```

The engine should expose reasons:

```json
{
  "level": "high",
  "reasons": [
    "command writes to remote repository",
    "operation can affect shared branch state"
  ]
}
```

LLM-based explanation can be introduced later as an optional feature. It should never be the only security decision mechanism.

---

## 16. Mobile Application

Recommended first implementation: **Flutter**.

Target:

```text
Android  → MVP
 iOS     → subsequent release
```

Core screens:

```text
Dashboard
├── Active computers
├── Active agents
├── Pending requests
└── Connection status

Permission Request
├── Agent
├── Project
├── Action
├── Risk
├── Details
└── Approve / Deny

History
├── Approved
├── Denied
├── Expired
└── Failed

Policies
└── Rules

Settings
├── Devices
├── Security
├── Notifications
└── Self-hosted server
```

---

## 17. Notification Architecture

Notifications are a wake-up and interaction surface, not the authoritative source of permission state.

The authoritative state remains on the desktop bridge and synchronized control plane.

### Android

Use Firebase Cloud Messaging (FCM) for remote notifications. FCM is designed to deliver messages to Android client applications. [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)

### iOS

Use Apple Push Notification service (APNs). Apple's remote notification architecture uses a provider server to submit notifications to APNs, which delivers them to the device. [Apple remote notifications](https://developer.apple.com/documentation/usernotifications/setting-up-a-remote-notification-server)

Push payload should contain minimal information, for example:

```json
{
  "type": "permission_request",
  "requestId": "req_01J..."
}
```

Sensitive command details should not be trusted from the notification payload alone.

---

## 18. Connectivity Architecture

The system should use a tiered transport strategy.

### Tier 1 — Direct LAN

When phone and computer are on the same local network:

```text
Phone ───────── Desktop
       local
```

### Tier 2 — Direct P2P

When remote but direct traversal is possible:

```text
Phone ═════ WebRTC ═════ Desktop
```

### Tier 3 — Relay

If direct connection cannot be established:

```text
Phone ───── TURN relay ───── Desktop
```

The relay is a fallback because it carries user traffic and therefore has the greatest infrastructure cost.

The architecture must never make TURN mandatory for ordinary communication if a direct connection is possible.

---

## 19. Shared Cloud Control Plane

The official service exists to make the software genuinely "install and use" for normal users.

The cloud should be responsible for:

- account/device identity;
- pairing coordination;
- WebRTC signaling;
- online presence;
- push notification coordination;
- short-lived request metadata;
- abuse/rate limiting;
- optional relay discovery.

The cloud should **not** become the permanent storage system for terminal output, source code, or complete agent conversations.

### Recommended architecture

```text
Internet
   │
   ▼
Cloudflare Edge
   │
   ├── Workers API
   ├── Durable Objects / WebSocket coordination
   ├── D1 or equivalent metadata storage
   └── TURN infrastructure as a separate fallback layer
```

Cloudflare Durable Objects currently support WebSockets and are specifically intended for coordination among multiple clients; the hibernation WebSocket API is designed to reduce costs during idle periods. [Cloudflare Durable Objects WebSockets](https://developers.cloudflare.com/durable-objects/best-practices/websockets/)

Cloudflare documents Durable Objects as available on Free and Paid plans, with SQLite-backed Durable Objects available on the Free plan subject to platform limits. [Cloudflare Durable Objects overview](https://developers.cloudflare.com/durable-objects/)

Important: free-tier limits and pricing can change, so the deployment must be designed to migrate between providers without changing the client protocol.

---

## 20. Cloud Scaling Strategy

The system is designed for large user counts by making the control plane lightweight and the data plane peer-to-peer.

```text
Registered users
      ↓
Mostly idle identities
      ↓
Only active devices create realtime sessions
      ↓
Only pending approvals generate meaningful traffic
      ↓
Actual sensitive payload prefers direct P2P
```

### Example at 1M registered users

```text
1,000,000 registered users
        ≠
1,000,000 simultaneous users
        ≠
1,000,000 TURN connections
```

Scaling targets should therefore be expressed using:

- registered devices;
- concurrent desktop connections;
- concurrent phone connections;
- requests per second;
- permission requests per minute;
- percentage of traffic using direct P2P;
- TURN relay bandwidth;
- push notifications per minute.

The architecture should support horizontal scale without requiring application redesign.

---

## 21. Data Model

### User

```text
id
created_at
status
```

### Device

```text
id
user_id
platform
public_key
push_token
created_at
last_seen
```

### Computer

```text
id
user_id
name
public_key
status
last_seen
```

### Agent

```text
id
computer_id
adapter_type
agent_name
agent_version
status
```

### Session

```text
id
agent_id
project_name
workspace
status
started_at
ended_at
```

### Permission Request

```text
id
session_id
agent_id
type
risk_level
status
created_at
expires_at
resolved_at
```

Command content may be stored transiently or encrypted, depending on privacy settings. The default should minimize persistence.

### Decision

```text
id
request_id
device_id
decision
issued_at
nonce
signature
```

### Audit Event

```text
id
request_id
device_id
event_type
created_at
metadata
```

---

## 22. Security Architecture

Security is a core product requirement because the system controls potentially dangerous AI-agent actions.

### Device Identity

Each device should generate a public/private key pair during first initialization.

```text
Computer
  private key → never leaves device
  public key  → registered with service

Phone
  private key → never leaves device
  public key  → registered with service
```

### Pairing

The QR code should carry only short-lived pairing information, for example:

```text
pairing session ID
server endpoint
temporary challenge
```

Long-term device credentials must be established through cryptographic key exchange.

### Signed Decisions

Every approval should contain:

```text
requestId
nonce
decision
timestamp
deviceId
signature
```

The desktop bridge validates:

1. request ID matches a pending request;
2. decision has not already been consumed;
3. timestamp is acceptable;
4. nonce is valid;
5. signature matches the paired phone public key.

### Replay Protection

A consumed request must never be accepted again.

### Fail Closed

No network response must ever be interpreted as approval.

```text
No decision
   ↓
Agent remains blocked
```

### Least Privilege

The mobile app should never receive arbitrary shell access to the computer. It should issue narrowly scoped permission decisions.

### Secret Handling

Source code, tokens, API keys, terminal output, and agent context should not be uploaded to the cloud by default.

---

## 23. Privacy Model

Default data principles:

- Store the minimum metadata necessary.
- Prefer P2P over cloud relay.
- Encrypt sensitive payloads end-to-end.
- Do not retain terminal output unless explicitly enabled.
- Do not sell user data.
- Make self-hosting available.
- Clearly document what the official infrastructure can observe.

The open-source repository should include a dedicated `SECURITY.md` and a plain-language privacy document.

---

## 24. Reliability Requirements

### Offline Phone

If the phone is offline:

```text
permission → remains PENDING
```

The user should see it when the phone reconnects if the request is still valid.

### Offline Computer

The mobile app should show the computer as offline and should not fabricate approval state.

### Duplicate Decisions

The system should be idempotent.

### Reconnection

Desktop and phone clients should automatically reconnect with exponential backoff and jitter.

### Expiration

Every pending request must have a TTL.

### Clock Skew

Security-sensitive timestamps should not rely exclusively on local client clocks.

---

## 25. Emergency Controls

Future release:

```text
STOP ALL AGENTS
```

or per-machine:

```text
Pause Agent
Resume Agent
Terminate Agent
```

The desktop bridge should expose a controlled local process-management interface so emergency actions remain possible even when cloud communication fails.

---

## 26. Self-Hosting

Self-hosting is important for users who want maximum privacy or organizations that cannot depend on the public service.

Target deployment:

```bash
git clone https://github.com/<org>/agentapprove
cd agentapprove
cp .env.example .env
docker compose up -d
```

Self-hosted components should include:

- API/control plane;
- signaling;
- database;
- optional TURN relay.

The public mobile and desktop applications should allow the server endpoint to be changed in advanced settings.

---

## 27. Recommended Technology Stack

| Layer | Recommendation | Reason |
|---|---|---|
| Desktop bridge | Rust | Reliability, security, low footprint, cross-platform |
| Desktop UI | Tauri or native/minimal UI | Small install footprint; optional GUI |
| Mobile | Flutter | Android-first with iOS path |
| Protocol | Protobuf or JSON + versioning | Explicit contract and backward compatibility |
| Transport | WebRTC + secure WebSocket signaling | P2P-first remote transport |
| Local transport | WebSocket/HTTP on LAN | Fast and simple local fallback |
| Cloud API | TypeScript + Workers | Low operational overhead |
| Realtime | Durable Objects/WebSockets | Stateful connection coordination |
| Metadata DB | D1 / SQLite-backed service or portable relational DB | Low operational complexity |
| Push | FCM + APNs | Native mobile push infrastructure |
| Relay | coturn or managed TURN | P2P fallback |
| CI | GitHub Actions | Native open-source workflow |
| Releases | GitHub Releases | Simple binary distribution |
| Containers | Docker Compose | Self-hosting |

---

## 28. Repository Structure

```text
agentapprove/
│
├── apps/
│   ├── desktop/
│   └── mobile/
│
├── crates/
│   ├── core/
│   ├── crypto/
│   ├── protocol/
│   ├── transport/
│   ├── policy/
│   └── adapters/
│       ├── cursor/
│       ├── codex/
│       ├── claude-code/
│       └── antigravity/
│
├── cloud/
│   ├── api/
│   ├── signaling/
│   ├── push/
│   └── migrations/
│
├── infrastructure/
│   ├── docker/
│   ├── turn/
│   └── deployment/
│
├── docs/
│   ├── architecture.md
│   ├── protocol.md
│   ├── security.md
│   ├── adapters.md
│   ├── self-hosting.md
│   └── contributing.md
│
├── tests/
│   ├── protocol/
│   ├── integration/
│   ├── security/
│   └── e2e/
│
├── .github/
│   └── workflows/
│
├── LICENSE
├── README.md
├── SECURITY.md
└── CONTRIBUTING.md
```

---

## 29. CLI UX

The normal command-line experience should be simple.

```bash
agentapprove status
agentapprove pair
agentapprove agents
agentapprove logs
agentapprove policy list
agentapprove policy test "npm test"
```

The first-run command can be:

```bash
agentapprove
```

with an interactive setup flow.

---

## 30. API Design

Illustrative control-plane endpoints:

```http
POST   /v1/auth/device
POST   /v1/pairing/sessions
POST   /v1/pairing/complete
GET    /v1/devices
POST   /v1/devices/revoke
GET    /v1/sessions
GET    /v1/requests
GET    /v1/requests/:id
POST   /v1/requests/:id/ack
POST   /v1/push/register
GET    /v1/health
```

The actual permission decision can travel over an authenticated realtime/P2P channel rather than an ordinary REST request.

---

## 31. Example End-to-End Flow

### Scenario

The developer starts Cursor on their computer and goes to bed.

### Agent request

```text
Cursor
  ↓
Request: git push origin feature/lumina
```

### Desktop bridge

```text
CursorAdapter
  ↓
PermissionRequest
  ↓
Policy Engine
  ↓
Rule says: ASK
```

### Cloud coordination

```text
Bridge
  ↓
control plane
  ↓
phone notification trigger
```

### Phone

```text
🔔 Cursor needs approval

git push origin feature/lumina
Risk: HIGH

[ DENY ] [ APPROVE ]
```

### User response

```text
Phone
  ↓
signed decision
  ↓
secure device channel
  ↓
Desktop bridge
```

### Agent response

```text
Desktop bridge
  ↓
CursorAdapter
  ↓
allow-once
  ↓
Cursor continues
```

---

## 32. Error Scenarios

### Network failure before approval

```text
Request → PENDING
```

### Phone offline

```text
Request → PENDING until TTL
```

### Request already resolved

A later duplicate decision is rejected as stale.

### Desktop restarts

Pending requests are recovered from encrypted local state if still valid.

### Cloud unavailable but LAN available

The system should attempt direct local connectivity.

### P2P unavailable

Use TURN if configured/available.

### TURN unavailable

Agent remains blocked until the request expires or the user resolves it through an available local path.

---

## 33. Scaling to 1 Million Users

The architecture should treat one million users as a capacity-planning target rather than an assumption that every user is simultaneously active.

### Scaling characteristics

```text
                     LOW COST
                        │
                        ▼
             Metadata / authentication
                        │
                        ▼
                  Signaling
                        │
                        ▼
              Realtime coordination
                        │
                        ▼
              Push notifications
                        │
                        ▼
                  TURN relay
                        │
                        ▼
                    HIGH COST
```

The final category—relay bandwidth—is the main reason P2P should be preferred.

### Capacity metrics

The production service should continuously measure:

- active devices;
- WebSocket connections;
- signaling messages/sec;
- permission events/sec;
- notification sends/minute;
- TURN sessions;
- TURN bytes transferred;
- median and p99 decision latency;
- request delivery success rate;
- reconnect frequency.

### Cost-control mechanisms

- P2P-first transport.
- Short-lived server state.
- Idle WebSocket hibernation where supported.
- No storage of large payloads.
- Rate limiting.
- Device-level quotas for abuse prevention.
- Push payload minimization.
- Optional self-hosted relay.
- Runtime-configurable infrastructure providers.

Cloudflare's Durable Objects documentation notes that hibernation can reduce duration charges during idle WebSocket periods, making this architecture a reasonable candidate for low-overhead coordination. [Cloudflare WebSockets](https://developers.cloudflare.com/durable-objects/best-practices/websockets/)

The project's documentation must avoid promising permanently zero operational cost for the official infrastructure. The software should remain free to users, while infrastructure may need sponsorship, donations, grants, community hosting, or a sustainable funding model if adoption becomes very large.

---

## 34. Open-Source Strategy

### License

**Apache-2.0** is recommended because it is permissive, business-friendly, and includes an explicit patent grant.

### Governance

Initially:

```text
Maintainer(s)
    ↓
Pull requests
    ↓
Security review
    ↓
Release
```

As adoption grows:

```text
Core maintainers
├── protocol
├── security
├── desktop
├── mobile
├── adapters
└── infrastructure
```

### Contribution model

Third-party contributions should be especially encouraged around:

- new agent adapters;
- new mobile platforms;
- transport implementations;
- policy rules;
- documentation;
- localization;
- security hardening.

---

## 35. Testing Strategy

### Unit Tests

- protocol serialization;
- policy matching;
- risk classification;
- crypto verification;
- state-machine transitions.

### Integration Tests

- bridge ↔ cloud;
- bridge ↔ mobile;
- adapter ↔ agent;
- push registration;
- reconnect logic.

### End-to-End Tests

```text
Fake agent
   ↓
permission request
   ↓
desktop bridge
   ↓
mobile simulator/device
   ↓
approve
   ↓
agent receives decision
```

### Security Tests

- replay attack;
- forged approval;
- stale request;
- revoked device;
- invalid signature;
- expired pairing token;
- unauthorized device enumeration;
- malformed protocol messages.

---

## 36. Observability

The official service should expose metrics and logs without collecting user command content by default.

### Metrics

```text
permission_request_rate
permission_approval_rate
permission_denial_rate
permission_expiration_rate
median_decision_latency
p95_decision_latency
p99_decision_latency
active_devices
active_connections
p2p_success_rate
turn_fallback_rate
push_delivery_rate
```

### Logging

Use structured logs with privacy-safe identifiers:

```json
{
  "event": "permission_resolved",
  "requestIdHash": "...",
  "deviceIdHash": "...",
  "decision": "approve_once",
  "latencyMs": 812
}
```

Avoid logging raw commands by default.

---

## 37. Threat Model

Primary threats:

1. Attacker impersonates a paired phone.
2. Attacker replays a previous approval.
3. Attacker steals pairing credentials.
4. Attacker compromises the central signaling service.
5. Attacker attempts to inject a fake permission request.
6. Malicious or compromised desktop software attempts to spoof agent state.
7. Push notification contains sensitive data.
8. TURN relay becomes a data-exposure or cost-amplification point.
9. A malicious user abuses the public infrastructure.

Mitigations:

- device public keys;
- signed decisions;
- request-specific nonces;
- short-lived pairing sessions;
- minimal cloud knowledge;
- encrypted transport;
- request TTLs;
- rate limits;
- device revocation;
- audit trail;
- fail-closed behavior;
- abuse monitoring.

The security design should be independently reviewed before claiming production security.

---

## 38. MVP Scope

### Version 0.1

**Target:** prove the core workflow.

```text
Desktop: Windows
Mobile: Android
Agent: Cursor CLI via ACP
Transport: LAN + secure realtime connection
Cloud: shared signaling/control plane
Features:
- QR pairing
- permission capture
- push notification
- approve once
- deny
- request expiration
- basic device security
```

Success condition:

> A developer can start Cursor, leave the computer, receive a permission request on Android, tap Approve, and have Cursor continue without touching the computer.

---

## 39. Version Roadmap

### Phase 1 — Proof of Concept

```text
Cursor
Android
Windows
QR pairing
LAN transport
Approve/Deny
```

### Phase 2 — Remote MVP

```text
Cloud signaling
WebRTC
Push notifications
P2P transport
TURN fallback
Secure device identity
```

### Phase 3 — Production 1.0

```text
macOS
Linux
iOS
Cursor
Codex
Claude Code
Policy engine
Approval history
Risk classification
Multi-device
```

### Phase 4 — Ecosystem

```text
Open AgentApprove protocol
Third-party adapters
Plugin SDK
Public adapter registry
Self-hosting
Organization support
Emergency stop
```

### Phase 5 — Large-Scale Infrastructure

```text
Multi-region control plane
Adaptive routing
TURN capacity management
Abuse prevention
Operational dashboards
Formal protocol compatibility guarantees
```

---

## 40. Definition of Done for v1.0

AgentApprove v1.0 is complete when all of the following are true:

- [ ] Windows/macOS/Linux desktop bridge is stable.
- [ ] Android mobile application is stable.
- [ ] iOS mobile application is released.
- [ ] Cursor integration is reliable.
- [ ] Codex integration is reliable.
- [ ] Claude Code integration is reliable.
- [ ] Device pairing uses secure key exchange.
- [ ] Approvals are signed and replay-protected.
- [ ] Push notifications work reliably.
- [ ] Remote P2P connection works outside the LAN.
- [ ] TURN fallback works.
- [ ] Policy engine works locally.
- [ ] Requests expire safely.
- [ ] No network failure causes implicit approval.
- [ ] Self-hosted deployment is documented.
- [ ] Security review is completed.
- [ ] Automated integration/e2e test suite passes.
- [ ] Upgrade and rollback procedures exist.

---

## 41. Success Metrics

The project should measure product value rather than vanity metrics.

### Activation

- percentage of installs successfully paired;
- time from installation to first successful approval;
- percentage of users completing one approval from phone.

### Reliability

- permission delivery success rate;
- approval round-trip latency;
- reconnect success rate;
- P2P success rate;
- false-approval rate (target: zero).

### Adoption

- active devices;
- weekly active developers;
- permission requests resolved remotely;
- supported agent adapters;
- GitHub contributors.

### Trust

- security incidents;
- device revocations;
- failed verification attempts;
- support issues related to permissions.

---

## 42. Monetization / Sustainability

The intended end-user experience should remain free.

Because the official infrastructure can incur costs at large scale, long-term sustainability can come from a combination of:

- sponsorships;
- GitHub Sponsors;
- grants;
- donations;
- community-hosted infrastructure;
- optional managed features for organizations;
- paid enterprise support;
- hosted observability/administration features.

The core open-source functionality should remain usable without payment.

A future commercial layer must not make the basic permission-approval workflow unusable for free users.

---

## 43. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Agent vendors change permission APIs | High | Adapter interface + protocol abstraction |
| Push delivery is delayed | Medium | App syncs authoritative state after notification |
| P2P connection fails | Medium | LAN fallback + TURN fallback |
| TURN costs grow rapidly | High | P2P-first + quotas + optional self-hosting |
| Security vulnerability | Critical | Minimal privileges, signatures, threat model, security review |
| Cloud outage | High | Local LAN mode + degraded local operation |
| Public service abuse | High | Rate limits, device quotas, abuse controls |
| Project complexity grows too early | High | Cursor + Android MVP first |
| Free infrastructure limits change | Medium | Provider abstraction + self-hosting |
| User accidentally approves dangerous command | High | risk levels, details, policies, explicit confirmation for critical actions |

---

## 44. Product Principles

### Principle 1 — Permission, not remote shell

The phone should approve a specific action, not become an unrestricted remote terminal.

### Principle 2 — Local policy before cloud

Automatic decisions belong on the user's computer.

### Principle 3 — P2P before relay

Do not force all traffic through the official infrastructure.

### Principle 4 — Server is coordination, not authority

The cloud should not be able to silently approve an agent action.

### Principle 5 — Fail closed

Uncertainty means no approval.

### Principle 6 — Install and use

Normal users should not know what WebRTC, TURN, Durable Objects, databases, or signaling mean.

### Principle 7 — Open protocol

A new agent should be able to integrate without rewriting the platform.

---

## 45. Recommended Initial Build Order

The development sequence should be:

```text
1. Define AgentApprove protocol
2. Implement request/decision state machine
3. Build Cursor adapter
4. Build desktop bridge
5. Build Android approval UI
6. Implement QR pairing
7. Implement secure local transport
8. Implement shared cloud signaling
9. Add WebRTC P2P
10. Add FCM notifications
11. Add TURN fallback
12. Add policy engine
13. Add security hardening
14. Add Codex adapter
15. Add Claude Code adapter
16. Add self-hosting
17. Add iOS
```

The protocol and security model should be designed before adding multiple vendor adapters.

---

## 46. Final Architecture Decision

The recommended production architecture is:

```text
                        ┌──────────────────────────────┐
                        │      AGENTAPPROVE CLOUD      │
                        │                              │
                        │ Authentication              │
                        │ Device registry              │
                        │ Pairing                      │
                        │ WebRTC signaling             │
                        │ Realtime coordination        │
                        │ Push notification trigger    │
                        │ Minimal metadata             │
                        └───────────────┬──────────────┘
                                        │
                              signaling/control
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 │                                             │
                 ▼                                             ▼
        ┌──────────────────┐                         ┌──────────────────┐
        │   DESKTOP        │                         │     MOBILE       │
        │                  │                         │                  │
        │ AgentApprove     │◀════ secure P2P ═════▶ │ AgentApprove     │
        │ Bridge           │                         │ App              │
        │                  │                         │                  │
        │ Agent adapters   │                         │ Notifications    │
        │ Policy engine    │                         │ Approvals        │
        │ Crypto           │                         │ History          │
        │ Request queue    │                         │ Policies         │
        └────────┬─────────┘                         └──────────────────┘
                 │
                 ▼
      ┌──────────────────────────┐
      │ AI Coding Agents         │
      │                          │
      │ Cursor                   │
      │ Codex                    │
      │ Claude Code              │
      │ Antigravity              │
      │ Future agents            │
      └──────────────────────────┘

                  Optional fallback path:

        Desktop ───── TURN relay ───── Mobile
```

This architecture satisfies the core product requirements:

- users install and use without running infrastructure;
- the official service can serve many users;
- sensitive traffic can remain device-to-device;
- the service can scale independently of agent execution workloads;
- the project remains open source and self-hostable;
- multiple agent vendors can share the same permission protocol;
- security does not depend on trusting the cloud with arbitrary command execution.

---

## 47. Sources and Technical References

1. Cursor ACP documentation — permission request and decision flow:  
   https://prod.cursor.com/docs/cli/acp

2. Cursor permissions reference:  
   https://prod.cursor.com/docs/cli/reference/permissions

3. Claude Code permissions documentation:  
   https://code.claude.com/docs/en/permissions

4. Open-source `ai-approve` project for phone-based Codex/Claude approval:  
   https://github.com/jzethar/ai-approve

5. Cloudflare Durable Objects + WebSockets:  
   https://developers.cloudflare.com/durable-objects/best-practices/websockets/

6. Cloudflare Durable Objects overview and Free/Paid plan information:  
   https://developers.cloudflare.com/durable-objects/

7. Firebase Cloud Messaging:  
   https://firebase.google.com/docs/cloud-messaging

8. Apple remote notifications / APNs architecture:  
   https://developer.apple.com/documentation/usernotifications/setting-up-a-remote-notification-server

---

## 48. Conclusion

AgentApprove should be built as an **open-source remote permission infrastructure layer for AI coding agents**, not merely as a notification utility.

The MVP should prove one extremely valuable workflow:

```text
Agent asks permission
        ↓
Phone notification
        ↓
User approves/denies
        ↓
Agent continues/stops
```

The production architecture should then expand around that core with:

- a normalized permission protocol;
- agent adapters;
- local policy enforcement;
- cryptographically paired devices;
- signed, replay-protected decisions;
- P2P-first networking;
- cloud-assisted signaling;
- push notifications;
- TURN fallback;
- self-hosting;
- horizontal scaling;
- an open contributor ecosystem.

The project should optimize for one defining experience:

> **Install it, pair your phone, leave your AI agent working, and never have to walk back to the computer just to click “Approve.”**
