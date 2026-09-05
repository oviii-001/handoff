# Changelog

All notable changes to the HandOff project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.0] - 2026-09-05

Production hardening of the MCP connection path, push notification styling, and mobile UX overhaul.

### Added
- **6-Digit Pairing PIN & Clipboard Auto-Detection**:
  - Desktop CLI generates a friendly 6-digit PIN (e.g. `178 324`) and registers it with the relay.
  - Mobile app supports direct 6-digit PIN input without scanning QR codes or pasting long URLs.
  - Automatic clipboard snooping detects pairing codes/links on resume and displays a 1-tap pairing card.
  - Zero-touch pairing over ADB automatically injects pairing deep links into connected devices.
- **Global Windows CLI Command (`handoff`)**:
  - Installed a global command shim in `%LOCALAPPDATA%\Microsoft\WindowsApps\handoff.cmd`.
  - Developers can run `handoff`, `handoff --pair`, `handoff --status`, and `handoff --doctor` from any terminal without `./handoff.bat`.
- **Material 3 Expressive Push Notification Overhaul**:
  - Risk-adaptive colorization (crimson `#D32F2F` for Critical with heads-up alerts; orange `#E65100` for High; teal `#0288D1` for Medium/Low).
  - Monospace formatting for shell commands (`$> command_name`).
  - Contextual direct action buttons (`[Approve Once]`, `[Deny]`, `[Answer]`, `[Review Plan]`).
  - Clean Material 3 notification hierarchy without redundant circular initial-letter badges.
- **Calm Approval UX**:
  - Replaced anxiety-inducing shrinking progress bars with calm countdown badges (`⏱️ 4m left`).
  - Added an interactive `[+5m Extend]` chip allowing users to extend review deadlines during complex code inspection.
- **Clean UI Hierarchy**:
  - Removed duplicate `IDE:` and `Working:` subtitles from `HomeTopAppBar` to ensure clean, developer-friendly presentation.

### Fixed
- **Pairing could not work while the IDE was closed.** `handoff --pair` printed a QR code and exited
  without ever connecting to the relay. Because the relay assigns a pair room to the first *desktop*
  socket, the room stayed unclaimed and the phone was refused with a bare `401`. `--pair` now opens
  the socket first — claiming the room — then shows the code and waits, reporting `Paired with <id>`
  when the phone arrives. It also diagnoses a failed claim, distinguishing an unreachable relay from
  a pair id already claimed by another machine.
- **The phone reported success for a pairing the relay had rejected.** `PairDeviceUseCase` wrote the
  pairing to disk and returned success without confirming anything, so the app showed "Paired.
  Waiting for your agent." while its socket was being refused. Pairing now waits for the relay to
  accept the device, and on failure rolls the pairing back and shows the relay's own reason.
- **An unpaired or unreachable phone blocked the agent for five minutes.** `RelayClient` waited each
  request's full TTL in every failure case, and discarded the relay's `ack` — including
  `status: "rejected"`. Approvals now fail immediately when no phone has ever paired, and within a
  ~20 s grace window when the relay confirms the request reached no socket and no push was possible.
- **VS Code integration silently did nothing.** The installer wrote `mcpServers`, but VS Code reads
  `servers` with an explicit `"type": "stdio"`. The config shape is now a property of each target
  rather than sniffed from existing file content, which got it wrong on every first install.
- **The generated launch command rotted.** It was derived from the current working directory, so the
  config broke when the repository moved. It is now derived from the running process's own
  installation and uses a `lib/*` wildcard classpath, which survives dependency version bumps. This
  also resolves the contradiction where the installer preferred `cli.bat` while `SETUP_GUIDE.md` told
  users to avoid it.
- **The protocol version was asserted, not negotiated.** The server always answered `2024-11-05`
  whatever the client requested. It now echoes the client's version when supported
  (`2025-06-18`, `2025-03-26`, `2024-11-05`) and otherwise names the newest it supports.
- **`roots/list` was sent to clients that never declared the capability**, always under the same
  hardcoded request id — two outstanding requests could share an id, which JSON-RPC forbids. It is
  now capability-gated with a unique id per request, and a client's error reply is handled.
- **Concurrent tool calls could mislabel each other's approval card.** `workspacePath` was a shared
  field rewritten by every call; the working directory is now resolved per call and passed down.
