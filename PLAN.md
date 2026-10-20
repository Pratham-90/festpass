# FestPass: Build Plan for Claude Code

FestPass is a flash-sale event ticketing platform for college fests and society events. It is the course project for UCS553 Enterprise Web Application (TIET), built by Pratham Arora, Krishna Pandey and Nipun Behl.

The core functionality (create events, book tickets, scan at the gate) is simple. What makes this project stand out is that it must feel **live and fun**: seats change colour in real time as other people grab them, flash drops open with a countdown and a waiting room, tickets arrive with confetti, the gate scanner flashes green or red, and an admin "Stress Arena" lets the professor watch 500 bots fight over 50 seats with **zero overselling**.

---

## 0. How to use this document (instructions for Claude Code)

1. Read this whole file before writing any code.
2. Build **one phase at a time, in order**. Do not start the next phase until the current phase's acceptance criteria pass.
3. At the end of each phase:
   - run the full test suite and the app locally;
   - tick the phase's boxes in **Section 12 (Progress tracker)** of this file;
   - commit with a message like `phase-2: booking engine with seat holds and mock payments`;
   - write a short summary (what was built, how to run it, anything deferred), then **stop and wait for the team to review**.
4. Use only the technologies listed in Section 2. Ask before adding any other library or service.
5. Keep the code simple and readable. This is a single Spring Boot application (a modular monolith), not microservices. Do not add Kafka, Redis, Kubernetes, service discovery or similar.
6. Correctness of the booking engine matters more than anything else. Never trade it for a feature.
7. If something in this plan is ambiguous or seems wrong, ask instead of guessing.

---

## 1. Product overview

### Roles

| Role | What they do |
|---|---|
| `STUDENT` | Browses events, joins flash-drop queues, picks seats, pays (mock), keeps tickets in a wallet, collects passport stamps |
| `ORGANIZER` | Creates events, designs seat layouts and ticket tiers, watches the live dashboard |
| `GATE_STAFF` | Scans tickets at the entry gate for an assigned event |
| `ADMIN` | Manages users and roles, runs the Stress Arena |

### Core user journey

1. A student opens an event page and sees a live countdown, a "people viewing now" counter and the seat map.
2. For a **flash-drop** event, the student joins the waiting room before the sale opens. At opening time, people are admitted in batches, and the student sees their queue position move.
3. The student picks seats. The seats are **held** for them for a few minutes (a countdown ring is shown) and turn "held" for everyone else instantly.
4. The student pays through the mock payment page. The order is idempotent, so double clicks and retries never double-book.
5. On success, confetti fires and a flippable ticket card with a QR code appears in the wallet. A confirmation email (with a PDF ticket and an `.ics` calendar file) is sent.
6. At the venue, gate staff scan the QR with a phone camera. Valid tickets give a green flash and a sound; reused or fake tickets give a red flash with the reason.
7. The checked-in event adds a stamp to the student's **Fest Passport**.

### The guarantees (non-negotiable)

- A seat can never be sold twice.
- A general-admission tier can never sell more than its capacity.
- A ticket can be checked in only once.
- Retrying a payment or an order request with the same idempotency key never creates a second order or a second set of tickets.
- An expired hold never turns into a ticket.

---

## 2. Tech stack

### Backend (Java)
- Java 21 (virtual threads are used in the Stress Arena)
- Spring Boot 3.x with Maven
- Spring Web, Spring Validation, Spring Security (session-based login with an HTTP-only cookie, CSRF enabled)
- Spring Data JPA (Hibernate) with PostgreSQL 16 and Flyway migrations
- Spring WebSocket with STOMP (simple in-memory broker)
- Spring Mail (Mailpit in Docker catches the emails during development)
- `@Scheduled` jobs for hold expiry and queue admission
- ZXing for QR codes, OpenPDF for PDF tickets
- Bucket4j (in-memory) for rate limiting
- JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), AssertJ

