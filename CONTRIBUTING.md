# Contributing to HandOff

Thank you for your interest in contributing to HandOff! We welcome contributions from engineers of all backgrounds.

## Development Standards
This project adheres to the engineering standards specified in `AGENT.md` and `.agents/rules/engineering-standards.md`:
- **Kotlin 2.2.x** with Kotlin Multiplatform & Compose Multiplatform.
- **Clean Architecture & MVVM**: Dependencies point strictly inward (`:feature:*` -> `:domain:*` <- `:data:*`).
- **Strict UI Models**: Never pass Domain entities, Room models, or DTOs directly into Composables.
- **No Hardcoded Strings (i18n)**: All user-facing text must reside in `res/values/strings.xml`.
- **Offline-First**: Local Room database is the source of truth; remote network is a synchronization mechanism.
- **Material 3 Expressive**: Physics-based motion schemes and semantic color tokens.

## Local Setup & Testing
Refer to the [**Setup & Installation Guide**](SETUP_GUIDE.md) for full instructions on configuring ADB, Cloudflare Relay, and MCP daemons.

Ensure all tests pass before submitting a pull request:
```bash
./gradlew test
```

## Git Workflow
- Branch naming: `feat/PROJ-123-description`, `fix/...`, `chore/...`
- **Conventional Commits**: Format commit messages as `feat:`, `fix:`, `refactor:`, `test:`, `chore:`.