- **Cancelling a tool call left the approval on the phone.** `notifications/cancelled` only cancelled
  the local job. It now also releases the relay request and sends a `cancel` frame, so the card
  disappears and the request is not replayed on reconnect.
- **`RelayRepositoryImplTest` did not compile** against the current constructor and has been rewritten.
- Onboarding text told users to run `handoff daemon`, a command that has never existed, and the manual
  pairing field invited a bare pair id, which is always rejected because it carries no relay token.

### Added
- **`handoff --doctor`**: checks local identity, relay reachability, pair-room claim, phone pairing
  and presence, runs an in-process MCP handshake, and reports which IDEs have HandOff registered —
  each failure printed with the command that fixes it.
- **Stdout isolation.** The real stdout is claimed at startup and `System.out` is redirected to
  stderr, so a stray `println` from any dependency can no longer corrupt the JSON-RPC stream — a
  failure mode that presents as an unrecoverable hang.
- **File logging** at `~/.handoff/logs/handoff.log`, size-capped with one rotation, so a support
  report has an artifact that is not buried in per-IDE log panes.
- **Relay delivery reporting** (additive, backward compatible): `ack` frames now carry `delivered`,
  `pushQueued`, `phoneOnline` and `desktopOnline`; new `presence` frames tell each side whether its
  peer holds a socket; a new `cancel` frame drops an abandoned request.
- **Structured relay errors and a `GET /pair/:pairId` status route**, so a client that cannot open a
  socket can still learn why. A rejected WebSocket upgrade is opaque to both Ktor and OkHttp.
- **`connectionError` on `RelayRepository`**, surfaced in the connection banner, replacing a generic
  "offline" with the relay's actual reason.
- Tests where there were none: MCP protocol negotiation and dispatch, JSON-RPC transport framing and
  concurrency, installer config shapes and idempotency, wait-budget arithmetic, relay ack/presence/
  cancel/auth, and pairing rollback.

### Changed
- `sendRequestAndWaitForDecision` returns a sealed `ApprovalOutcome` rather than a nullable decision,
  so `NotPaired`, `PhoneUnreachable`, `RejectedByRelay`, `Expired` and `RelayUnreachable` are no
  longer indistinguishable from each other.
- `handoff_status` leads with a plain-language verdict and next step, keeping the JSON as a second
  content block.
- `McpServer` is split into a thin process entry point and a testable `McpProtocolServer`.
- MCP server version is now `2.1.0`.
- `SETUP_GUIDE.md` rewritten for Steps 3–4, with a per-message troubleshooting table and a command
  reference. The sample pairing URL previously omitted `token=` and `v=` — exactly the shape the app
  rejects.

## [1.5.0] - 2026-09-05

### Added
- **Production-Grade Mobile UI & UX Redesign**:
  - **Zero-Container Embeddable Snippets**: Introduced `TerminalSnippet(command, cwd)` in `TerminalCard.kt` and `DiffViewerSnippet(filePath, diffContent)` in `DiffViewerCard.kt`, allowing commands and code patches to embed directly inside cards without extra outer container wrappers.
  - **Unified Request Surface (`UnifiedRequestCard`)**: Consolidated fragmented multi-card stacks in `LiveRequestScreen` into a single cohesive surface with integrated agent identity, risk callout, and embedded payload view.
  - **Typographical Dashboard Metrics**: Replaced redundant IDE name chips with a clean 3-column stats layout (`Reviewed`, `Pending`, `Zero-Trust Security`) in `ActiveSessionScreen`.
  - **Zero-Trust E2EE Security Badge**: Added a subtle `🔒 E2EE` cryptographic badge to the hero banner header.
- **Relay & Protocol Hardening**:
  - **Cloudflare Worker Hibernation & Testing**: Added Vitest test suite for Cloudflare Worker relay (`relay.spec.ts`) with Durable Object WebSocket hibernation support and structured error responses.
  - **Shared Wire Protocol & Canonical Crypto**: Added `Canonical.kt`, `Sha256.kt`, and `Protocol.kt` in `:shared` for wire protocol framing, canonical decision signing, and HMAC verification.
  - **Clean Architecture Domain Expansion**: Added `RequestUseCases.kt` and `SubmitDecisionUseCase.kt` in `:mobile:domain`, paired with hardware-backed `AndroidDecisionSigner.kt`, `RequestVerifier.kt`, and `SecretVault.kt` in `:mobile:data:security`.
  - **Room Database Schema v3**: Implemented Room migration and schema versioning for resilient local request tracking.

