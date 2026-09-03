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
|  [Handoff Desktop Daemon / McpServer] (:desktopApp)                     |
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

### 1. Launch Pairing Mode
In your project terminal, execute:

```bash
# Windows PowerShell
.\gradlew :desktopApp:run --args="--pair"

# macOS / Linux
./gradlew :desktopApp:run --args="--pair"
```

The terminal will generate a unique session identifier:
```text
==================================================
       HandOff Desktop Pairing Mode          
==================================================
Pair ID: pair-a1b2c3d4
Relay  : agentapprove-relay.ismamhasanovi.workers.dev

Enter this code manually on your phone:
>>>  pair-a1b2c3d4  <<<

Or copy this pairing URL:
handoff://pair?pairId=pair-a1b2c3d4&host=agentapprove-relay.ismamhasanovi.workers.dev
==================================================
```

### 2. Pair on Mobile
1. Open **HandOff** on your Android device.
2. If unpaired, you will see the **Home Quickstart Guide**.
3. **Scan QR Code**: Tap **Scan QR Code** and point your phone at the terminal QR code, OR:
4. **Manual Code**: Type `pair-a1b2c3d4` into the input field and tap **Connect & Pair** (or tap the **Paste** icon).
5. The top bar status pill will transition to green: **`CONNECTED`**.

---

## Step 4: AI Agent Integration (MCP Configuration)

HandOff implements the **Model Context Protocol (MCP)** stdio interface, enabling seamless integration with any modern AI coding assistant.

### 1. Claude Desktop
Add HandOff to your `claude_desktop_config.json`:
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "handoff": {
      "command": "java",
      "args": [
        "-jar",
        "c:/Users/USERAS/Desktop/HandOff/handoff/desktopApp/build/libs/desktopApp.jar",
        "--mcp"
      ],
      "env": {
        "HANDOFF_RELAY_HOST": "agentapprove-relay.ismamhasanovi.workers.dev"
      }
    }
  }
}
```

### 2. Cursor Composer
Open Cursor Settings &rarr; **Features** &rarr; **MCP Servers** &rarr; **Add New MCP Server**:
- **Name**: `handoff`
- **Type**: `command`
- **Command**: `.\gradlew :desktopApp:run --args="--mcp"`

### 3. Claude Code CLI
Add HandOff to your Claude Code project configuration:
```bash
claude mcp add handoff -- gradlew :desktopApp:run --args="--mcp"
```

### 4. Antigravity IDE
Add the server definition to `~/.gemini/antigravity-ide/mcp_config.json`:
```json
{
  "mcpServers": {
    "handoff": {
      "command": "gradlew.bat",
      "args": [":desktopApp:run", "--args=--mcp"],
      "env": {
        "HANDOFF_RELAY_HOST": "agentapprove-relay.ismamhasanovi.workers.dev"
      }
    }
  }
}
```

---

## Step 5: End-to-End Testing & Verification

Use the built-in test suites in `:desktopApp` to simulate real agent requests without needing a live agent session:

### 1. Test Dangerous Shell Execution (Root Deletion Simulation)
Dispatches a critical risk shell action:
```bash
.\gradlew :desktopApp:run --args="--test-request --pair-id <YOUR_PAIR_ID>"
```
- **Mobile Behavior**: The phone displays a **CRITICAL** risk card with `rm -rf / --no-preserve-root`.
- **Action**: Tap **Reject** or **Approve (Requires Biometric)**.
- **Terminal Result**: The CLI outputs the decision timestamp, feedback notes, and device ID.

### 2. Test Multi-Step Implementation Plan Review
Dispatches a complex multi-file refactoring plan:
```bash
.\gradlew :desktopApp:run --args="--test-plan --pair-id <YOUR_PAIR_ID>"
```
- **Mobile Behavior**: The phone displays the **PlanApprovalCard** with bulleted steps, affected files, and user feedback field.
- **Action**: Add an optional steering note (e.g., *"Make sure to keep backward compatibility"*) and tap **Send Changes**.

### 3. Test Interactive Architectural Question
Dispatches a single or multi-choice question:
```bash
.\gradlew :desktopApp:run --args="--test-question --pair-id <YOUR_PAIR_ID>"
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

### Q1: `adb: error: failed to get feature set: device unauthorized`
- **Fix**: Check your phone's screen. A prompt asking *"Allow USB debugging?"* will appear. Check *"Always allow from this computer"* and tap **Allow**.

### Q2: 16 KB Page Size Crashes on Android 15 / Pixel 9
- **Status**: **Fully Supported**. HandOff uses 16 KB aligned libraries and WorkManager 2.10+ without legacy 4 KB native binary dependencies. If using custom native C++ libraries, ensure `-Wl,-z,max-page-size=16384` is enabled in CMake.

### Q3: WebSocket Disconnects or Reconnects Continuously
- Check if your corporate network or firewall blocks raw WebSocket traffic (`wss://`).
- Ensure `HANDOFF_RELAY_HOST` matches your deployed Cloudflare Worker domain.
- Verify your Pair ID has not expired or been unpaired from the Settings screen.

### Q4: Camera Scanner Does Not Open
- Check **Settings &rarr; Device Permissions Hub** in the mobile app.
- If Camera permission is marked `Not Granted`, tap the toggle or go to Android System Settings &rarr; Apps &rarr; HandOff &rarr; Permissions &rarr; Camera &rarr; **Allow while using app**.
- Alternatively, you can use the manual code entry on the Home screen to connect without camera access.

### Q5: How Do I Clear Audit History?
- Open the **Audit Log** tab from the bottom navigation bar.
- Tap the **Clear History** sweep icon in the top right corner.
- Confirm in the dialog to completely purge the local Room SQLite database.

---

*HandOff is built with Kotlin Multiplatform, Jetpack Compose, and Cloudflare Workers.*  
*Maintained with ❤️ by the HandOff Core Team.*
