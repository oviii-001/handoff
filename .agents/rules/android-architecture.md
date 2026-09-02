---
trigger: glob
globs: **/*.kt, **/*.kts, **/res/**
---

# Android & Kotlin Multiplatform Architecture

> Instructions for mobile development spanning Android (Native), KMP, and Compose Multiplatform.

---

## 1. Toolchain & Multi-Module Architecture

### 1.1 Baseline Toolchain
- **Kotlin 2.2.x**, Compose Compiler bundled. Enable strong-skipping mode.
- **Gradle Version Catalogs (`libs.versions.toml`)** exclusively — never hardcode dependency versions inline.
- Use **Kotlin DSL (`build.gradle.kts`)** only. Target latest stable `compileSdk`.

### 1.2 MVVM + Clean Architecture Enforcement
Dependencies point strictly inward toward `:domain`:
- **Domain Layer (`:domain:*`):** Pure Kotlin (`commonMain`), zero Android/platform imports. Contains UseCases (single responsibility), pure domain models, and repository interfaces.
- **Data Layer (`:data:*`):** Repository implementations, local DB (Room), remote sources (Ktor/Retrofit), and DTO ↔ Domain mappers. Depends on `:domain`, never on `:feature:*`.
- **Presentation Layer (`:feature:*`):** MVVM vertical slices (`feature/checkout`, `feature/profile`). ViewModels expose `StateFlow<UiState>` and `Channel`/`SharedFlow` for one-off events. Composables render UI state. Exposes only public navigation entry points (`internal` everything else).
- **Core Layer (`:core:*`):** Feature-agnostic components: design system, network primitives, common utils.
- **App Layer (`:app`):** Composition root only: DI graph assembly and top-level navigation graph.
- **Non-Negotiable Rule:** `:feature:*` → `:domain:*` ← `:data:*`. Features never import other features; domain never imports data.

### 1.3 Navigation
- **Navigation 3 (Compose Navigation):** Type-safe routes via `@Serializable` sealed classes/objects, not string paths. Each feature module owns its route definitions and exposes a navigation builder function.

---

## 2. Multiplatform, UI & State

### 2.1 KMP / CMP Standards
- Default to **KMP for shared business logic** (domain, data, networking, persistence) in `commonMain`. Platform code remains behind `expect`/`actual` declarations.
- Keep `commonMain` free of Android `Context` — inject system capabilities via interfaces.
- Stack: **Ktor Client** (networking), **kotlinx.serialization** (JSON), **Room KMP / SQLDelight** (persistence), **Koin / kotlin-inject** (DI).
- Compose Multiplatform is for shared UI only when UX is intentionally identical. If iOS requires native look-and-feel, share only the ViewModel/state layer and render with SwiftUI.

### 2.2 Material 3 Expressive UI
- Use **Material 3 Expressive** (Android 16 / Compose M3 1.4+).
- **Motion:** Use physics-based `MotionScheme.expressive()` for key interactions; `MotionScheme.standard()` for lists/utilities. Respect system "reduce motion" tokens.
- **Color & Theming:** Support dynamic color on Android 12+ with static fallback palettes. Never hardcode hex colors or raw `sp` sizes; reference `MaterialTheme`.

### 2.3 Async & State Management
- **Coroutines + Flow** exclusively. Scope all coroutines to `viewModelScope` or `rememberCoroutineScope`. Never use `GlobalScope`.
- `StateFlow` for persistent UI state; `Channel`/`SharedFlow` for one-off events (snackbars, navigation).
- Local database is the single source of truth: network syncs write to DB; UI observes DB via `Flow`.

---

## 3. Production Reliability & Testing

### 3.1 Resilience & Performance
- **Typed Error Handling:** Use cases return sealed `Result<T>` or `Either<DomainError, T>`. Never let exceptions cross into the presentation layer as control flow.
- **Process Death:** Store transient user inputs in `SavedStateHandle`. Test restoration on configuration changes.
- **Performance Budget:** Baseline Profiles for startup and hot scroll paths. Verify recomposition counts using Layout Inspector metrics.

### 3.2 Testing Standards
- Unit tests: **JUnit5 + Kotlin Test + MockK + Turbine** (Flow assertions) + coroutines-test.
- Compose tests: Use Compose Test APIs (`createComposeRule`) asserting on semantics (`testTag`, accessibility labels), not screenshot pixels.
- Visual regressions: **Paparazzi** or **Roborazzi** in separate non-blocking CI jobs.

---

## 4. Strict Android Guardrails & System Constraints

- **No Hardcoded Strings (i18n):** NEVER hardcode user-facing text in Composables or ViewModels. Extract all text to `res/values/strings.xml` and reference via `stringResource(R.string.key)`.
- **Strict UI Data Models:** NEVER pass Domain entities, Room models, or DTOs directly into Composables. The ViewModel MUST map them into dedicated immutable UI models (e.g., `UserUiModel`).
- **Secrets Management:** NEVER commit API keys or endpoints. Read local secrets from `local.properties` and inject via Gradle `BuildConfig`.
- **ProGuard / R8:** If adding a reflection-based library or serialization logic, write corresponding keep rules in `proguard-rules.pro`. Assume `isMinifyEnabled = true`.
- **Background Work:** Never use raw threads or foreground services for deferrable tasks. Default to **WorkManager** with explicit device constraints (unmetered network, battery not low). Use **AlarmManager** strictly for exact-time execution.