### Changed
- **Elimination of Nested Cards Anti-Pattern**: Eradicated all cards-inside-cards designs across `LiveRequestScreen`, `ActiveSessionScreen`, `UnpairedHomeScreen`, and `AuditScreen`.
- **Removal of Duplicate Status & Elements**:
  - Removed duplicate `OFFLINE`/`CONNECTED` status pills from hero cards, centralizing connection state exclusively in `HomeTopAppBar`.
  - Removed duplicate agent identity labels and workspace paths between summary cards and code snippet cards.
  - Removed redundant bottom emergency halt button, keeping the persistent emergency action in the top app bar.
- **Streamlined Live Action Buttons**: Reorganized authorization actions into an authoritative elevated primary `Approve Once` button alongside clear secondary `Deny` and `Deny with Note` actions.
- **100% String Localization**: Extracted all user-facing strings across mobile screens into `strings.xml`.

## [1.4.1] - 2026-09-04

### Added
- **MCP `roots/list` Dynamic Workspace Discovery**:
  - Implemented dynamic project workspace root discovery via standard MCP `roots/list` requests and `notifications/roots/list_changed` events in `McpServer`.
  - Added optional `cwd` parameter to all MCP tool definitions (`handoff_approve`, `handoff_ask_question`, `handoff_request_plan_approval`, `handoff_status`), enabling callers to dynamically provide working directories per request.

### Changed
- **Universal MCP Configuration**:
  - Removed static `HANDOFF_WORKSPACE` environment variable bindings from `McpAutoInstaller` and global `mcp_config.json`, allowing the HandOff MCP server to adapt dynamically across different projects.
  - Configured direct `java -classpath` invocation in `McpAutoInstaller` for cross-platform process spawning reliability.

### Fixed
- **Mobile UI Workspace Display**:
  - Enhanced `resolveProjectOrWorkspace` in `PermissionRequest.kt` to prioritize real filesystem paths, normalize Windows backslashes, and filter out IDE program directories (`AppData/Local/Programs/Antigravity IDE`, `Program Files`).
  - Added single-line truncation with ellipsis on Home screen TopAppBar and Connected IDE card to gracefully handle long path strings on mobile viewports.

## [1.4.0] - 2026-09-04

### Added
- **Dynamic IDE & Workspace Detection**:
  - Automatically identifies connected AI IDE clients (Antigravity, Cursor, Claude Code, VSCode, IntelliJ, Android Studio, Gemini) via MCP `clientInfo` and environment variables.
  - Dynamically detects and resolves the active workspace folder name from MCP `workspaceFolders` or project directories.
  - Added `@Serializable data class SessionAnnouncement` to the shared communication protocol, broadcasted over WebSockets immediately upon MCP client initialization.
  - Persisted active IDE and workspace metadata in `PairingRepository` via `SharedPreferences`, enabling Android to display the connected agent and workspace even when standing by with zero pending requests.
  - Displayed connected IDE chip in the Standby Hero card, Agent Mesh card with IDE version and workspace folder, and an active workspace indicator badge in the top app bar.
- **Production-Grade MCP Server Overhaul**:
  - **Eliminated Stdout Pollution**: Redirected all startup greetings, diagnostic logs, and build notices to `System.err` / `>&2`. The `stdout` stream is strictly reserved for pure JSON-RPC 2.0 messages, resolving parsing errors in Antigravity and other MCP clients.
  - **Comprehensive Protocol Support**: Implemented handlers for `initialize`, `notifications/initialized`, `tools/list`, `resources/list`, `prompts/list`, and `ping`, with standard JSON-RPC `-32601` error responses for unsupported methods.
  - **Rich Zero-Trust Tools**:
    - `handoff_approve`: Prompts mobile device for zero-trust authorization before executing dangerous commands or modifying files.
    - `handoff_ask_question`: Presents interactive multiple-choice questions on mobile to clarify agent requirements.
    - `handoff_request_plan_approval`: Submits architectural implementation plans for human review before proceeding.
    - `handoff_status`: Returns current pair ID, connected IDE, and relay status.
  - **Cryptographic Request Signing**: Every MCP authorization request is signed with local hardware/software Ed25519 keys via `KeyStoreManager`.
  - **Auto-Installer Support**: Updated `McpAutoInstaller` to target compiled application binaries (`cli.bat` / `cli`) and auto-inject into `~/.gemini/config/mcp_config.json` and `~/.gemini/antigravity-ide/mcp_config.json`.
