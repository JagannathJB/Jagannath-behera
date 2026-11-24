# FinPilot AI - MVP foundation

AI-powered payment operations and fraud investigation platform (synthetic data only).

**Current state: Milestone 3.** PostgreSQL in Docker, a Spring Boot API with Flyway, and a React dashboard with payment creation (idempotent), a payment state machine with audit history, search/filter/pagination, and synthetic seed data. Not yet built: Kafka and outbox (Milestone 4), fraud rules and cases (5), authentication and observability (6), AI copilot, AWS. Payment "processing" is simulated by a manual transition endpoint until Milestone 4.

| Part | Tech | URL |
|---|---|---|
| Dashboard | React 18, TypeScript, Vite 5 | http://localhost:5173 |
| Backend API | Java 21, Spring Boot 3.5.16, Maven, JPA, Flyway | http://localhost:8080 |
| Health endpoint | Spring Boot Actuator | http://localhost:8080/actuator/health |
| Payments API | `/api/v1/payments` (create, search, detail, transitions) | http://localhost:8080/api/v1/payments |
| Database | PostgreSQL 16 (Docker Compose) | `localhost:5432` |

## Prerequisites (Windows 11, PowerShell)

Install once (a new terminal is needed afterwards so PATH updates apply):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install Docker.DockerDesktop     # then start Docker Desktop and wait until it says "running"
```

Maven 3.9+ is also required: download the binary zip from https://maven.apache.org/download.cgi, extract it, and add its `bin` folder to your PATH (a Maven wrapper is not included).

Verify:

```powershell
java -version            # must report 21.x
mvn -version             # 3.9 or newer, running on Java 21
node -v                  # 20 or newer
docker compose version
```

## Run it

Use three PowerShell windows from the repository root.

**1. PostgreSQL**

```powershell
Copy-Item .env.example .env
notepad .env                      # replace POSTGRES_PASSWORD=change-me with your own local password
docker compose up -d
docker compose ps                 # STATUS should become "healthy"
```

**2. Backend** (reads `..\.env` automatically; Flyway creates the schema on startup)

```powershell
cd backend
mvn spring-boot:run
```

Ready when the log shows `Started FinPilotApplication`.

**3. Frontend**

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

## Verify

```powershell
curl.exe -s http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}

curl.exe -s "http://localhost:8080/api/v1/payments?size=2"
# {"content":[...],"page":0,"size":2,"totalElements":N,"totalPages":M}
# totalElements is 0 on an empty database, or about 500 with SEED_ENABLED=true
```

(Use `curl.exe`; plain `curl` is a PowerShell alias for `Invoke-WebRequest`.) The dashboard should show **Backend UP** and the empty state "No payments yet".

To see the populated table, insert one row by hand (this is manual demo data, not application logic):

```powershell
docker compose exec postgres psql -U finpilot -d finpilot -c "INSERT INTO payments (id, payment_reference, amount, currency, status) VALUES (gen_random_uuid(), 'P-1024', 1250.50, 'USD', 'COMPLETED');"
```

Reload the dashboard; remove it again with `DELETE FROM payments WHERE payment_reference = 'P-1024';` using the same command pattern.

## Milestone 3 features

**Seed data.** `.env.example` sets `SEED_ENABLED=true`, so the backend inserts 500 deterministic synthetic payments on first start (skipped if already seeded). Reset everything (asks you to type YES first): `.\scripts\reset-local-db.ps1`.

**API**

| Method and path | Purpose |
|---|---|
| `POST /api/v1/payments` | Create. Requires header `Idempotency-Key`. 201 new, 200 + `Idempotent-Replayed: true` on replay, 422 if the key is reused with a different body |
| `GET /api/v1/payments` | Search. Params: `status`, `currency`, `minAmount`, `maxAmount`, `createdFrom`, `createdTo` (ISO-8601), `reference`, `page`, `size` (max 100), `sort` (`createdAt|amount|status|paymentReference`, `,asc|desc`) |
| `GET /api/v1/payments/{id}` | Detail with status history and allowed next states |
| `POST /api/v1/payments/{id}/transitions` | Move state (`CREATED→VALIDATED→PROCESSING→COMPLETED`, plus `REQUIRES_REVIEW` and `FAILED`; a reason is required for those two). Invalid moves return 409 |

Errors are RFC 9457 `application/problem+json`. Send `X-Correlation-Id` (or one is generated); it is stored and echoed.

**Try idempotency in PowerShell**

```powershell
$body = @{ amount = 1250.50; currency = "USD"; debtorAccount = "SYN100000007919"; creditorAccount = "SYN100000099999"; creditorName = "Northwind Traders"; description = "Invoice INV-1001" } | ConvertTo-Json
$key = [guid]::NewGuid().ToString()
$uri = "http://localhost:8080/api/v1/payments"
(Invoke-WebRequest -Method Post -Uri $uri -Headers @{ "Idempotency-Key" = $key } -ContentType "application/json" -Body $body).StatusCode   # 201
(Invoke-WebRequest -Method Post -Uri $uri -Headers @{ "Idempotency-Key" = $key } -ContentType "application/json" -Body $body).StatusCode   # 200, same payment, no duplicate
```

Business-rule failures (same debtor and creditor account, amount over 1,000,000, unknown currency) create a payment in `FAILED` state with the reason, rather than an HTTP error. Malformed input returns 400 with per-field errors.

## Run the tests

```powershell
cd backend;  mvn test          # unit + web-layer tests; the Testcontainers integration test (incl. a concurrent idempotency race) needs Docker running
cd frontend; npm test          # Vitest + React Testing Library
cd frontend; npm run build     # type-check + production build
```

## Stop

```powershell
docker compose down            # stops PostgreSQL, keeps your data volume
```

`docker compose down -v` also **deletes the database volume and all data**.

## Configuration

All environment variables live in `.env` (git-ignored; template in `.env.example`): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`; optional `DB_HOST`, `SERVER_PORT`. Docker Compose and the backend read the same file. Real environment variables override it, for example `$env:POSTGRES_PASSWORD = "..."`. The backend has no default password and fails fast without one. The frontend dev server proxies `/api` and `/actuator` to the backend (override with `$env:VITE_BACKEND_URL`), so no CORS setup is needed.

