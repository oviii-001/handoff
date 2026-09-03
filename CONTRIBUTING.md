# Contributing to HandOff

Thank you for your interest in contributing to HandOff! We welcome contributions from engineers of all backgrounds.

## Development Standards
This project adheres to the engineering standards specified in `AGENT.md`:
- **Kotlin 2.2.x** with Kotlin Multiplatform & Compose Multiplatform.
- **Clean Architecture & MVVM**: Dependencies point strictly inward (`:feature:*` -> `:domain:*` <- `:data:*`).
- **Offline-First**: Local Room database is the source of truth; remote network is a synchronization mechanism.
- **Material 3 Expressive**: Physics-based motion schemes and semantic color tokens.

## Git Workflow
- Branch naming: `feat/PROJ-123-description`, `fix/...`, `chore/...`
- **Conventional Commits**: Format commit messages as `feat:`, `fix:`, `refactor:`, `test:`, `chore:`.

## Running Tests
Ensure all tests pass before submitting a pull request:
```bash
./gradlew test
```