- **Compact & High-Contrast Terminal QR Code**:
  - Replaced oversized full-block renderer with Unicode half-block characters (`▀`, `▄`, `█`), cutting QR vertical height and horizontal width by 50% so it fits cleanly in any terminal window without scrolling.
  - Enforced UTF-8 output streams (`chcp 65001`, JVM args) and ANSI contrast sequences (`\u001B[40m\u001B[97m`) so QR codes render with a solid quiet zone and scan instantly on phone cameras across both dark and light terminal themes.

### Changed
- **CLI Architecture Refactor**:
  - Renamed legacy `:desktopApp` module and directory to `:cli` across Gradle build scripts, launch wrappers, and documentation.
  - Updated `handoff.bat` and `handoff.sh` to execute the fast standalone distribution binary (`cli/build/install/cli/bin/cli.bat`).
- **Documentation**:
  - Switched the main project logo format from SVG to PNG (`Handoff_Logo.png`) in `README.md`.

### Fixed
- Fixed Android app name displaying as "AgentApprove" by normalizing all `app_name` string resources to "HandOff" in `:androidApp` and `:mobile:feature:approval`.
- Fixed MCP server IDE identification in headless agent environments by adding explicit checks for the `ANTIGRAVITY_AGENT` environment variable.
- Fixed MCP server workspace detection in headless agent environments by adding fallback support for the `HANDOFF_WORKSPACE` environment variable, injected via `mcp_config.json`.

## [1.3.1] - 2026-09-04

### Changed
- Improved professionalism of Relay Server settings UI by masking raw URLs with "Default Public Relay" or "Custom Private Relay" labels.

### Fixed
- Fixed white screen rendering bug on the edges of the display during edge-to-edge navigation transitions by wrapping the root `NavHost` in a themed `Surface`.

## [1.3.0] - 2026-09-04

### Added
- **Push Notifications & Background Sync**:
  - Implemented Firebase Cloud Messaging (FCM) integration for remote wake-ups.
  - Added background WorkManager jobs that sync with Room DB even when the app is closed.
  - Implemented Webhook relay endpoint to forward pending requests to FCM.
- **CLI Wrapper Scripts**:
  - Added `handoff.bat` and `handoff.sh` to the project root for streamlined execution.
  - Added `--install` argument for automated MCP configuration injection into Claude Desktop, Cursor, and Antigravity IDE.
  - Added `--exec <cmd>` argument to manually intercept and dispatch any arbitrary terminal command.
  - Added ASCII QR Code generation directly in the terminal for faster pairing without a GUI.
- **Resiliency & Security**:
  - Implemented full-jitter exponential backoff for WebSocket reconnections in the mobile client (`RelayRepositoryImpl`).
  - Replaced legacy Regex JSON parsing in `CommandTokenizer` with strict AST-based validation for robust MCP command interception.
  - Enforced hardware-backed biometric security constraints (CryptoObject) with automatic fallback handling.

### Changed
- **BREAKING CHANGE**: Completely removed the Compose Multiplatform Desktop UI (`:desktopApp` UI package) in favor of a lightweight, headless CLI architecture, drastically reducing the binary size and improving MCP integration speed.
- Updated `SETUP_GUIDE.md` to reflect the new purely CLI-based setup process.

## [1.2.0] - 2026-09-04

### Added
- **Cryptographic Request Signing & Key Exchange (Ed25519)**:
  - Added cryptographic payload signing in Desktop daemon (`RelayClient`, `KeyStoreManager`).
  - Added optional `signature` field to shared `PermissionRequest` protocol specification.
  - Embedded Base64URL-encoded public key parameter (`&pubKey=...`) into desktop pairing mode CLI QR output and URLs.
  - Implemented public key extraction and decoding in `PairDeviceUseCase`, forwarding public keys to `PairingRepository`.
  - Added unit test suite in `PairDeviceUseCaseTest` for public key payload parsing and validation.

### Changed
- **Mobile Presentation Modularization**:
  - Extracted `HomeScreen` and `HomeTopAppBar` into dedicated, self-contained `HomeScreen.kt` under `:mobile:feature:approval:ui:home`.
  - Refactored `ApprovalScreen.kt` to focus purely on top-level scaffold, animated tab transitions, and bottom navigation bar.
  - Standardized component visibility modifiers, removing redundant `public` keywords across domain use cases and UI components.
  - Streamlined Composable parameters and added `@SuppressLint("LocalContextGetResourceValueCall")` to ensure clean lint passes.

