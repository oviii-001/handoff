# HandOff — Complete Installation & Setup Guide 🚀📱⚡💻

> Step-by-step setup, configuration, deployment, and verification guide for **HandOff**: the zero-trust remote AI agent permission approval platform.

---

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites & System Requirements](#2-prerequisites--system-requirements)
3. [Step 1: Android Mobile App Installation](#step-1-android-mobile-app-installation)
4. [Step 2: Cloudflare Relay Setup (Edge WebSocket Broker)](#step-2-cloudflare-relay-setup-edge-websocket-broker)
5. [Step 3: Desktop Daemon & Pairing](#step-3-desktop-daemon--pairing)
6. [Step 4: AI Agent Integration (MCP Configuration)](#step-4-ai-agent-integration-mcp-configuration)
7. [Step 5: End-to-End Testing & Verification](#step-5-end-to-end-testing--verification)
8. [Step 6: Firebase Push Notifications Setup (Optional)](#step-6-firebase-push-notifications-setup-optional)
9. [Troubleshooting & FAQ](#9-troubleshooting--faq)
10. [Command Reference](#command-reference)

---

## 1. Architecture Overview

HandOff enables developers to safely delegate tasks to autonomous coding agents (Claude Code, Antigravity, Cursor, Codex) while retaining ultimate control over dangerous shell executions, file mutations, and schema migrations directly from an Android device anywhere in the world.

```
+-------------------------------------------------------------------------+
|                          DEVELOPER WORKSTATION                          |
|                                                                         |
|  [Coding Agents: Claude Code / Cursor / Codex / Antigravity]            |
|                                    │                                    |
|                         (MCP / Tool Dispatch)                           |
|                                    ▼                                    |
|  [Handoff Desktop Daemon / McpServer] (:cli)                     |
|  - Cryptographic request signing & session state                        |
+------------------------------------+------------------------------------+
                                     │
                             (WSS / Encrypted)
                                     ▼
+-------------------------------------------------------------------------+
|                  CLOUDFLARE EDGE RELAY (apps/relay)                     |
|                                                                         |
|  - Cloudflare Worker + Durable Objects (RelayRoom)                      |
|  - Ephemeral SQLite & D1 audit store                                    |
|  - Sub-100ms global message broker routing                              |
+------------------------------------+------------------------------------+
                                     │
                             (WSS / Encrypted)
                                     ▼
+-------------------------------------------------------------------------+
|                     ANDROID MOBILE PHONE (:androidApp)                  |
|                                                                         |
|  - Jetpack Compose + Material 3 Expressive (Dynamic Color & Motion)     |
|  - Offline-First Architecture (Room Database Single Source of Truth)   |
|  - CameraX QR Code Pairing & Biometric Security Pass                    |
|  - Live Request Card / Plan Review / Question Modal / Audit Log         |
+-------------------------------------------------------------------------+
```

---

## 2. Prerequisites & System Requirements

Ensure your environment meets the following specifications:

| Component | Minimum Requirement | Recommended |
| :--- | :--- | :--- |
| **Operating System** | macOS 13+, Ubuntu 22.04+, or Windows 10/11 | Windows 11 / macOS Sequoia |
| **Java Development Kit (JDK)** | JDK 17 | JDK 21 (Eclipse Temurin / OpenJDK) |
| **Android SDK** | compileSdk 36, minSdk 28 | Android Studio Meerkat / Ladybug |
| **Physical Phone / Emulator** | Android 9.0+ (API 28+) | Android 15 / 16 (e.g., Google Pixel 9) |
| **Node.js** (for Relay) | Node.js 18.0+ | Node.js 20 LTS & npm |
| **Cloudflare Wrangler** | Wrangler CLI v3.0+ | Latest `wrangler` |

---

## Step 1: Android Mobile App Installation

You can install HandOff directly onto your connected Android device or compile a standalone APK.

### Method A: Direct Install via Gradle & ADB (Recommended for Devs)

1. Connect your Android phone via USB and enable **USB Debugging** in Developer Options.
2. Confirm your device is recognized:
   ```bash
   adb devices
   ```
   *(Expected output: `4C050DLAQ001CY device`)*
3. Build and install the debug application:
   ```bash
   # Linux / macOS
   ./gradlew :androidApp:installDebug

   # Windows PowerShell
   .\gradlew :androidApp:installDebug
   ```
4. Launch the application:
   ```bash
   adb shell am start -n com.ovi.handoff/.MainActivity
   ```

### Method B: Build Standalone APK

1. Generate the APK:
   ```bash
   .\gradlew :androidApp:assembleDebug
   ```
2. The compiled APK will be located at:
   ```
   androidApp/build/outputs/apk/debug/androidApp-debug.apk
   ```
3. Transfer and install via ADB:
   ```bash
   adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   ```

### App Permissions Setup on First Launch

When opening **HandOff** for the first time:
- **Notifications**: Tap **Allow** when prompted on Android 13+ to receive instant approval alerts.
- **Camera**: Tap **Allow** when tapping "Scan QR Code" to enable CameraX scanning.
- **Biometrics**: Ensure your device has a Fingerprint, Face Unlock, or Screen Lock enabled to approve high-risk operations.

---

## Step 2: Cloudflare Relay Setup (Edge WebSocket Broker)

### Option 1: Use the Live Production Relay (Zero Setup)
A globally-deployed edge relay is already pre-configured out of the box:
```
wss://agentapprove-relay.ismamhasanovi.workers.dev
```
No additional setup is necessary if you use this default relay!

---

### Option 2: Self-Host Your Own Relay

To self-host the relay on your Cloudflare account:

1. Navigate to the relay directory:
   ```bash
   cd apps/relay
   npm install
   ```
2. Log in to your Cloudflare account:
   ```bash
   npx wrangler login
   ```
3. Create the D1 Database:
   ```bash
   npx wrangler d1 create agentapprove-d1
   ```
   *Note the `database_id` output from this command.*
4. Update [`apps/relay/wrangler.toml`](file:///c:/Users/USERAS/Desktop/HandOff/handoff/apps/relay/wrangler.toml) with your `database_id`:
   ```toml
   [[d1_databases]]
   binding = "DB"
   database_name = "agentapprove-d1"
   database_id = "<YOUR_DATABASE_ID>"
   ```
5. Apply the initial database schema:
   ```bash
   npx wrangler d1 execute agentapprove-d1 --file=./schema.sql
   ```
6. Deploy the Worker and Durable Object:
   ```bash
   npx wrangler deploy
   ```
7. Set the environment variable on your workstation:
   ```bash
   # Windows PowerShell
   $env:HANDOFF_RELAY_HOST="your-relay-subdomain.workers.dev"

   # macOS / Linux
   export HANDOFF_RELAY_HOST="your-relay-subdomain.workers.dev"
   ```

---

## Step 3: Desktop Daemon & Pairing

The desktop daemon manages session keys, communicates with the Cloudflare relay, and interfaces with your IDE agents via MCP.

### 1. Build the CLI once

```bash
# Windows PowerShell
.\gradlew.bat :cli:installDist

# macOS / Linux
./gradlew :cli:installDist
```

### 2. Launch Pairing Mode
In your project terminal, execute:

```bash
# Windows PowerShell
.\handoff.bat --pair

# macOS / Linux
./handoff.sh --pair
```

> [!IMPORTANT]
> **`--pair` stays open on purpose.** The relay assigns a pair room to the first *desktop* that
> connects with your pairing secret. Until that happens the room is unclaimed and your phone will be
> refused with *"No desktop has claimed this pair yet"*. Leaving `--pair` running claims the room, so
> keep the terminal open until it prints `Paired with …`.

The terminal generates a pairing identity, claims the relay room, prints an ASCII QR code, and then waits:

```text
==================================================
 HandOff pairing
==================================================
Pair ID    : pair-a1b2c3d4
Relay      : agentapprove-relay.ismamhasanovi.workers.dev
Protocol   : 2.0

Scan this with the HandOff app:

[ASCII QR Code Output Here]

Cannot scan? Paste this whole link into the app's manual pairing field:

  handoff://pair?v=2.0&pairId=pair-a1b2c3d4&host=agentapprove-relay.ismamhasanovi.workers.dev&pubKey=<key>&token=<secret>

Treat that link like a password: it authorizes a device to approve your agent's actions.
Run `handoff --rotate-pair` to invalidate it.

Relay      : connected

Waiting for your phone to scan the code...   (Ctrl-C to stop)
```

> [!WARNING]
> The link contains `token=<secret>`, which authorizes any device holding it to approve your agent's
> actions. Never paste it into a chat, a screenshot, or an issue. `handoff --rotate-pair` invalidates it.

### 3. Pair on Mobile
1. Open **HandOff** on your Android device.
2. If unpaired, you will see the **Home Quickstart Guide**.
3. **Scan QR Code**: Tap **Scan QR Code** and point your phone at the terminal QR code, OR
4. **Manual**: paste the **whole `handoff://pair?...` link** into the input field and tap **Connect & Pair**.
   A bare pair id is not enough — it carries no relay token, so the relay would refuse the connection.
5. The app confirms the relay accepted it before reporting success. If it cannot connect, it shows the
   relay's own reason rather than a generic error.
6. The terminal running `--pair` prints `Paired with <deviceId>` and the app's status pill turns green: **`CONNECTED`**.

### 4. Verify

```bash
.\handoff.bat --doctor
```

This checks the local identity, relay reachability, the pair room claim, phone pairing and presence,
runs an in-process MCP handshake, and reports which IDEs have HandOff registered.

---

## Step 4: AI Agent Integration (MCP Configuration)

HandOff implements the **Model Context Protocol (MCP)** stdio interface, enabling seamless integration with any modern AI coding assistant (Antigravity IDE, Cursor, Claude Desktop, Claude Code, VS Code, Windsurf, etc.). It negotiates protocol revisions `2025-06-18`, `2025-03-26` and `2024-11-05`.

### 1. Build the Distribution First
If you have not already:

```bash
# Windows PowerShell
.\gradlew.bat :cli:installDist

# macOS / Linux
./gradlew :cli:installDist
```
This builds all required runtime libraries into `cli/build/install/cli/lib/*`.

---

### Option A: Auto-Installation (Recommended)
HandOff detects and registers itself with supported IDEs (Claude Desktop, Claude Code, Cursor, Windsurf, Antigravity IDE, VS Code):

```bash
# Windows
.\handoff.bat --install

# macOS / Linux
./handoff.sh --install
```

It backs up each config before writing, skips tools that are not installed, and is safe to re-run —
an already-correct entry is left untouched. For **Claude Code** it prefers the `claude mcp add-json`
CLI when available, because `~/.claude.json` is live state that Claude Code rewrites while running.

Restart your IDE, then confirm with `.\handoff.bat --doctor`.

---

### Option B: Manual Configuration

> [!TIP]
> **Why direct `java` invocation:** launching the stdio server through `cli.bat` / `cmd.exe` adds a
> shell whose own output shares the server's stdout, and stdout *is* the JSON-RPC stream. A single
> stray line corrupts every frame after it, and the client cannot resynchronise — the server simply
> appears to hang. `--install` generates the `java -classpath` form below for exactly this reason.
> The classpath uses a `lib/*` wildcard so a dependency version bump does not invalidate your config.

Replace `C:\path\to\handoff` with your actual checkout path.

#### 1. Claude Desktop, Cursor, Windsurf, Antigravity IDE

These all use the `mcpServers` shape. Config locations:

| IDE | Path |
| :--- | :--- |
| Claude Desktop (Windows) | `%APPDATA%\Claude\claude_desktop_config.json` |
| Claude Desktop (macOS) | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Cursor | `~/.cursor/mcp.json` |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` |
| Antigravity IDE | `~/.gemini/config/mcp_config.json` |

```json
{
  "mcpServers": {
    "handoff": {
      "command": "java",
      "args": [
        "-classpath",
        "C:\\path\\to\\handoff\\cli\\build\\install\\cli\\lib\\*",
        "com.ovi.handoff.MainKt",
        "--mcp"
      ]
    }
  }
}
```
*(On macOS / Linux use forward slashes: `"/path/to/handoff/cli/build/install/cli/lib/*"`)*

#### 2. VS Code

VS Code uses a **different shape** in `mcp.json` — the key is `servers`, and each entry declares its
transport type. An `mcpServers` block here is parsed and then ignored.

- **Windows**: `%APPDATA%\Code\User\mcp.json`
- **macOS**: `~/Library/Application Support/Code/User/mcp.json`
- **Linux**: `~/.config/Code/User/mcp.json`

```json
{
  "servers": {
    "handoff": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-classpath",
        "C:\\path\\to\\handoff\\cli\\build\\install\\cli\\lib\\*",
        "com.ovi.handoff.MainKt",
        "--mcp"
      ]
    }
  }
}
```

#### 3. Claude Code CLI

```bash
claude mcp add handoff -- java -classpath "C:/path/to/handoff/cli/build/install/cli/lib/*" com.ovi.handoff.MainKt --mcp
```

---

### Available MCP Tools in HandOff

Once integrated, your coding agents automatically gain access to 4 human-in-the-loop governance tools:

| Tool Name | Purpose | Mobile Experience |
| :--- | :--- | :--- |
| **`handoff_approve`** | Request human approval before executing dangerous commands or mutating sensitive files. | Renders high-priority `LiveRequestScreen` with risk badges, justifications, and diff/terminal snippet. |
| **`handoff_ask_question`** | Prompt the developer with multiple-choice questions or open write-ins to clarify ambiguous requirements. | Renders interactive `QuestionModal` with radio options and write-in feedback. |
| **`handoff_request_plan_approval`** | Submit multi-phase implementation plans for user review before writing code. | Renders `PlanApprovalCard` with step breakdown and steering notes. |
| **`handoff_status`** | Report whether HandOff can actually reach your phone, plus the next step when it cannot. | Diagnostic query executed in real-time. |

An approval returns `isError=true` when the action was **not** authorized. The accompanying text
distinguishes the cases, so the agent can tell the user which one applies:

| Situation | Behaviour |
| :--- | :--- |
| No phone ever paired | Returns immediately: *"No phone is paired with this desktop… run `handoff --pair`"* |
| Phone offline and unreachable by push | Returns after a ~20 s grace window, in case the phone reconnects |
| Phone reachable | Waits for the user, up to the request's deadline (default 5 minutes) |
| Relay backlog full | Returns immediately, asking the user to clear pending approvals |

---

## Step 5: End-to-End Testing & Verification

Use the built-in test suites in `:cli` to simulate real agent requests without needing a live agent session:

### 1. Test Dangerous Shell Execution (Root Deletion Simulation)
Dispatches a critical risk shell action:
```bash
.\handoff.bat --test-request --pair-id <YOUR_PAIR_ID>
```
- **Mobile Behavior**: The phone displays a **CRITICAL** risk card with `rm -rf / --no-preserve-root`.
- **Action**: Tap **Reject** or **Approve (Requires Biometric)**.
- **Terminal Result**: The CLI outputs the decision timestamp, feedback notes, and device ID.

### 2. Test Multi-Step Implementation Plan Review
Dispatches a complex multi-file refactoring plan:
```bash
.\handoff.bat --test-plan --pair-id <YOUR_PAIR_ID>
```
- **Mobile Behavior**: The phone displays the **PlanApprovalCard** with bulleted steps, affected files, and user feedback field.
- **Action**: Add an optional steering note (e.g., *"Make sure to keep backward compatibility"*) and tap **Send Changes**.

### 3. Test Interactive Architectural Question
Dispatches a single or multi-choice question:
```bash
.\handoff.bat --test-question --pair-id <YOUR_PAIR_ID>
```
- **Mobile Behavior**: The phone renders the **QuestionModal** with selectable radio buttons.
- **Action**: Select an option or provide a custom write-in response and tap **Submit Decision**.

### 4. Test Emergency Session Halt
In any active session, tap the **Emergency Halt** button in the mobile TopAppBar or Home card:
- Displays a safety confirmation dialog.
- Purges all pending authorizations.
- Sends an immediate abort signal to the desktop daemon, terminating agent subprocesses.

---

## Step 6: Firebase Push Notifications Setup (Optional)

To enable background wake-up notifications when the mobile app is not active:

1. Create a project at [Firebase Console](https://console.firebase.google.com/).
2. Register an Android App with package name `com.ovi.handoff`.
3. Download `google-services.json` and place it at:
   ```
   androidApp/google-services.json
   ```
4. In Firebase Settings &rarr; **Service Accounts**, click **Generate new private key** to download your JSON credentials.
5. In your terminal under `apps/relay/`, upload the secret to Cloudflare:
   ```bash
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT
   ```
   Paste the entire contents of the service account JSON when prompted.
6. Re-deploy the relay: `npx wrangler deploy`.

---

## 9. Troubleshooting & FAQ

> **Start here:** `handoff --doctor` checks every hop in order and prints the command that fixes each
> failure. The daemon also writes `~/.handoff/logs/handoff.log`, which is the artifact to attach to a
> bug report.

### Q0: I scanned the QR code and the phone says it could not connect

Almost always the pair room was never claimed. `handoff --pair` must be **left running** while you
scan — it is what claims the room on the relay. If the terminal already exited, run it again and keep
it open. The app now shows the relay's own reason instead of a generic error:

| Message on the phone | Cause | Fix |
| :--- | :--- | :--- |
| "No computer has claimed this pairing code yet" | `--pair` not running, or the IDE's MCP server never started | Run `handoff --pair` and leave it open |
| "This pair id is claimed by a different device or an older pairing secret" | You re-ran `--rotate-pair`, or another machine uses this pair id | Run `handoff --rotate-pair`, then `handoff --pair` |
| "This pairing has no relay token" | You typed a bare pair id instead of the whole link | Paste the full `handoff://pair?...` link, or scan the QR |
| "the live channel did not open" | The network blocks WebSockets | Try mobile data; some corporate/public Wi-Fi blocks `wss://` |

### Q1: My agent says "No phone is paired with this desktop"

The desktop has never received a signing key from a phone, so no approval could ever be answered.
The tool call fails immediately rather than blocking for five minutes. Run `handoff --pair`, scan,
and confirm with `handoff --doctor` that **Phone paired** passes.

### Q2: The MCP server does not appear in my IDE

1. Run `handoff --doctor` and read the **IDE registration** section — it names each config file and
   whether HandOff is registered and current.
2. Re-run `handoff --install`, then fully restart the IDE.
3. If you configured it by hand, check the shape: **VS Code uses `servers` with `"type": "stdio"`**,
   every other editor uses `mcpServers`. An `mcpServers` block in VS Code's `mcp.json` is ignored.
4. Confirm the classpath points at an existing `cli/build/install/cli/lib` directory. If you moved
   the repository, re-run `.\gradlew.bat :cli:installDist` and then `handoff --install`.

### Q3: The server starts but every tool call hangs

Something other than the protocol is writing to the server's stdout, which corrupts the JSON-RPC
stream. HandOff redirects `System.out` to stderr on startup to prevent this, so the usual remaining
cause is a shell wrapper: launch with `java -classpath …` rather than through `cli.bat` / `cmd.exe`.

### Q4: `adb: error: failed to get feature set: device unauthorized`
- **Fix**: Check your phone's screen. A prompt asking *"Allow USB debugging?"* will appear. Check *"Always allow from this computer"* and tap **Allow**.

### Q5: 16 KB Page Size Crashes on Android 15 / Pixel 9
- **Status**: **Fully Supported**. HandOff uses 16 KB aligned libraries and WorkManager 2.10+ without legacy 4 KB native binary dependencies. If using custom native C++ libraries, ensure `-Wl,-z,max-page-size=16384` is enabled in CMake.

### Q6: WebSocket Disconnects or Reconnects Continuously
- Check if your corporate network or firewall blocks raw WebSocket traffic (`wss://`).
- Ensure `HANDOFF_RELAY_HOST` matches your deployed Cloudflare Worker domain.
- Verify your Pair ID has not expired or been unpaired from the Settings screen.

### Q7: Camera Scanner Does Not Open
- Check **Settings &rarr; Device Permissions Hub** in the mobile app.
- If Camera permission is marked `Not Granted`, tap the toggle or go to Android System Settings &rarr; Apps &rarr; HandOff &rarr; Permissions &rarr; Camera &rarr; **Allow while using app**.
- Alternatively, paste the full `handoff://pair?...` link on the Home screen to connect without camera access.

### Q8: How Do I Clear Audit History?
- Open the **Audit Log** tab from the bottom navigation bar.
- Tap the **Clear History** sweep icon in the top right corner.
- Confirm in the dialog to completely purge the local Room SQLite database.

### Q9: I need to revoke a pairing

`handoff --rotate-pair` issues a new pair id and secret and forgets the phone's key. Every previously
paired device is refused by the relay from that moment. Then run `handoff --pair` to pair again.

---

## Command Reference

| Command | Purpose |
| :--- | :--- |
| `handoff --pair` | Claim the relay room, show the pairing code, and wait for the phone |
| `handoff --doctor` | Diagnose every hop and print the fix for each failure |
| `handoff --status` | Short summary of pairing, key and relay state |
| `handoff --install` | Register HandOff with every installed IDE |
| `handoff --rotate-pair` | Issue a new pair id and secret, revoking paired phones |
| `handoff --mcp` | Run the MCP server on stdio (what your IDE invokes) |
| `handoff --exec <cmd>` | Require phone approval before running a local command |

| Environment variable | Purpose |
| :--- | :--- |
| `HANDOFF_RELAY_HOST` | Point at a self-hosted relay |
| `HANDOFF_PAIR_ID` | Override the configured pair id |
| `HANDOFF_WORKSPACE` | Override the detected workspace path |
| `HANDOFF_LAUNCHER` | Command an IDE should run to start the MCP server |
| `HANDOFF_INSECURE=1` | Accept unsigned decisions (pre-v2 phones only; not recommended) |

---

*HandOff is built with Kotlin Multiplatform, Jetpack Compose, and Cloudflare Workers.*  
*Maintained with ❤️ by the HandOff Core Team.*
