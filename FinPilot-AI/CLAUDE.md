# CLAUDE.md - FinPilot AI project conventions

AI-powered payment operations and fraud investigation platform. Synthetic data only; never real bank data.

## Stack and layout
- `backend/` - Java 21, Spring Boot 3.5.x, Maven, Spring Web, Data JPA, Flyway, PostgreSQL. Package root `com.finpilot`, organised **by feature** (`com.finpilot.payments`).
- `frontend/` - React 18 + TypeScript + Vite. Vitest + React Testing Library.
- `docker-compose.yml` - PostgreSQL only. Backend and frontend run locally.
- Developer machine is **Windows 11 / PowerShell**: give PowerShell commands (`Copy-Item`, `curl.exe`, `$env:VAR`), not bash. AWS is the eventual deploy target.

## Commands (from repo root)
- DB: `docker compose up -d` / `docker compose ps`
- Backend: `cd backend; mvn spring-boot:run` | tests: `mvn test`
- Frontend: `cd frontend; npm install; npm run dev` | `npm test` | `npm run build`

## Backend conventions
- Controllers are thin: map HTTP to a service call only. Business logic lives in `@Service` classes; transactions are declared on service methods (`@Transactional(readOnly = true)` for reads).
- API responses are DTOs (Java records). Never return JPA entities. Entities stay package-visible to their feature's service layer.
- Schema changes only through Flyway (`db/migration/V<n>__description.sql`). Never edit a migration that has been applied; add a new one. Hibernate `ddl-auto` stays `validate`.
- Money is `BigDecimal` / `NUMERIC(19,4)` plus an ISO-4217 currency code. Timestamps are `Instant` / `TIMESTAMPTZ` (UTC).
- Endpoints live under `/api/v1/...`. Use constructor injection, no field injection.
- Every behavior change ships with a test. Prefer `@WebMvcTest` and plain Mockito unit tests; use Testcontainers for database-backed tests.

## Payment domain rules (Milestone 3)
- The state machine lives only in `PaymentStatus.allowedTargets()`; status changes only via `Payment.transitionTo(...)`, which appends the audit history row. Never set status directly.
- Create is idempotent: `Idempotency-Key` header + SHA-256 of the canonical body (`RequestHasher`). The unique constraint on `payments.idempotency_key` is the concurrency guard; `PaymentService.create` is deliberately not `@Transactional` so the loser of a race can catch the violation and replay.
- Search is built with `PaymentSpecifications` (parameterised); sort fields are whitelisted in `PaymentService.parseSort`. Page size max 100.
- Errors are `ProblemDetail` via `ApiExceptionHandler`; add a handler for each new domain exception.
- Seed data (`SeedDataRunner`) is local-only, gated by `SEED_ENABLED`, and must go through the domain model.

## Frontend conventions
- Every data-driven view handles loading, error (with retry) and empty states.
- API access goes through `src/api.ts`; types in `src/types.ts` mirror backend DTOs.
- In dev the browser calls Vite, which proxies `/api` and `/actuator` to the backend (no CORS config).

## Security and secrets
- Never commit secrets. `.env` is git-ignored; only `.env.example` with placeholders is committed. No default passwords in code or `application.yml`.
- Do not log secrets or unnecessary sensitive data.

## Scope guardrails
- Do not add Kafka, AI/LLM integration, authentication, or Kubernetes until explicitly requested.
- Do not claim a command or test passed unless it actually ran and succeeded; state what is unverified.
- Ask before destructive operations: deleting files/directories, `docker compose down -v`, dropping or resetting databases, force-pushes, editing applied migrations.

## Known decisions
- Spring Boot 3.5.16 is the last OSS 3.5.x release; plan an upgrade to 4.0.x/4.1.x.