### Frontend
- React 18 with Vite (JavaScript, not TypeScript, to suit the team's experience)
- React Router, TanStack Query for server state
- Tailwind CSS for styling, Framer Motion for animations
- `@stomp/stompjs` for WebSockets
- `canvas-confetti`, `html5-qrcode` (gate scanner), `recharts` (dashboards)
- The seat map is hand-written SVG (no seat-map library)

### Infrastructure
- Docker Compose: `postgres`, `mailpit`, `app`
- A multi-stage Dockerfile builds the React app, copies it into Spring Boot's `static/` folder and produces **one runnable JAR** that serves both the API and the UI
- GitHub Actions: build and test on every push
- Apache JMeter test plan for the formal load-test report

---

## 3. Repository structure

```
festpass/
├── PLAN.md                     # this file
├── CLAUDE.md                   # short conventions file (created in Phase 0)
├── README.md
├── docker-compose.yml
├── Dockerfile
├── .github/workflows/ci.yml
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/festpass/
│       │   ├── FestPassApplication.java
│       │   ├── common/        # errors, ProblemDetail handler, clock, ids, config
│       │   ├── auth/          # security config, login, register, current user
│       │   ├── user/
│       │   ├── event/         # events, tiers, seat layout generation
│       │   ├── seating/       # seat map queries
│       │   ├── booking/       # holds, orders, hold expiry job
│       │   ├── payment/       # mock payment gateway
│       │   ├── ticket/        # tickets, QR signing, PDF, ICS, email
│       │   ├── checkin/       # gate scanning
│       │   ├── queue/         # flash-drop waiting room
│       │   ├── realtime/      # WebSocket config and publishers
│       │   ├── dashboard/     # organizer stats
│       │   └── arena/         # stress arena (bots)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/migration/  # Flyway V1__..., V2__...
│       │   └── static/        # React build output (generated, git-ignored)
│       └── test/java/com/festpass/
├── frontend/
│   ├── package.json
│   ├── vite.config.js         # dev proxy: /api and /ws -> localhost:8080
│   └── src/
│       ├── api/               # fetch wrapper, query hooks
│       ├── realtime/          # STOMP client hook
│       ├── components/        # SeatMap, TicketCard, Countdown, HoldTimer, ...
│       ├── pages/
│       └── styles/
├── load-tests/festpass.jmx
└── docs/
    ├── architecture.md
    ├── api.md
    └── demo-script.md
```

Each backend package follows `controller → service → repository`, with DTOs as Java `record`s. Controllers never return JPA entities directly.

---

## 4. Data model

All money is stored as `long` **paise**. All timestamps are `timestamptz` and are created through an injectable `Clock`, so tests can control time.

| Table | Key columns |
|---|---|
| `users` | `id`, `name`, `email` (unique), `password_hash` (BCrypt), `role`, `created_at` |
| `events` | `id`, `organizer_id`, `slug` (unique), `title`, `description`, `category`, `banner_url`, `venue_name`, `starts_at`, `ends_at`, `sale_opens_at`, `sale_closes_at`, `status` (`DRAFT`/`PUBLISHED`/`CANCELLED`), `seating_mode` (`SEATED`/`GENERAL`), `hold_minutes` (default 5), `max_tickets_per_user` (default 4), `flash_drop` (bool), `is_sandbox` (bool, used by the arena), `created_at` |
| `ticket_tiers` | `id`, `event_id`, `name`, `price_paise`, `capacity`, `reserved` (held + sold), `color_hex` |
| `seats` | `id`, `event_id`, `tier_id`, `section`, `row_label`, `seat_number`, `x`, `y`, `status` (`AVAILABLE`/`HELD`/`BOOKED`), `hold_id`, `hold_expires_at`; unique on (`event_id`, `section`, `row_label`, `seat_number`) |
| `holds` | `id` (UUID), `event_id`, `user_id`, `tier_id` (GA only), `quantity` (GA only), `status` (`ACTIVE`/`CONVERTED`/`EXPIRED`/`RELEASED`), `expires_at`, `created_at` |
| `orders` | `id`, `user_id`, `event_id`, `hold_id` (unique), `idempotency_key` (unique), `amount_paise`, `status` (`PENDING`/`PAID`/`FAILED`), `created_at`, `paid_at` |
| `payment_attempts` | `id`, `order_id`, `idempotency_key` (unique), `outcome`, `created_at` |
| `tickets` | `id` (UUID), `order_id`, `event_id`, `tier_id`, `seat_id` (nullable), `holder_name`, `checked_in_at`, `checked_in_by`; **partial unique index on `seat_id` where `seat_id` is not null** |
| `checkin_scans` | `id`, `event_id`, `ticket_id` (nullable), `scanned_by`, `result` (`VALID`/`ALREADY_USED`/`INVALID`/`WRONG_EVENT`), `scanned_at` |
| `gate_assignments` | `user_id`, `event_id` |
| `arena_runs` | `id`, `sandbox_event_id`, `bots`, `strategy`, `status`, `started_at`, `finished_at`, `result_json` |

The partial unique index on `tickets.seat_id` is the database's last line of defence against double booking, even if a bug slips into the service layer.

---

## 5. The booking engine (the heart of the project)

### 5.1 Seated holds
Holding seats is one transaction and **all-or-nothing**:

```sql
UPDATE seats
SET status = 'HELD', hold_id = :holdId, hold_expires_at = :expiresAt
WHERE event_id = :eventId
  AND id IN (:seatIds)
  AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at < :now));
```

If the updated row count is not equal to the number of requested seats, roll back and return `409 SEAT_UNAVAILABLE` with the IDs of the seats that are no longer free. The check `hold_expires_at < :now` means correctness never depends on the expiry job's timing: an expired hold is treated as free immediately.

### 5.2 General-admission holds
```sql
UPDATE ticket_tiers
SET reserved = reserved + :qty
WHERE id = :tierId AND reserved + :qty <= capacity;
```
Zero rows updated means `409 SOLD_OUT`. Releasing or expiring a GA hold decrements `reserved`.

### 5.3 Per-user ticket limit
Within the hold transaction, take a PostgreSQL advisory transaction lock on (user, event) with `pg_advisory_xact_lock`. Then check: active holds + owned tickets + requested quantity ≤ `max_tickets_per_user`. Otherwise return `409 LIMIT_EXCEEDED`.

### 5.4 Orders and mock payment
- `POST /api/orders` takes `{holdId}` plus an `Idempotency-Key` header. If the same key arrives again, return the existing order. One order per hold (enforced by the unique constraint on `hold_id`).
- The mock payment page shows "Pay" and "Fail" buttons and simulates 0.5–2 s of latency.
- Confirming a payment (also idempotent through `payment_attempts.idempotency_key`) runs in one transaction:
  1. lock the order row (`SELECT ... FOR UPDATE`);
  2. if the order is already `PAID`, return it unchanged;
  3. if the hold has expired or is not `ACTIVE`, mark the order `FAILED`, release the hold and return `409 HOLD_EXPIRED`;
  4. otherwise set seats to `BOOKED` with `UPDATE seats SET status = 'BOOKED' WHERE hold_id = :holdId AND status = 'HELD'` (the updated row count must equal the hold's seat count, or the payment fails as `HOLD_EXPIRED`), create the tickets, set the hold to `CONVERTED` and the order to `PAID`.
- The email is sent **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`), so a failed email never undoes a payment.

### 5.5 Hold expiry
A `@Scheduled` job runs every 5 seconds. It marks expired holds `EXPIRED`, frees their seats (or decrements `reserved` for GA tiers), and publishes seat updates over WebSocket.

Every hold state change (expire, release, convert) is a conditional update such as `UPDATE holds SET status = 'EXPIRED' WHERE id = :id AND status = 'ACTIVE'`. Seats are freed or `reserved` is decremented only if that update changed exactly one row, so the expiry job, a user cancelling and a late payment can never release the same hold twice.

### 5.6 Ticket codes and check-in
- The QR code contains `ticketId.eventId.signature`, where the signature is an HMAC-SHA256 with a server secret from configuration. A forged code fails verification and returns `INVALID`.
- Check-in is a single conditional update:
  ```sql
  UPDATE tickets SET checked_in_at = :now, checked_in_by = :staffId
  WHERE id = :ticketId AND event_id = :eventId AND checked_in_at IS NULL;
  ```
  Zero rows updated means `ALREADY_USED` (the response includes when and by whom) or `WRONG_EVENT`. Every scan is logged to `checkin_scans`.

### 5.7 Rate limiting
Bucket4j, per user: at most 5 hold requests per second and 30 per minute. Over the limit returns `429` with a `Retry-After` header.

### 5.8 Errors
All errors use Spring's `ProblemDetail` (RFC 7807) with a stable `code` field: `SEAT_UNAVAILABLE`, `SOLD_OUT`, `LIMIT_EXCEEDED`, `HOLD_EXPIRED`, `NOT_ADMITTED`, `SALE_NOT_OPEN`, `RATE_LIMITED`, `VALIDATION_FAILED`. The frontend maps each code to a friendly message.

---

## 6. REST API

Prefix: `/api`. JSON everywhere. Document every endpoint in `docs/api.md` as it is built.

**Auth**
- `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`

**Public events**
- `GET /events?q=&category=&upcoming=true`
- `GET /events/{slug}`
- `GET /events/{id}/seatmap` returns sections, seats with status and tier colours, and tiers with remaining counts

**Organizer**
- `POST /organizer/events`, `PUT /organizer/events/{id}`, `POST /organizer/events/{id}/publish`
- `POST /organizer/events/{id}/tiers`
- `POST /organizer/events/{id}/sections` with `{name, tierId, rows, seatsPerRow, aisleAfter: [4, 12]}`, which generates the seats with x/y coordinates
- `GET /organizer/events/{id}/dashboard`
- `POST /organizer/events/{id}/gate-staff` with `{email}`

**Booking**
- `POST /events/{id}/holds` with `{seatIds}` or `{tierId, quantity}`; for flash drops, the `X-Admission-Token` header is required
- `DELETE /holds/{id}`
- `POST /orders` (`Idempotency-Key` header)
- `GET /orders/{id}`
- `POST /payments/mock/{orderId}` with `{outcome: "SUCCESS" | "FAIL"}` (`Idempotency-Key` header)

**Tickets**
- `GET /me/tickets`, `GET /tickets/{id}`, `GET /tickets/{id}/qr.png`, `GET /tickets/{id}/pdf`, `GET /tickets/{id}/calendar.ics`
- `GET /me/passport`

**Waiting room**
- `POST /events/{id}/queue` (join), `GET /events/{id}/queue/me`

**Gate**
- `POST /checkin` with `{eventId, code}`, `GET /events/{id}/checkin/stats`

**Admin**
- `GET /admin/users`, `PUT /admin/users/{id}/role`
- `POST /admin/arena/runs` with `{sourceEventId, bots, strategy}`, `GET /admin/arena/runs/{id}`

---

## 7. Real-time layer (WebSocket / STOMP)

Endpoint: `/ws`. The same session cookie authenticates the connection.

| Destination | Payload | Used by |
|---|---|---|
| `/topic/events/{id}/seats` | batched seat deltas `[{seatId, status}]`, sent at most every 100 ms | seat map |
| `/topic/events/{id}/stats` | `{viewers, remainingByTier, soldLastMinute}` | event page, dashboard |
| `/topic/events/{id}/queue` | `{waiting, admittedSoFar, opensAt}` | waiting room |
| `/user/queue/admission` | `{eventId, admissionToken, expiresAt}` | waiting room |
| `/user/queue/holds` | `{holdId, status}` (for example, expired) | checkout |
| `/topic/events/{id}/checkins` | `{checkedIn, total, lastScan}` | gate screen, dashboard |
| `/topic/arena/{runId}` | progress ticks and the final report | Stress Arena |

The "viewers" count comes from tracking subscriptions to an event's topics (subscribe and disconnect events). Publish seat deltas only **after** the database transaction commits.

---

## 8. Fun and interactive features (what makes this project memorable)

1. **Live seat map.** SVG with zoom and pan. Seats are coloured by tier. Seats held by others pulse softly; booked seats fade out; your own selected seats glow. Updates arrive in real time, and if a seat you were about to click gets taken, it shakes and a toast says "Someone just grabbed that seat!".
2. **Hold timer.** A circular countdown ring during checkout turns amber in the last minute and red in the last 15 seconds. When it expires, the seats animate back to the map.
3. **Flash drop and waiting room.** A large countdown to sale opening, a live "1,284 people waiting" counter, and an animated queue-position bar. At opening, users are admitted in batches (for example, 50 every 5 seconds); admitted users get a "You're in!" animation and a 10-minute admission window.
4. **Ticket reveal.** Confetti in the tier's colour, then a ticket card that flips in 3D to show the QR code. Buttons to download the PDF and add the event to a calendar.
5. **Gate scanner.** A full-screen camera view on a phone. A valid scan flashes the whole screen green with a chime and a short vibration; an invalid one flashes red with the reason ("Already scanned at 7:42 PM by Gate 2"). A live counter shows how many people are in.
6. **Organizer mission control.** Live charts of sales over time, tier fill bars, a seat-map heatmap of what sold first, check-in progress and live viewers.
7. **Fest Passport.** Every checked-in event adds an animated stamp to the student's passport page, with simple milestones (first event, five events, "front row regular").
8. **Stress Arena (the demo centrepiece).** An admin picks an event, a number of bots (for example, 500) and a strategy (`RUSH_SAME_SEATS`, `RANDOM_SEATS`, `GA_RUSH`). The backend clones the event into a sandbox event, creates bot users and logs them in ahead of time (BCrypt is slow, so logins happen before the start), then launches every bot at the same moment on **virtual threads**. Each bot calls the real HTTP API with `java.net.http.HttpClient`: join queue (if a flash drop), hold, order, pay (10% randomly fail payment or abandon, so holds expire). The screen shows the sandbox seat map filling live with bot colours, plus counters for attempts, successes, conflicts and p50/p95 latency. At the end, an **integrity report** runs SQL checks: seats with more than one ticket (must be 0), tiers with `reserved > capacity` (must be 0), orders paid without tickets (must be 0). The result is shown as a big green "0 oversold" badge.

### Design direction
- A dark "festival night" theme: near-black background, one vivid accent colour per event (from its first tier), large bold headings and a lot of motion, without being cluttered.
- Mobile-first. The gate scanner and ticket wallet are used on phones.
- Respect `prefers-reduced-motion`.
- Every loading, empty and error state is designed; no blank screens.
- Krishna owns the final visual design. Build the components so colours, fonts and spacing come from Tailwind theme tokens and are easy to restyle.

---

## 9. Phases

Each phase lists what to build and the acceptance criteria that must pass before moving on.

### Phase 0: Project setup
Build:
- The repository structure from Section 3. Spring Boot app with a `/api/health` endpoint. Vite React app with the dev proxy. Tailwind configured.
- `docker-compose.yml` with PostgreSQL 16 and Mailpit; the app's `application.yml` points to them.
- Multi-stage `Dockerfile` that produces one JAR serving the UI and the API.
- GitHub Actions workflow: backend `mvn verify`, frontend `npm ci && npm run build`.
- `CLAUDE.md` (at most 40 lines) with the commands for running, testing and building, the package conventions and the guarantees from Section 1.
- `README.md` with setup steps.

Acceptance:
- `docker compose up -d postgres mailpit`, then running the backend and `npm run dev`, shows a placeholder React page that calls `/api/health` successfully.
- `docker build` produces an image that serves the same page on port 8080.
- CI passes.

### Phase 1: Backend foundations (data, auth, events)
Build:
- Flyway migrations for every table in Section 4, including indexes and the partial unique index on `tickets.seat_id`.
- JPA entities, repositories and record DTOs.
- Spring Security: register, login, logout, `me`, role-based access to `/api/organizer/**`, `/api/admin/**` and `/api/checkin`. CSRF token handling that works with the SPA (cookie-based CSRF token).
- Event, tier and section endpoints, including the seat generator (rows × seats per row, with aisle gaps and a lettered row label).
- Global `ProblemDetail` exception handler.
- A seed profile (`dev`) with one user per role and three published events: one seated auditorium show (about 200 seats, 3 tiers), one general-admission open-air event (2 tiers), and one flash-drop event whose sale opens 3 minutes after startup.

Acceptance:
- Integration tests (Testcontainers) cover register/login, role restrictions, event creation and seat generation (correct count, unique labels, aisles).
- `GET /api/events/{id}/seatmap` returns the generated layout.

### Phase 2: Booking engine
Build everything in Section 5: holds (seated and GA), the per-user limit, idempotent orders, the mock payment gateway, ticket creation, the hold expiry job, signed QR codes, check-in, rate limiting, PDF tickets, ICS files and the confirmation email via Mailpit.

Acceptance (all automated tests):
- **Concurrency test:** 200 threads try to hold the same seat at the same moment, and exactly 1 succeeds.
- **GA test:** 300 concurrent requests for 1 ticket each against a tier with capacity 100 result in exactly 100 successes and `reserved = 100`.
- **Idempotency test:** posting the same order and the same payment confirmation twice with the same key yields one order and one set of tickets.
- **Expiry test:** with the `Clock` moved past the hold's expiry, the seat is available again and paying the old order returns `HOLD_EXPIRED`.
- **Check-in test:** a first scan is `VALID`, a second is `ALREADY_USED`, a tampered code is `INVALID`, and a ticket scanned at the wrong event is `WRONG_EVENT`.
- The per-user limit and the rate limit are enforced.
- A confirmation email with a PDF attachment appears in Mailpit.

### Phase 3: Real-time layer
Build Section 7: the STOMP configuration, authentication on connect, publishers that send after commit, seat-delta batching, the viewers counter, the waiting room (`queue` package: in-memory queue per event, an admission job that admits batches, signed short-lived admission tokens required by the hold endpoint for flash drops), and check-in broadcasts.

Acceptance:
- A test STOMP client receives a seat delta after another user holds a seat, and again when the hold expires.
- For a flash-drop event, a hold without an admission token returns `NOT_ADMITTED`; after admission it succeeds.
- Queue positions shrink as batches are admitted.
- The in-memory queue limitation (one app instance) is noted in `docs/architecture.md`.

### Phase 4: Frontend core
Build the pages, wired to the real API with TanStack Query and to WebSockets through a single `useStomp` hook:
- Landing / event discovery: search, category filters, animated event cards with countdowns.
- Login and registration.
- Event page: banner, details, live viewers, tier list with remaining counts, and the **seat map** (or a quantity picker for GA events).
- Checkout: hold timer ring, order summary, redirect to the mock payment page and back.
- Ticket wallet: list of tickets, ticket detail with QR, PDF and calendar downloads.
- Organizer: event list, create/edit event form, tier editor, section generator with a **live preview** of the seat map, publish button.
- Admin: user list with role changes.
- A shared layout, navigation by role, toasts for every error code from Section 5.8, and loading, empty and error states.

Acceptance:
- A full journey works in the browser: register → browse → hold seats → pay → see the ticket → organizer sees the sale.
- Opening the same event in two browser windows shows one window's holds appearing in the other within a second.
- Pages work at phone width (375 px).

### Phase 5: The fun layer
Build Section 8 features 1–7 to their full, animated form: seat-map animations and the "seat grabbed" shake, the hold timer colour stages, the waiting room screen, confetti and the 3D ticket flip, the gate scanner (camera, flash, sound, vibration, live counter), organizer mission control with live charts and the seat heatmap, and the Fest Passport.

Acceptance:
- The gate scanner works on a real phone over the local network (document how: the phone opens `http://<laptop-ip>:8080`; note that browsers only allow camera access on `localhost` or HTTPS, so provide a simple way to run with HTTPS, such as a self-signed certificate profile).
- All animations respect `prefers-reduced-motion`.
- The organizer dashboard updates live during a sale without a page refresh.

### Phase 6: Stress Arena
Build Section 8 feature 8: the `arena` package (sandbox cloning, bot user creation, virtual-thread launch with a start barrier so all bots fire together, the three strategies, progress publishing every 250 ms, latency percentiles, the integrity report) and the Arena page (configuration form, live sandbox seat map, counters, final report with the "0 oversold" badge). Sandbox events and bot users must be hidden from all public pages and cleaned up by a "Reset arena" button.

Acceptance:
- 500 bots against 50 seats with `RUSH_SAME_SEATS` finishes in under 30 seconds on a laptop, with an integrity report of all zeros.
- `GA_RUSH` with 1,000 bots against capacity 100 sells exactly 100.
- Running the arena never changes real (non-sandbox) events.

### Phase 7: Hardening, documentation and demo prep
Build:
- Fill test gaps so the service layer has good coverage of the booking, payment, check-in and queue rules (aim for about 80% line coverage on those packages; report it with JaCoCo).
- One Playwright smoke test of the main journey (optional if time is short).
- A JMeter plan in `load-tests/` for the hold endpoint, with instructions for producing an HTML report.
- `docs/architecture.md` with a component diagram (Mermaid), the booking sequence diagram and an explanation of each guarantee.
- `docs/demo-script.md`: a 10-minute demo for the professor (flash drop with two browsers and a phone → booking with confetti → gate scan → mission control → Stress Arena finale).
- A `demo` profile that resets the data and schedules a flash drop to open 2 minutes after startup.
- Security checklist: passwords hashed, CSRF on, authorisation checked on every organizer, gate and admin endpoint (organizers can only edit their own events), QR secret and DB credentials read from environment variables, no stack traces in API responses.

Acceptance:
- `docker compose up` on a fresh machine brings up the whole working app with demo data.
- CI is green and the demo script runs start to finish without manual fixes.

---

## 10. Testing rules

- Every booking, payment, queue and check-in rule gets an integration test against a real PostgreSQL instance (Testcontainers). Do not use H2, because the concurrency guarantees depend on PostgreSQL behaviour.
- Concurrency tests start all threads together with a `CountDownLatch` and assert exact counts.
- Time-dependent logic uses the injected `Clock`; tests never `Thread.sleep` to wait for an expiry.
- If Docker is not available where you are running, say so instead of skipping or weakening the tests.

## 11. Out of scope

Real payment gateways, refunds, native mobile apps, multiple app instances or horizontal scaling, social login, and seat-map drawing by freehand. Squad booking (friends holding adjacent seats together) is a possible stretch goal only after Phase 7, and only if the team asks for it.

---

## 12. Progress tracker

Claude Code updates this section at the end of every phase.

- [x] Phase 0: Project setup
- [x] Phase 1: Backend foundations
- [ ] Phase 2: Booking engine
- [ ] Phase 3: Real-time layer
- [ ] Phase 4: Frontend core
- [ ] Phase 5: The fun layer
- [ ] Phase 6: Stress Arena
- [ ] Phase 7: Hardening, documentation and demo prep

### Notes and deferred items
- **Phase 0:** Versions chosen: Spring Boot 3.5.16, Maven 3.9.16 (via the wrapper, no global Maven needed), Vite 8, Tailwind CSS 4 (design tokens live in the `@theme` block of `frontend/src/styles/index.css`), React 18.3.1. Only the dependencies needed so far are installed; the rest of Section 2 is added in the phase that uses it.
- **Phase 0:** The CI workflow (`.github/workflows/ci.yml`) has not run yet because the repository has no GitHub remote. Its three steps (`mvnw verify`, `npm ci && npm run build`, `docker build`) all pass locally.
- **Phase 1:** Decisions made where the plan was open:
  - Public registration always creates a `STUDENT`. Other roles are set by an admin (`PUT /api/admin/users/{id}/role`) and take effect at the user's next login.
  - `ADMIN` can use every organizer endpoint for any event. Organizers only for their own (`403` otherwise).
  - `/api/checkin` is open to `GATE_STAFF` and `ADMIN`. The per-event gate assignment check comes with check-in in Phase 2.
  - For seated events, a tier's `capacity` is its seat count and grows as sections are added. Tiers and sections can only change while the event is a draft.
  - Extra endpoints the frontend will need: `GET /api/organizer/events`, `GET /api/organizer/events/{id}`, `GET /api/organizer/events/{id}/seatmap` (draft preview) and `GET /api/organizer/events/{id}/gate-staff`.
  - Extra error codes beyond Section 5.8: `UNAUTHENTICATED`, `BAD_CREDENTIALS`, `FORBIDDEN`, `CSRF_INVALID`, `EMAIL_TAKEN`, `NOT_FOUND`, `INVALID_STATE`, `CONFLICT`, `INTERNAL_ERROR`.
  - Extra DB safety nets beyond the plan: `CHECK (reserved <= capacity)` on `ticket_tiers`, and seat status/hold consistency checks on `seats`. `tickets` also has a `created_at` column.
- **Phase 1:** Entities exist for every table, so Hibernate validates the whole schema. Their behaviour comes in the phase that uses them.
- **Phase 1:** The `csrf()` helper in spring-security-test permanently replaces the CSRF cookie repository in the Spring context it runs in. That's why the real cookie-flow test (`CsrfCookieFlowIntegrationTest`) runs in its own context.
- **Phase 0:** `SpaWebConfig` serves `index.html` for client-side routes such as `/events/x`, so deep links work from the single JAR. `/api/**` paths and missing files still return 404.
