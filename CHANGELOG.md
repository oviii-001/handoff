# Changelog

All notable changes to the HandOff project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