## Important choices

- **Spring Boot 3.5.16** is the latest 3.5.x patch, but it is also the **final open-source release of the 3.5 line** (June 2026). Plan a move to 4.0.x/4.1.x before production; the change is isolated to `backend/pom.xml` plus any API changes the upgrade guide lists.
- **Single backend app, feature-packaged** (`com.finpilot.payments`), not microservices yet; split later only where a boundary earns it.
- **Flyway owns the schema**; Hibernate runs with `ddl-auto: validate`, so entity/schema drift fails at startup.
- **Entities never leave the service layer**; the API returns `PaymentResponse` records. The list is capped at 100 rows until pagination is designed.
- `flyway-database-postgresql` is required alongside `flyway-core` for Flyway 10+.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `mvn` / `node` / `docker` is not recognized | Open a **new** PowerShell window after installing; check PATH. |
| `Could not resolve placeholder 'POSTGRES_PASSWORD'` | `.env` is missing, or you didn't start from `backend\`. Create `.env`, or set `$env:POSTGRES_PASSWORD`. |
| `password authentication failed for user "finpilot"` | PostgreSQL only applies the password when its volume is first created. Restore the original password in `.env`, or (loses data) `docker compose down -v` then `docker compose up -d`. |
| `port is already allocated` / bind error on 5432 | Another PostgreSQL uses it (`Get-NetTCPConnection -LocalPort 5432`). Set `POSTGRES_PORT=5433` in `.env`, then restart both Compose and the backend. |
| Port 8080 already in use | `$env:SERVER_PORT = "8081"; $env:VITE_BACKEND_URL = "http://localhost:8081"` before starting backend and frontend. |
| Docker commands fail / "cannot connect to the Docker daemon" | Start Docker Desktop and wait for it to finish starting; check `docker info`. Needs WSL 2. |
| `npm : running scripts is disabled on this system` | Use `npm.cmd install` / `npm.cmd run dev`, or run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`. |
| Dashboard shows "Backend unreachable" | Backend isn't running or crashed; run the `curl.exe` health check and read the backend console. |
| `Schema-validation: wrong column type` | Entity and migration disagree. Fix with a new Flyway migration; never edit an applied one. |
| Integration test shows as skipped | Docker isn't running. If it errors about a Docker API version, update Testcontainers (Spring Boot BOM) or Docker Desktop. |
