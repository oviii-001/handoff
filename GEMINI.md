# AGENT.md

> Instructions for AI coding agents (Claude Code, Cursor, Copilot, Codex, Gemini CLI, etc.) working in this repository.
> Human docs live in `README.md`. This file exists for the agent — it states conventions, constraints, and decisions that aren't obvious from reading the code, so don't duplicate generic tutorials or restate what the code already shows.
>
> **Engineering bar for this repository:** production-grade, senior-level code. Favor correctness, explicit trade-offs, and long-term maintainability over the fastest thing that compiles — including under time pressure. Every change should look like something a senior engineer would sign off on in review, not a first draft.

---

## 0. How to Use This File

- Treat this as a senior engineer's onboarding brief, not a style guide to quote back.
- If a rule here conflicts with something you infer from the codebase, **the codebase wins for local convention, this file wins for architecture/process decisions**. Flag the conflict instead of silently picking one.
- Never invent scope. If a task is ambiguous, state your assumption in the PR description and proceed — don't block on it unless it changes the architecture.
- Scope of this repo: **Android app (Kotlin Multiplatform + Compose Multiplatform)** and **web platform (React + Node.js)** sharing a common domain model and backend contract where practical.

---

## 1. Engineering Operating Model (SDLC + Agile)

### 1.1 Process