## [1.1.0] - 2026-09-04

### Added
- **Production-Grade Material 3 Expressive Navigation**:
  - Material 3 Navigation Bar with dynamic badges (active request badge on Home, count badge on Audit Log).
  - Physics-based spring animations (`MotionScheme.expressive()`) across NavHost and tab switches.
  - Dedicated screen-specific TopAppBars (`HomeTopAppBar`, `AuditTopAppBar`, `SettingsTopAppBar`).
  - Full-screen back button navigation on Pairing and Settings screens.
- **Integrated Home Pairing & Onboarding (`UnpairedHomeView`)**:
  - Interactive 3-step quickstart guide directly on the Home screen for first-time installation.
  - Quick action to open CameraX QR barcode scanner.
  - Manual pairing code input with 1-tap clipboard paste and live validation.
- **Comprehensive Permissions & Permissions Hub**:
  - Added Android runtime and manifest permissions (`CAMERA`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC`, `VIBRATE`, `ACCESS_NETWORK_STATE`, `INTERNET`).
  - Live Device Permissions Hub in Settings displaying real-time status for Camera, Notifications, and Biometrics.
  - Android 13+ runtime notification prompt on app launch.
- **Strict UI Data Model Architecture**:
  - Introduced dedicated immutable UI models (`PermissionRequestUiModel`, `ConnectedAgentUiModel`, `PlanUiModel`, `QuestionUiModel`).
  - Decoupled all presentation layer Composables from domain entities and Room entities.
  - Safe boundary mappers (`toUiModel()`) in ViewModel.
- **100% String Resource Extraction (i18n)**:
  - Extracted all user-facing text to `strings.xml` across `:mobile:core`, `:mobile:feature:approval`, and `:mobile:feature:pairing`.
  - Zero hardcoded strings in Composables and ViewModels.
- **Audit Log Purging & Emergency Session Abort**:
  - Added Clear Audit Log button with confirmation modal and database wipe.
  - Added Emergency Session Halt action in TopAppBar and Home dashboard.
- **Comprehensive End-to-End Setup & Installation Guide**:
  - Detailed `SETUP_GUIDE.md` covering prerequisites, ADB installation, Cloudflare Relay self-hosting, MCP configuration, and troubleshooting.

### Changed
- Normalized IDE and agent names to clean single identifiers (`Antigravity`, `Cursor`, `Codex`, `Claude`) without verbose labels or icons.
- Standardized project branding strictly as **Handoff** across application manifests, notifications, top bars, and documentation.
- Updated unit test suites across all modules (`:mobile:feature:approval`, `:mobile:feature:pairing`, `:mobile:domain`) to 100% passing.

## [1.0.0] - 2026-09-03

### Added
- **Cloudflare Relay**: Edge-deployed WebSocket broker with Durable Objects (`RelayRoom`) supporting persistent bidirectional communication between desktop daemon and mobile client.
- **Desktop Daemon & CLI**:
  - Interactive CLI supporting `--pair`, `--test-request`, and `--mcp` flags.
  - MCP stdio server implementation for Claude Code, Cursor, and Antigravity IDE integration.
  - Cryptographic keypair generation and secure request dispatch.
- **Android Client (Jetpack Compose & Material 3 Expressive)**:
  - Material 3 Expressive design system with dynamic theming and motion schemes.
  - Pairing screen supporting CameraX live QR scanning and fallback manual ID entry.
  - Interactive approval card displaying risk levels (Low, Medium, High, Critical), target resources, commands, and action buttons.
  - Compose Navigation 3 type-safe routes (`PairingRoute`, `ApprovalRoute`).
- **Offline-First Room Architecture**:
  - `handoff_db` local SQLite database as single source of truth.
  - `RelayRepositoryImpl` continuous WebSocket background synchronization updating Room DB on arrival.
  - Reactive `Flow<PermissionRequest?>` data streams observing Room DB for zero-latency UI reactivity.
- **Hardware Compatibility**:
  - Full compatibility with Android 15 and 16 KB page-size devices (verified on Google Pixel 9).
  - Robust WorkManager / Koin dependency injection configuration.

### Fixed
- Resolved duplicate `WorkManagerInitializer` provider collision on Android 15 startup.
- Upgraded test suites with Turbine, MockK, and Coroutines Test across all modules.
