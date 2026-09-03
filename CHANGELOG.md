# Changelog

All notable changes to the HandOff project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