- **Scrum-based, 2-week sprints.** Backlog lives in the tracker (Jira/Linear) — not in this repo. Do not create ad-hoc TODO trackers in markdown; link the ticket ID in commits/PRs instead (`[PROJ-123] ...`).
- Every feature starts from a **user story** with explicit acceptance criteria in Gherkin-ish form (`Given / When / Then`). If a task lacks acceptance criteria, write them yourself before implementing and put them in the PR description.
- **Definition of Ready (DoR)** before work starts: acceptance criteria defined, design/API contract available, dependencies identified, testable.
- **Definition of Done (DoD)** before work is merged:
  1. Code compiles with zero warnings treated as errors in CI.
  2. Unit tests written and passing (see §2 coverage targets).
  3. UI/integration tests updated for changed behavior.
  4. Accessibility pass (TalkBack/VoiceOver for mobile, axe/Lighthouse for web) for user-facing changes.
  5. No new lint/static-analysis violations (detekt/ktlint for Kotlin, ESLint/TypeScript for web).
  6. Docs/changelog updated if public API or user-facing behavior changed.
  7. PR reviewed and approved by at least one other engineer (or explicitly flagged for human review if you're the agent).
- **Trunk-based development** preferred over long-lived feature branches. Feature flags gate incomplete work in `main`, not long-lived branches, once a branch would live longer than ~3 days.

### 1.2 Branching & Commits

- Branch naming: `feat/PROJ-123-short-description`, `fix/...`, `chore/...`, `refactor/...`.
- **Conventional Commits** required: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`, `perf:`, `build:`, `ci:`. Breaking changes: `feat!:` + `BREAKING CHANGE:` footer.
- One logical change per commit. Don't bundle formatting-only diffs with behavior changes.
- PRs should be small enough to review in under 30 minutes. If a task is naturally larger, split into stacked PRs behind a feature flag.

### 1.3 Agile Testing Strategy

Follow the **Agile Testing Quadrants** (Q1–Q4, Lisa Crispin / Janet Gregory) — tests support the team, not just guard the code:

| Quadrant | Purpose | Examples in this repo |
| --- | --- | --- |
| Q1 — Technology-facing, supports team | Fast feedback for devs | Unit tests, component tests |
| Q2 — Business-facing, supports team | Confirms behavior matches intent | Acceptance tests (Gherkin/BDD), API contract tests |
| Q3 — Business-facing, critiques product | Exploratory validation | Manual exploratory sessions, usability testing |
| Q4 — Technology-facing, critiques product | Non-functional quality | Performance, security, load, accessibility testing |

**Test pyramid ratio (target):** ~70% unit, ~20% integration/component, ~10% end-to-end. E2E tests are expensive and flaky — don't compensate for missing unit tests by adding more E2E.

**Coverage targets (guideline, not a gate to game):** domain/business logic ≥ 85%, ViewModels/presenters ≥ 80%, UI composables/components ≥ 50% (behavior-focused, not snapshot-only), overall project ≥ 75%. Never write tests that assert implementation details just to inflate coverage.

**Shift-left:** write the failing test before or alongside the implementation for bug fixes (regression test first, reproduces the bug, then fix). For new features, write acceptance-criteria-level tests before diving into implementation details.

### 1.4 Code Quality, Readability & Comments

- Optimize for the next reader (human or agent), not for cleverness. If a shorter/more "elegant" version is harder to follow, prefer the clearer one.
- **Naming carries most of the documentation load.** Functions, variables, and types should say what they do/hold without needing a comment to translate them. `calculateDiscountedPrice(basePrice, promoCode)` needs no comment; `calc(p, c)` needs one that shouldn't have to exist.
- **Comment the "why," not the "what."** Don't narrate code line-by-line (`// increment i` above `i++`). Do explain: non-obvious business rules, why a workaround exists (link the ticket/issue), why an obvious-looking alternative was rejected, and any assumption the next person could easily violate without knowing.
- Every public API — Kotlin `public` classes/functions crossing module boundaries, exported TS functions/types, REST/GraphQL endpoints — gets a doc comment (KDoc/TSDoc/OpenAPI) stating purpose, parameters, return value, and thrown/rejected error cases. Internal, self-evident implementation details do not need prose padding.
- Keep functions small and single-purpose (roughly: fits on one screen, one level of abstraction, one reason to change). If you need a comment to separate "sections" inside a function, that's usually a signal to extract a named function instead.
- No dead code, no commented-out blocks left "just in case," no leftover debug logging — delete it or it doesn't ship. Version control is the history, not inline comments.
- Consistent formatting is non-negotiable and non-manual: rely on the enforced formatter/linter (ktlint/detekt, Prettier/ESLint) rather than hand-formatting — don't fight the formatter in review.
- When you fix a bug, add a short comment at the fix site only if the root cause is non-obvious from the diff itself (e.g., a platform quirk, a race condition, an off-by-one tied to a specific API contract).

---

## 2. Android — Kotlin, Jetpack Compose, KMP & CMP

### 2.1 Baseline Toolchain

- **Kotlin 2.2.x**, Compose Compiler (bundled with Kotlin — no separate compiler artifact needed since Kotlin 2.0+). Enable the compiler's strong-skipping mode; trust stability inference over hand-annotating `@Stable`/`@Immutable` unless the compiler report shows a real gap.
- **Gradle with Version Catalogs (`libs.versions.toml`)** — never hardcode dependency versions inline in module `build.gradle.kts`.
- Target latest stable **compileSdk**; `minSdk` set per product decision (document it in `README.md`, not here).
- Use **Kotlin DSL** (`build.gradle.kts`) exclusively — no Groovy build scripts in new modules.

### 2.2 Architecture Pattern: MVVM + Clean Architecture, Feature-Based & Multi-Module

This project combines three complementary patterns — they're not alternatives, they operate at different levels:

- **MVVM (Model–View–ViewModel)** governs the *presentation layer*: `View` is the Composable (stateless, renders `UiState`), `ViewModel` holds and transforms state and exposes it as `StateFlow<UiState>` plus one-off events via `SharedFlow`/`Channel`, `Model` is whatever the domain/data layer hands back. The View never talks to a repository or use case directly — only through its ViewModel. Never put business logic inside a Composable or inside the ViewModel itself beyond orchestration — that belongs in the domain layer.
- **Clean Architecture** governs the *dependency direction* across the whole app, independent of MVVM: dependencies point inward, toward `domain`. `domain` knows nothing about `data` or `presentation`; `data` implements interfaces `domain` defines; `presentation` (MVVM) depends on `domain`, never the other way around. This is what keeps business rules testable without Android, a database, or a network in the test.
  - **Domain layer** — use cases/interactors (one class, one responsibility, e.g. `GetUserProfileUseCase`), domain models (plain Kotlin, no annotations tying them to Room/network), and repository *interfaces*. Pure Kotlin/KMP `commonMain`, zero Android or platform imports.
  - **Data layer** — repository *implementations*, remote data sources (Ktor/Retrofit), local data sources (Room/DataStore), and DTO ↔ domain mappers. Owns all serialization/persistence annotations; domain models stay clean.
  - **Presentation layer** — ViewModels (MVVM) + Composables per feature, consuming only `domain` use cases/interfaces, never `data`-layer types directly.
- **Feature-based organization** inside `presentation`/`feature` modules: group by *what the user does* (`feature/checkout`, `feature/profile`, `feature/onboarding`), not by *technical type* (no top-level `viewmodels/`, `screens/`, `composables/` folders spanning unrelated features). Each feature folder/module is a vertical slice: its own screens, ViewModel(s), feature-local state, and navigation entry point.
- **Multi-module architecture** is how the above is *enforced*, not just organized — Gradle module boundaries make illegal dependencies a build failure, not a code-review nitpick:
  - `:core:*` — cross-cutting, feature-agnostic: design system/theme, networking primitives, common utilities, shared UI components. No feature-specific logic.
  - `:domain:*` — one module per bounded context (or a single `:domain` module for smaller apps) — pure Kotlin, use cases, domain models, repository interfaces.
  - `:data:*` — repository implementations, remote/local data sources, mappers. Depends on `:domain`, never on `:feature:*`.
  - `:feature:*` — one module per feature (`:feature:checkout`, `:feature:profile`), MVVM presentation layer for that feature. Depends on `:domain` and `:core:*`. Exposes only its public navigation entry point (`internal` everything else) — features must never depend on each other directly; shared needs get pulled down into `:domain` or `:core`.
  - `:app` — composition root only: DI graph assembly, top-level navigation graph wiring feature entry points together, application-level config. No business or presentation logic lives here.
  - **Dependency rule, non-negotiable:** `:feature:*` → `:domain:*` ← `:data:*`, and `:app` → everything. A dependency arrow pointing the other way (e.g., `:domain` importing from `:data`, or one `:feature` importing another `:feature`) is an architecture violation, not a style preference — fix the boundary, don't add an exception.
  - Start with coarser modules (`:core`, `:domain`, `:data`, `:feature:*` grouped sensibly) and split further only when build times or team ownership actually demand it — see §2.6 on right-sizing modularization.
- Composables stay stateless where possible (state hoisted to the ViewModel per MVVM above); `remember { mutableStateOf(...) }` is fine for transient, composition-local UI state (e.g., an expanded/collapsed toggle) but never for anything that should survive recomposition scope or represents actual app state — that's a ViewModel's job.
- Navigation: **Navigation 3 (Compose Navigation with type-safe routes)** — routes as `@Serializable` sealed classes/objects, not string paths. Each feature module owns its own route definitions and exposes a navigation graph builder function; `:app` composes them together.

### 2.3 Kotlin Multiplatform (KMP) / Compose Multiplatform (CMP)

- Default to **KMP for shared business logic** (domain layer, data layer, networking, persistence, DTOs/serialization) across Android, iOS, and Desktop/Web where a product decision calls for multiplatform reach. Platform-specific code stays behind `expect`/`actual` declarations, isolated and minimal.
- **Compose Multiplatform for shared UI** only when the UX is intentionally identical across platforms. If iOS needs native look-and-feel (SwiftUI idioms), keep UI native per-platform and share only the presentation/state layer (shared `ViewModel`/state holder consumed by both Compose and SwiftUI) — do not force pixel-identical UI where platform conventions diverge.
- Recommended shared-layer stack: **Ktor Client** (networking), **kotlinx.serialization** (JSON), **SQLDelight or Room KMP** (persistence — Room now supports KMP; prefer it if the team is Room-native, SQLDelight if type-safe SQL is prioritized), **Koin** (DI — lighter and more KMP-idiomatic than Hilt, which is Android-only) or **Kotlin-Inject** for compile-time DI across platforms.
- Gradle module shape for multiplatform targets: `commonMain` for shared logic, `androidMain`/`iosMain`/`desktopMain` for `actual` implementations. Never leak `androidMain`-only APIs into `commonMain` interfaces.
- Keep `commonMain` free of any Android `Context` dependency — inject platform capabilities (file system, secure storage, permissions) via interfaces.

### 2.4 UI: Material 3 Expressive

- Use **Material 3 Expressive** (the Material You evolution shipped with Android 16 / Compose Material3 1.4+) as the default design system, not legacy Material 2.
- **Motion:** use the physics-based `MotionScheme` (spring-based, not duration/easing-based). Default to `MotionScheme.expressive()` for hero moments and key interactions; use `MotionScheme.standard()` for utilitarian/high-frequency UI (lists, settings). Don't hand-roll `tween()`/`easing` animations where a motion-scheme token already exists — it keeps motion consistent and respects the system "reduce motion" accessibility setting automatically.
- **Shape:** use the expanded M3 shape library and built-in shape morphing for state changes (selection, loading, FAB expansion) rather than custom `Path` drawing, unless the shape truly isn't in the library.
- **Typography:** use the dual baseline/emphasized type scale — emphasized styles for hero text and key CTAs, baseline for body content. Don't default everything to `bodyMedium`.
- **Color:** dynamic color (`ColorScheme.fromSeed` / `dynamicColorScheme`) on Android 12+, with a well-defined static fallback palette for older versions and for CMP targets without dynamic color support (web has no M3 Expressive implementation as of 2026 — don't assume parity there).
- Components: prefer the new expressive component variants (expanded FAB menu, expressive slider, split button, loading indicators with shape morphing) where they improve the actual interaction, not decoratively. Target the "Excellent" or "Transformative" expression tier (per Material's own rubric) rather than the minimum "Foundational" migration.
- Respect `LocalContentColor`/`LocalTextStyle` theming — never hardcode hex colors or sp values in composables; pull from `MaterialTheme.colorScheme` / `MaterialTheme.typography` / `MaterialTheme.shapes`.

### 2.5 Async, State & Data

- **Coroutines + Flow** exclusively for async work — no callbacks, no RxJava in new code.
- `StateFlow` for UI state, `SharedFlow`/`Channel` for one-off events (navigation, snackbars) — never model one-off events as `StateFlow` (causes re-delivery on config change/recomposition).
- Structured concurrency: scope work to `viewModelScope`/`rememberCoroutineScope`; never launch un-scoped `GlobalScope` coroutines.
- Persistence: **Room** (or Room KMP for multiplatform) for structured local data, **DataStore** (Proto or Preferences) for key-value/settings — never raw `SharedPreferences` in new code.
- Networking: **Ktor Client** (KMP-shared) or **Retrofit** (Android-only modules) + **OkHttp** with structured error handling — map transport errors to a domain-level sealed `Result`/`Either` type; don't leak `HttpException`/`IOException` past the data layer.
- Dependency injection: **Hilt** for Android-only modules, **Koin** for shared KMP modules. Don't mix DI frameworks within the same module.

### 2.6 Production-Grade Android Architecture — Reliability, Scalability & Observability

This is what separates a demo app from a production one. Apply these by default, not just when something breaks:

- **Offline-first, not offline-tolerant.** The local database (Room/SQLDelight) is the source of truth the UI reads from; the network is a sync mechanism, not a dependency the UI blocks on. Pattern: network → write to local DB → UI observes DB via `Flow`. Don't render directly from a network response and treat local caching as an afterthought.
- **Explicit, typed error handling.** Every use case/repository function that can fail returns a sealed result type (`Result<T>` / a domain `Either<DomainError, T>`) — never let exceptions cross the domain boundary as control flow. Distinguish recoverable errors (no network, validation failure — show inline UI) from unrecoverable ones (crash-worthy bugs — let them crash in debug, report via Crashlytics/Sentry in release).
- **Resilience on the network layer:** timeouts on every request, retry with exponential backoff + jitter for idempotent calls only, circuit-breaking/backing off on repeated failures instead of hammering a degraded backend, and a documented behavior for stale-cache-vs-error when a refresh fails.
- **Graceful degradation over blank screens.** Every screen has an explicit `Loading` / `Content` / `Empty` / `Error` state in its `UiState` — a composable should never have an implicit "nothing happened yet" state that just renders blank.
- **Observability:** structured, leveled logging (Timber or equivalent) with no PII in logs; crash reporting (Crashlytics) and non-fatal error reporting wired from day one, not bolted on before release; key user journeys instrumented with analytics events tied to acceptance criteria, not ad hoc.
- **Performance budget, enforced, not aspirational:** Baseline Profiles generated and shipped for cold-start-critical paths; Macrobenchmark tests for startup and critical scroll/animation paths in CI; avoid unnecessary recomposition (verify via Layout Inspector / compose compiler metrics, not guesswork) on hot lists before shipping.
- **Config/secrets:** API keys and environment config injected via build variants / `BuildConfig` fields sourced from CI secrets — never hardcoded, never committed, even for "internal" builds.
- **Process death and configuration change are first-class cases, not edge cases** — `SavedStateHandle`-backed state for anything the user would be upset to lose; test rotation and process-death restoration for any screen holding in-progress user input.
- **Feature flags / remote config** (Firebase Remote Config or equivalent) gate risky or partially-rolled-out features — this is how trunk-based development in §1.1 stays safe.
- **Modularization scales with team size, not novelty.** Don't split into 40 micro-modules on a 2-person team "for architecture's sake" — module boundaries should track real ownership/build-time boundaries (see §2.2). Re-evaluate module graph when build times or team structure change, not preemptively.

### 2.7 Testing (Android/KMP)

- Unit tests: **JUnit5 + Kotlin Test + MockK + Turbine** (for Flow assertions) + Kotlinx-coroutines-test.
- Compose UI tests: **Compose Test APIs** (`createComposeRule`), assert on semantics (`testTag`, content descriptions), not pixel snapshots, for behavior tests. Use **Paparazzi** or **Roborazzi** for visual regression snapshots as a separate, non-blocking-by-default CI job.
- Instrumented/E2E: **Espresso**/Compose UI Test on real devices or emulators for critical user journeys only (login, checkout, core flow) — keep this suite small and fast.
- KMP shared logic: tests in `commonTest`, run against all targets in CI, not just JVM.
- Every bug fix ships with a regression test that fails on `main` before the fix and passes after.

---

## 3. Full-Stack Web — React + Node.js

### 3.1 Frontend

- **Next.js (App Router)** as the default React framework for anything needing SSR/SEO/full-stack routing. Use plain **Vite + React Router** only for pure SPA internal tools with no SEO requirement.
- **TypeScript strict mode** everywhere — `any` requires a `// eslint-disable` comment with justification.
- **Server Components by default**; opt into `"use client"` only where interactivity/state/browser APIs are genuinely needed. Don't blanket-convert whole trees to client components for convenience.
- State management: local component state / `useState` first; **React Query (TanStack Query)** for all server-state/caching (never hand-roll fetch+`useEffect` caching); **Zustand** for lightweight shared client state; avoid introducing Redux unless the app already has heavy cross-cutting client state that justifies it.
- Styling: **Tailwind CSS** + a component primitive layer (**shadcn/ui** / Radix primitives) for accessible, unstyled-by-default components you fully own — don't pull in a heavy opinionated component library unless the product explicitly wants that visual identity.
- Forms/validation: **React Hook Form + Zod**, with the same Zod schema reused for both client validation and server-side input validation (single source of truth for the shape).
- Accessibility: semantic HTML first, ARIA only to fill gaps; every interactive element keyboard-operable; run axe checks in CI for changed pages.

### 3.2 Backend (Node.js)

- Default backend framework: **NestJS** for the primary service (structured DI, modules, guards, interceptors — scales with team size and matches the layered architecture used on Android). Use **Fastify** directly (or Fastify-adapter under NestJS) where raw throughput on a specific hot-path route matters. Use **Hono** only for edge/serverless functions (Cloudflare Workers, Vercel Edge) that need small cold starts — not as the primary API framework.
- **TypeScript strict** on the backend too — the API contract (request/response DTOs) should be the same Zod/TypeBox schemas validated at the boundary, ideally shared as a package/workspace between frontend and backend in a monorepo (Turborepo/Nx) so types never drift.
- ORM: **Prisma** for teams that want DX and migrations out of the box on traditional Postgres; **Drizzle** where SQL-level control, edge/serverless compatibility (HTTP-based drivers, e.g. Neon serverless driver), or minimal runtime overhead matters. Pick one per service — don't mix ORMs in the same service.
- Auth: short-lived JWT **access tokens** + revocable, DB-backed **refresh tokens**; never store secrets or long-lived tokens in `localStorage` — use `httpOnly`, `secure`, `sameSite` cookies for session tokens in browser contexts.
- Validation at every boundary (`class-validator`/Zod on inbound DTOs); never trust client input, including from the mobile app.
- Background jobs/queues: **BullMQ** (Redis-backed) for async work — don't do long-running work inline in the request/response cycle.
- Structured logging (pino/winston) with correlation/request IDs; never `console.log` in production code paths.

### 3.3 Architecture Pattern: Layered/Clean Backend, Feature-Based Frontend & Modular Monorepo

Same philosophy as Android (§2.2), adapted to the web stack:

- **Backend — modular monolith with a layered (Clean-Architecture-flavored) internal structure:** `Controller` (NestJS controller / route handler) → `Service`/use case (business logic) → `Repository` (data access via Prisma/Drizzle). Dependencies point inward: services never import controller types; repositories expose interfaces services depend on, not concrete DB clients, so services stay testable with an in-memory/fake repository. NestJS **modules** are the enforcement mechanism — one module per bounded domain (`UsersModule`, `OrdersModule`, `PaymentsModule`), each owning its own controller/service/repository trio internally. Each module exposes a **single designated public-service interface** in its `exports` (e.g., `OrdersPublicService`) — everything else (entities, internal use cases, repositories) stays unexported. Another module may only depend on that public interface, never reach past it into internals; NestJS raises a DI error if you try, which is the mechanism doing the enforcing, not code review. This is the same shape as one Android `:feature` module reaching another only through its declared public API (§2.2) — and it's the standard on-ramp to microservices later, since a cleanly-bounded module can be extracted without a rewrite.
- **Frontend — feature-based, not type-based.** Mirrors §2.2's feature-module rule: organize by domain (`features/checkout/`, `features/profile/`) each containing its own components, hooks, API/query layer, and types, rather than global `components/`, `hooks/`, `types/` folders that mix unrelated features. Shared, feature-agnostic UI (buttons, layout primitives, the design system) lives in a `shared/`/`ui/` layer — the same role `:core` plays on Android. A feature folder may depend on `shared/`; feature folders never import from each other directly — cross-feature needs get promoted to `shared/` or handled via routing/composition at the page level.
- **MVVM's web equivalent — custom hooks as the ViewModel.** A component stays presentational (props in, JSX out); a custom hook (e.g., `useCheckoutForm()`) owns state, calls the service/query layer, and exposes state + handlers back to the component — same separation MVVM gives Android, without a wrapper component. This has superseded the older class-based container/presentational split (container component wraps a dumb component); don't introduce that older pattern in new code, hooks are the current idiom. Keep the split even inside a single feature folder: don't let a 300-line component both fetch data, hold five pieces of state, and render markup — extract the hook.
- **Modular monorepo (Turborepo/Nx)** is what makes multi-module discipline enforceable across frontend and backend simultaneously, mirroring Gradle's role on Android: `apps/web` (Next.js), `apps/api` (NestJS), `packages/shared-types` (Zod schemas / DTOs shared by both), `packages/ui` (shared design-system components), `packages/config` (shared ESLint/TS config). `apps/*` depend on `packages/*`; `packages/*` never depend on `apps/*`; `apps/web` and `apps/api` never import each other's internals directly — they communicate only over the HTTP contract defined in `packages/shared-types`.
- **Dependency rule, non-negotiable:** same as Android — an arrow pointing the wrong way (a repository importing a controller type, a `shared/` component importing from a specific feature, one module reaching past another's public service into its internals, `packages/shared-types` importing from `apps/api`) is a boundary bug, fixed at the boundary, not special-cased.

### 3.4 Production-Grade Web Architecture — Reliability, Scalability & Observability

- **Layered backend, not a fat controller.** Controller/route → service (business logic) → repository (data access) — each layer testable in isolation. Controllers stay thin: input validation + delegation + response shaping only; business rules never live in a route handler.
- **Idempotency on mutating endpoints** that can be safely retried (payments, order creation) via an idempotency key — clients and mobile apps *will* retry on timeout, design for it rather than treating double-submission as a rare edge case.
- **Explicit, typed error responses.** A consistent error shape (code, message, correlation ID) across the whole API — never leak stack traces or raw DB errors to a client response. Distinguish 4xx (client's fault, actionable) from 5xx (ours, gets paged) at the framework level (NestJS exception filters / Fastify error handlers), not ad hoc per-route try/catch.
- **Resilience:** timeouts and retry-with-backoff on all outbound calls (third-party APIs, inter-service calls); circuit breakers around flaky dependencies; graceful degradation (serve stale cache / reduced functionality) over a hard failure when a non-critical dependency is down.
- **Caching strategy is explicit, not incidental:** Redis for hot read paths and session data, HTTP caching headers (`ETag`/`Cache-Control`) for cacheable GETs, and a stated invalidation strategy per cache — "cache it" without an invalidation plan is a production incident waiting to happen.
- **Observability as a first-class concern:** structured JSON logs with correlation/request IDs propagated end-to-end (mobile → BFF → backend), metrics (RED: rate/errors/duration) exported per endpoint, distributed tracing (OpenTelemetry) across service boundaries, and alerting tied to SLOs — not just "check the logs when someone complains."
- **Rate limiting and abuse protection** at the edge (API gateway/reverse proxy) and per-user/per-IP at the application layer for public-facing endpoints — never assume the frontend is the only client of the API (the mobile app, scripts, and abuse all hit the same backend).
- **Database migrations are versioned, reversible, and reviewed** (Prisma Migrate / Drizzle Kit) — never hand-edit schema in production, never ship a migration without a rollback plan for anything touching a table with live traffic.
- **Horizontal scalability by default:** services are stateless (session state in Redis/DB, not in-process memory) so any instance can serve any request — this is what makes autoscaling and zero-downtime deploys possible.
- **Frontend resilience:** error boundaries per route/major section so one broken widget doesn't blank the whole page; a thin, explicit data-fetching layer (React Query hooks per resource, see §3.3) so components never call `fetch` directly and loading/error states are handled consistently everywhere.
- **Environment parity:** local/staging/production are the same architecture (same DB engine, same auth flow) with different scale/data — "works on my machine but not staging" is treated as a bug in the setup, not the code.

### 3.5 Testing (Web)

- Unit/component: **Vitest + React Testing Library** — test behavior/output, not implementation details (avoid testing internal state or relying on class names).
- API/integration: **Vitest/Jest + Supertest** (or NestJS's built-in testing module) against a real test database (Testcontainers) — not fully mocked DB layers for integration-level tests.
- E2E: **Playwright** for critical user journeys across real browsers; run against a deployed preview environment in CI, not just localhost.
- Contract tests between frontend and backend where they're deployed independently (Pact or shared OpenAPI/Zod schema validation) to catch drift early.

---

## 4. Cross-Cutting Standards

### 4.1 Security

- Never commit secrets. Use `.env` (git-ignored) locally and a secrets manager (Doppler/Vault/cloud provider secrets) in CI/CD.
- Dependency scanning (`npm audit`/`Dependabot`/`Snyk` for web, Gradle dependency-check for Android) runs in CI; treat high/critical vulnerabilities as release blockers.
- All user input validated and sanitized at the boundary on both mobile and web/backend — never rely on client-side validation alone.
- Passwords: `argon2`/`bcrypt`, never reversible encryption, never plaintext.

### 4.2 CI/CD

- Every PR triggers: build, lint/static analysis, unit tests, and a fast integration test subset. Full E2E and slower suites run on merge to `main` and on a nightly schedule.
- Android: assemble + run unit tests + detekt/ktlint on every PR; instrumented tests on merge (Firebase Test Lab or equivalent device matrix).
- Web: typecheck + lint + unit tests on every PR; Playwright E2E against a preview deployment (Vercel/Netlify preview or equivalent) on every PR for user-facing changes.
- Releases follow **Semantic Versioning**. Changelogs generated from Conventional Commits.

### 4.3 Documentation Expectations

- Public APIs (exported Kotlin `public` functions/classes crossing module boundaries, exported REST/GraphQL endpoints) require doc comments (KDoc/TSDoc/OpenAPI spec) — internal implementation details do not need prose explanation, the code should read clearly on its own.
- Architecture Decision Records (ADRs) for decisions with long-term consequences (choosing Room vs SQLDelight, NestJS vs Fastify, monorepo tooling) go in `/docs/adr/`, not in this file — this file states the current decision, ADRs explain why.

### 4.4 Repository Management & Auto-Documentation

- Agent Ownership: The AI agent is strictly responsible for drafting, updating, and maintaining all repository health files, including `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, and `.github/` templates. Do not expect the human developer to write these manually.
- Proactive Changelog Updates: When completing a major feature, fixing a critical bug, or merging a PR, the agent MUST proactively offer to update the `CHANGELOG.md` based on the recent Git commit history.
- Source of Truth: When generating setup instructions for the `README.md` or workflow rules for `CONTRIBUTING.md`, the agent must scan the active codebase and use this `AGENT.md` file as the absolute source of truth.
- No Hallucinated Credentials: When scaffolding files that require personal information or contact details (e.g., vulnerability reporting emails in `SECURITY.md`), NEVER invent or hallucinate data. Generate explicit placeholders (e.g., `[INSERT CONTACT EMAIL HERE]`) and halt to ask the human to provide them.

---

## 5. Agent-Specific Guardrails

- **Do not** introduce a new major dependency (DI framework, state library, ORM) without flagging it explicitly in the PR description — these are architectural decisions, not implementation details.
- **Do not** duplicate shared domain logic between the Android/KMP `commonMain` and the Node.js backend — if the same business rule must exist in both (e.g., pricing calculation, validation rule), say so explicitly; it's a sign the rule might belong behind a shared API instead.
- **Do not** write tests that mock so much of the system that they only assert the mocks were called — prefer testing real collaborators where feasible (in-memory DB, real reducers/use cases).
- **Never** disable a failing test to make CI green — fix it or explicitly mark it `@Ignore("PROJ-123: flaky, tracked")` with a linked ticket.
- When unsure whether something is a platform-specific concern (Android) vs. shared concern (KMP `commonMain`) vs. a web-only concern, default to the **narrowest scope** and let a human widen it in review — it's cheaper to promote code to `commonMain` later than to unwind a bad abstraction.
- Treat this file as living documentation: if you make an architectural decision not covered here, propose an addition to this file in the same PR rather than leaving the decision undocumented.
- No Hardcoded Strings (i18n by default): **NEVER** hardcode user-facing text in Composables, ViewModels, or Kotlin classes. Every piece of UI text MUST be extracted to res/values/strings.xml and referenced via stringResource(R.string.key). If you create a new screen or feature, generating the XML string definitions is part of the task.
- Strict UI Data Models: **NEVER** pass Domain models, Data entities (Room), or DTOs (Network responses) directly to a Composable. The ViewModel MUST map Domain models into immutable, UI-specific data classes (e.g., `UserUiModel`, `CheckoutState`) before exposing them via UiState. Composables should only know about UI models.
- Secrets Management (local.properties): **NEVER** hardcode API keys, auth tokens, or environment-specific base URLs in the codebase. Read local development secrets from local.properties and expose them to the app via Gradle `BuildConfig` or manifest placeholders. Assume `local.properties` is `.gitignored`.
- ProGuard / R8 Rules: If you introduce a new third-party dependency, use reflection, or implement custom serialization logic (e.g., `kotlinx.serialization` or `Gson`), you **MUST** write the corresponding keep rules in `proguard-rules.pro`. Always assume `isMinifyEnabled = true` is strictly enforced for release builds.
- Search Before Writing: Before writing any utility function, UI component, or extension function (e.g., date formatting, currency parsing, custom buttons, API error handlers), you **MUST** search the codebase to see if it already exists.
- Target Shared Modules: Actively check `:core:utils`, `:core:designsystem`, `packages/ui`, and `shared/` directories. If a suitable function or component exists, import it. **NEVER** duplicate it.
- Refactor Over Copy-Paste: If an existing shared function is almost what you need, do not copy-paste and modify it. Instead, update the existing function to safely handle the new use case (e.g., by adding a default parameter) while preserving its current behavior for existing callers.
- Extract New Shared Logic: If you are forced to write a genuinely new, highly reusable utility or UI primitive, do not bury it inside a specific `:feature` module. Place it in the appropriate `:core` or `shared` module immediately.

---

## 6. Mobile Performance & System Constraints

- Background Processing: For Android, never use raw threads or foreground services for deferrable work. Default to `WorkManager` for guaranteed, asynchronous tasks. Use `AlarmManager` only for exact-time scheduling where the OS strictly requires it.
- Code Obfuscation: Any new dependency or reflection-heavy code must be accompanied by corresponding `ProGuard/R8` rules. The agent must assume aggressive shrinking is enabled for release builds and write code accordingly.
- Resource Management: Background network syncs must explicitly check device state constraints (e.g., unmetered network, battery not low) before executing to prevent unnecessary battery and data drain.

---

## 7. BaaS & Backend Security

- Zero-Trust Client Models: If a feature interacts directly with backend-as-a-service platforms like `Firebase`, the agent **MUST** write or update the corresponding `Firestore/Storage` `.rules` file. Never ship client code that relies on overly permissive database rules.
- Authentication & Roles: Role-based access control should utilize secure tokens or custom claims. These must be validated strictly on backend routes (NestJS/Next.js) and at the database level, rather than just hiding UI elements on the frontend.

---

## 8. Observability & Localization

- PII Masking: Never log Personally Identifiable Information (names, emails, geolocations). The agent must actively mask or hash this data in any logging statements (Timber/Pino) or crash reporting breadcrumbs.
- Actionable Breadcrumbs: Complex state transformations within ViewModels or custom React hooks must log lightweight breadcrumbs. If a crash occurs in production, the exact sequence of state transitions must be visible in the crash report.
- No Hardcoded Strings: Every user-facing string must be extracted to `strings.xml` or the designated i18n JSON dictionary. Hardcoding text directly into a Composable or JSX component is an automatic failure of the Definition of Done.
