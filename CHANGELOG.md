# Changelog

All notable changes to the HandOff project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
