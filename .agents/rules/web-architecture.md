---
trigger: glob
globs: **/*.ts, **/*.tsx, **/*.js, **/*.jsx, **/*.json
---

# Full-Stack Web Architecture (React + Node.js)

> Instructions for web platform development spanning frontend apps, backend APIs, and monorepos.

---

## 1. Frontend (Next.js & React)

### 1.1 Stack & Conventions
- **Next.js (App Router)** is the default for SSR/SEO applications. Use Vite + React Router only for pure internal SPAs.
- **TypeScript strict mode** everywhere. Avoid `any`; if unavoidable, provide an `// eslint-disable` comment with written justification.
- **React Server Components by default.** Use `"use client"` only where interactivity, local state, or browser APIs are required.
- **Styling:** **Tailwind CSS** + **shadcn/ui** / Radix primitives. Avoid heavy opinionated UI libraries.
- **Forms & Validation:** **React Hook Form + Zod**. Reuse Zod schemas across client and server boundaries.
- **Accessibility:** Semantic HTML first, ARIA to bridge gaps. Every interactive element must be keyboard navigable. Run axe checks in CI.

### 1.2 State & Component Structure
- Local component state via `useState` first.
- Server state / caching: **TanStack Query (React Query)** exclusively. Never write custom `useEffect` fetch-and-cache loops.
- Shared client state: **Zustand**. Do not introduce Redux unless cross-cutting state demands it.
- **Custom Hooks as ViewModels:** Presentational components stay lean (props in, JSX out). Move state orchestration and query calls into custom hooks (e.g., `useCheckoutForm()`).

---

## 2. Backend (Node.js & NestJS)

### 2.1 Stack & Conventions
- Default framework: **NestJS** (DI, modules, guards, interceptors). Use **Fastify** for raw throughput routes; use **Hono** strictly for edge/serverless functions.
- **Database & ORM:** **Prisma** for traditional migrations and DX; **Drizzle** for SQL-level control, HTTP serverless drivers, or low runtime overhead. Never mix ORMs within the same service.
- **Auth:** Short-lived JWT access tokens + revocable DB-backed refresh tokens. Store session cookies using `httpOnly`, `secure`, and `sameSite` flags. Never store sensitive tokens in browser `localStorage`.
- **Async Processing:** Use **BullMQ** (Redis-backed) for queue-based and background jobs. Never run heavy computations inline during request cycles.
- **Input Validation:** Enforce validation on every inbound DTO using Zod or `class-validator`. Never trust client payloads.

---

## 3. Monorepo Architecture & Production Quality

### 3.1 Modular Monorepo (Turborepo / Nx)
Enforce clean boundaries mirroring Clean Architecture:
- `apps/web`: Next.js frontend.
- `apps/api`: NestJS backend.
- `packages/shared-types`: Zod schemas and DTOs shared between web and API.
- `packages/ui`: Shared design-system components.
- `packages/config`: Shared ESLint, TS, and Tailwind configurations.
- **Boundary Rule:** `apps/*` depend on `packages/*`. Packages never import from apps. Web and API interact solely via HTTP contracts defined in `shared-types`.

### 3.2 Backend Layering & Reliability
- **Thin Controllers:** Controller (route/validation) → Service (business logic) → Repository (database access). Never put business rules inside route handlers.
- **Idempotency:** Implement idempotency keys on mutating endpoints (payments, order creation) so client retries do not duplicate state.
- **Explicit Error Shapes:** Return consistent JSON error payloads (`code`, `message`, `correlationId`). Map exceptions via NestJS filters; never expose database stack traces to clients.
- **Caching:** Use Redis for hot read paths. Define an explicit cache invalidation strategy for every cached key.
- **Rate Limiting:** Protect public endpoints at the reverse proxy/gateway layer and per-IP/user at the application layer.
- **Stateless Services:** Keep services stateless (sessions in Redis/DB) to allow horizontal scaling and zero-downtime deployments.

---

## 4. Web Testing Standards

- **Unit & Component:** **Vitest + React Testing Library**. Test user behavior and DOM output, not internal implementation details or private state.
- **API & Integration:** **Vitest/Jest + Supertest** against real test databases using **Testcontainers** — avoid fully-mocked DB layers.
- **End-to-End (E2E):** **Playwright** testing critical user journeys against deployed preview environments in CI.
- **Contract Testing:** Validate frontend/backend contracts with shared Zod schemas or Pact before releases.