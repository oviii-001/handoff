# HandOff 📱⚡💻

> **Remote AI Agent Permission Approval** — Approve or reject sensitive coding agent actions (file writes, bash commands, deployment scripts) from your phone anywhere in the world.

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.2.x-blue.svg?logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-M3_Expressive-green.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Cloudflare Workers](https://img.shields.io/badge/Relay-Cloudflare_Durable_Objects-orange.svg?logo=cloudflare)](https://workers.cloudflare.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

---

## 🚀 Overview

Modern AI coding agents (Claude Code, Antigravity, Cursor, Codex) often ask for permission to run potentially destructive terminal commands (`rm -rf`, `npm install`, git push to main) or modify security-sensitive files. 

**HandOff** pairs your desktop environment directly with your mobile phone via an encrypted Cloudflare Relay. Whenever an agent requests tool access, a cryptographic approval card pops up on your phone with the command, target directory, and risk classification. You can approve or reject in real time with a single tap.

---

## 🏛 Architecture & Engineering Standards

HandOff is designed using **Clean Architecture + MVVM** and an **Offline-First Reactive Model**:

```
+-------------------------------------------------------+
|                 Desktop CLI / MCP Daemon              |
|  - MCP Stdio Server & Interactive Terminal CLI        |
|  - Terminal ASCII QR Code Pairing                     |
|  - End-to-End Cryptographic Signing (Ed25519)         |
+---------------------------+---------------------------+
                            | WebSocket
                            v
+-------------------------------------------------------+
|         Cloudflare Durable Object Relay               |
|  - Edge-deployed WebSocket broker (free-tier SQLite)  |
|  - Webhook endpoints for FCM push notifications       |
|  - Instant pairing & state synchronization            |
+---------------------------+---------------------------+
                            | WebSocket / FCM Push
                            v
+-------------------------------------------------------+
|             Mobile Phone (Android Native)             |
|  - Data: Room Database (Single Source of Truth)       |
|  - Domain: Pure Kotlin UseCases & Repositories        |
|  - UI: Jetpack Compose + Material 3 Expressive        |
|  - Background: WorkManager & Firebase Cloud Messaging |
+-------------------------------------------------------+
```

### Module Topology
- `:shared`: Domain models (`PermissionRequest`, `PermissionDecision`, `AgentInfo`) and protocol serialization (`kotlinx.serialization`).
- `:mobile:domain`: Pure Kotlin business logic (`ObserveRequestsUseCase`, `SendDecisionUseCase`, `PairDeviceUseCase`).
- `:mobile:data`: Room DB (`handoff_db`), continuous background WebSocket sync supervisor, and persistent preferences.
- `:mobile:feature:pairing`: CameraX QR barcode scanner + manual code input fallback.
- `:mobile:feature:approval`: Material 3 Expressive approval card with dynamic colors, motion scheme tokens, and interactive action buttons.
- `:desktopApp`: Kotlin JVM headless daemon with MCP server, Terminal QR generator (`--pair`), MCP auto-installer (`--install`), and manual command interceptor (`--exec`).
- `apps/relay`: Cloudflare Worker using Durable Objects (`RelayRoom`) routing real-time traffic between desktop and mobile endpoints.

---

## 🛠 Getting Started

> 📖 **Looking for full step-by-step instructions?** See the comprehensive [**Setup & Installation Guide**](SETUP_GUIDE.md) covering Android installation, Cloudflare relay hosting, MCP setup for Claude / Cursor / Antigravity, and end-to-end testing.

### Prerequisites
- JDK 17 or 21
- Android Studio Ladybug / Meerkat (Android SDK 35 / 36)
- Android device or emulator (tested on Android 15 / 16 KB page-size hardware, Google Pixel 9)
- Node.js 18+ & Wrangler (for Cloudflare Relay deployment)

### 1. Cloudflare Relay
The Cloudflare relay is deployed to:
```
wss://agentapprove-relay.ismamhasanovi.workers.dev
```
To deploy your own relay:
```bash
cd apps/relay
npx wrangler deploy
```

### 2. Run Desktop CLI
Generate a new pairing code or test a request using the included wrapper scripts:
```bash
# Generate a new pairing code and terminal ASCII QR
./handoff.sh --pair

# Automatically install HandOff into Claude Desktop, Cursor, or Antigravity
./handoff.sh --install

# Execute a command securely through HandOff's approval flow
./handoff.sh --exec "npm run build"

# Test dispatching a live critical permission request
./handoff.sh --test-request --pair-id <YOUR_PAIR_ID>
```

### 3. Install & Run Android App
Connect your Android phone via USB and run:
```bash
./gradlew :androidApp:installDebug
```
Open **HandOff** on your phone:
1. Scan the QR code or enter your Pair ID manually (e.g. `test-pixel-99`).
2. Tap **Connect & Pair**.
3. When the desktop dispatches a permission request, your phone immediately renders the approval card.
4. Tap **Approve** or **Reject**; desktop receives the decision instantly.

---

## 🧪 Testing & Verification

The project follows the Agile Testing Quadrants (Q1–Q4) with 100% passing tests:

```bash
# Run all unit and integration tests across all modules
./gradlew test
```

### Hardware Verification
- **Device Tested**: Google Pixel 9 (`Android 15`, Build `AP4A.241205.013`)
- **End-to-End Latency**: < 150ms roundtrip from CLI to Phone and back to CLI.

---

## 📄 License
MIT License. See [LICENSE](LICENSE) for details.