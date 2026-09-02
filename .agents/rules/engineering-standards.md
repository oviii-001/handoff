---
trigger: always_on
---

# Engineering Standards & Agent Guardrails

> Universal instructions for AI coding agents working in this repository.
> States conventions, constraints, and process decisions that aren't obvious from reading code alone.
>
> **Engineering bar:** production-grade, senior-level code. Favor correctness, explicit trade-offs, and long-term maintainability over the fastest thing that compiles — including under time pressure.

---

## 0. How to Use Rules

- Treat this as a senior engineer's onboarding brief, not a style guide to quote back.
- If a rule conflicts with codebase convention, **codebase wins for local syntax; this file wins for architecture/process decisions**. Flag the conflict instead of silently guessing.
- Never invent scope. If a task is ambiguous, state your assumption in the PR description and proceed.
- Cross-reference modular rules using `@android-architecture.md` and `@web-architecture.md`.

---

## 1. Engineering Operating Model (SDLC + Agile)

### 1.1 Process & Definition of Done
- **Scrum-based, 2-week sprints.** Backlog lives in the tracker (Jira/Linear) — not in this repo. Do not create ad-hoc TODO trackers in markdown; link ticket IDs in commits/PRs (`[PROJ-123] ...`).
- Every feature starts from a **user story** with explicit acceptance criteria (`Given / When / Then`). If missing, write them yourself in the PR description before implementing.
- **Definition of Ready (DoR):** Acceptance criteria defined, API contract available, dependencies identified, testable.
- **Definition of Done (DoD):**
  1. Code compiles with zero warnings treated as errors in CI.
  2. Unit tests written and passing (domain ≥ 85%, presentation ≥ 80%, overall ≥ 75%).
  3. UI/integration tests updated for changed behavior.
  4. Accessibility pass (TalkBack/VoiceOver, axe/Lighthouse) completed.
  5. Zero new lint/static-analysis violations (detekt/ktlint, ESLint/Prettier).
  6. Docs/changelog updated if public APIs or UI behavior changed.
  7. PR reviewed and approved by an engineer (or flagged for human review).
- **Trunk-based development:** Gate incomplete work with feature flags in `main`. Avoid branches living longer than ~3 days.

### 1.2 Branching, Commits & PRs
- **Branch naming:** `feat/PROJ-123-short-description`, `fix/...`, `chore/...`, `refactor/...`.
- **Conventional Commits required:** `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`, `perf:`, `build:`, `ci:`. Breaking changes require `feat!:` and `BREAKING CHANGE:` footer.
- One logical change per commit. Never bundle formatting-only diffs with behavior changes.
- PRs should be reviewable under 30 minutes; split larger tasks into stacked PRs behind feature flags.

### 1.3 Code Quality & Readability
- Optimize for the next reader, not cleverness. Prefer clear code over shorter, harder-to-follow code.
- **Naming carries documentation:** `calculateDiscountedPrice(basePrice, promoCode)` needs no comment; `calc(p, c)` is unacceptable.
- **Comment the "why," not the "what."** Document non-obvious business rules, platform quirks, and workarounds linking tickets. Never narrate code line-by-line.
- Public APIs crossing module boundaries require doc comments (KDoc/TSDoc/OpenAPI).
- No dead code, no commented-out blocks, no leftover debug logging. Version control is history.

---

## 2. Cross-Cutting Standards

### 2.1 General & Cloud Security
- **Never commit secrets.** Use `.env` (git-ignored) locally and secrets managers in CI/CD.
- Dependency scanning runs in CI; high/critical vulnerabilities block releases.
- All user input must be sanitized and validated at boundaries on both client and server.
- Passwords must use `argon2` or `bcrypt` — never reversible encryption.
- **Zero-Trust Client Models:** For BaaS platforms (Firebase, Supabase), you MUST maintain the corresponding security rules (`firestore.rules`, `storage.rules`). Never rely on permissive rules or client-only checks.
- **RBAC:** Role-based access control must be validated on backend routes and database levels via secure tokens/claims, not just by hiding UI elements.

### 2.2 Observability & Privacy
- **PII Masking:** Never log Personally Identifiable Information (names, emails, geolocations, tokens). Mask or hash this data before passing to Timber/Pino or Sentry/Crashlytics.
- **Actionable Breadcrumbs:** State transformations in ViewModels and hooks must log lightweight breadcrumbs so crashes can be reproduced from the exact transition sequence.
- **Structured Logging:** Use correlation/request IDs across service calls. Never use raw `console.log` or `println` in production paths.

### 2.3 Repository Auto-Documentation & Health Files
- **Agent Ownership:** The agent is strictly responsible for scaffolding and maintaining `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, and `.github/` templates. Do not expect human manual authoring.
- **Proactive Changelog Updates:** After completing a feature or bug fix, proactively offer to update `CHANGELOG.md` based on the commit history.
- **Source of Truth:** Generate repository documentation strictly from the architecture defined in these rules files.
- **No Hallucinated Credentials:** When scaffolding templates needing personal contact details (e.g., reporting emails in `SECURITY.md`), NEVER invent data. Use explicit placeholders like `[INSERT CONTACT EMAIL HERE]`.

---

## 3. Global Agent Guardrails

- **Search Before Writing (Zero Duplication):** Before writing any utility function, custom UI component, or extension function (dates, formatting, error parsing), search the codebase. Check `:core:utils`, `:core:designsystem`, and `shared/` directories. NEVER duplicate existing logic.
- **Refactor Over Copy-Paste:** If a shared function is almost what you need, extend it with safe defaults rather than copy-pasting a variation.
- **Extract New Shared Logic:** Place reusable utilities and UI primitives in `:core` or `shared` modules immediately, not buried in a feature module.
- **No Arbitrary Dependencies:** Do not introduce major libraries (DI, ORM, state management) without explicitly flagging the architectural rationale in the PR description.
- **No Mock-Only Tests:** Write tests against real collaborators (in-memory DBs, real use cases) instead of tests that only assert mocks were called.
- **Never Disable Tests:** Never disable a failing test to turn CI green. Fix the root cause or mark `@Ignore("PROJ-123: flaky, tracked")` with a ticket link.