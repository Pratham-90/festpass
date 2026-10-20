# FestPass conventions

Flash-sale ticketing for college fests. Full spec and phase plan: `PLAN.md` (build one phase at a time).

## Commands
- Infra: `docker compose up -d postgres mailpit` (Mailpit inbox: http://localhost:8025)
- Backend: `cd backend && ./mvnw spring-boot:run` (API on :8080, `dev` profile seeds data; Windows: `mvnw.cmd`)
- Frontend: `cd frontend && npm install && npm run dev` (UI on :5173, proxies `/api` and `/ws` to :8080)
- Tests: `cd backend && ./mvnw verify` (needs Docker running for Testcontainers)
- Frontend build: `cd frontend && npm run build`
- One JAR / full stack: `docker compose up --build` (UI + API on :8080)

## Stack rules
- Only the technologies in PLAN.md Section 2. Ask before adding any library or service.
- One Spring Boot modular monolith. No Kafka, Redis, Kubernetes or microservices.
- Frontend is JavaScript (not TypeScript). Colours, fonts and radii come from `@theme` tokens in `frontend/src/styles/index.css`.

## Backend conventions
- Package per feature under `com.festpass` (`event`, `booking`, `ticket`, ...); shared code in `common`.
- Each package: `controller -> service -> repository`. DTOs are Java `record`s. Controllers never return JPA entities.
- Money is `long` paise. Timestamps are `timestamptz`, always from the injected `Clock` (never `Instant.now()` without it).
- Schema changes only via Flyway (`db/migration/V<n>__name.sql`); Hibernate is `validate` only.
- Entities hold foreign keys as plain ids (`Long eventId`), not JPA relations.
- Errors: throw `ApiException` with an `ErrorCode`; it becomes a `ProblemDetail` with a stable `code`.
- Publish WebSocket messages and send email only after the transaction commits.
- Document every endpoint in `docs/api.md` as it is built.

## Testing
- Integration tests extend `IntegrationTest` (Testcontainers PostgreSQL + Mailpit, frozen `MutableClock`). Never H2.
- Concurrency tests start threads together with a `CountDownLatch` and assert exact counts.
- Time-dependent tests move the `Clock`; never `Thread.sleep` to wait for expiry.
- If Docker is unavailable, say so. Never skip or weaken tests.

## Guarantees (never trade these for a feature)
- A seat can never be sold twice.
- A general-admission tier can never sell more than its capacity.
- A ticket can be checked in only once.
- Retrying an order or payment with the same idempotency key never creates a second order or ticket set.
- An expired hold never turns into a ticket.
