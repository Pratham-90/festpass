# FestPass

Flash-sale event ticketing for college fests and society events: live seat maps, flash drops with a waiting room, QR tickets, a gate scanner and a Stress Arena that proves zero overselling.

Course project for **UCS553 Enterprise Web Application** (TIET) by Pratham Arora, Krishna Pandey and Nipun Behl.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL 16, Flyway, Spring Mail
- **Frontend:** React 18, Vite, Tailwind CSS, React Router, TanStack Query, Framer Motion, STOMP over WebSocket, Recharts, html5-qrcode, canvas-confetti
- **Infra:** Docker Compose (PostgreSQL, Mailpit, app), GitHub Actions

See [PLAN.md](PLAN.md) for the full design and build plan.

## Prerequisites

- JDK 21
- Node.js 22 or newer (Vite needs at least 20.19)
- Docker Desktop (for PostgreSQL, Mailpit and the backend tests)

Maven does not need to be installed; the repository includes the Maven wrapper (`mvnw` / `mvnw.cmd`).

## Run locally (development)

1. Start the database and the mail catcher:

   ```bash
   docker compose up -d postgres mailpit
   ```

2. Start the backend (API on http://localhost:8080):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell or cmd, use `mvnw.cmd spring-boot:run`.

   `spring-boot:run` uses the `dev` profile, which creates sample data on first start (see below).

3. In a second terminal, start the frontend (UI on http://localhost:5173):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

Open http://localhost:5173 and log in with one of the dev accounts below, or sign up.

Emails sent by the app are caught by Mailpit at http://localhost:8025.

### Dev accounts and sample data

The `dev` profile creates these accounts. They all use the password `festpass123`. They're for local development only.

| Email | Role |
|---|---|
| `student@festpass.local` | Student |
| `organizer@festpass.local` | Organizer |
| `gate@festpass.local` | Gate staff |
| `admin@festpass.local` | Admin |

It also creates three published events. On every start it moves them back into the future if their dates have passed:

- **Rhythm Night: Saturday Showcase**: a seated auditorium show with 202 seats in 3 tiers.
- **Open Air: Sunset Sessions**: general admission with 2 tiers.
- **Midnight Drop: DJ Night**: a seated flash drop. Its sale opens 3 minutes after every backend start.

To start again from an empty database:

```bash
docker compose down -v
```

## Run everything in Docker

Builds the React app, packs it into the Spring Boot JAR and starts it with PostgreSQL and Mailpit:

```bash
docker compose up --build
```

Open http://localhost:8080 (the UI and the API are served from the same JAR).

## Tests

```bash
cd backend
./mvnw verify
```

The integration tests start a real PostgreSQL 16 with Testcontainers, so Docker must be running.

## Configuration

The backend reads these environment variables (defaults suit the local Docker Compose setup):

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/festpass` |
| `DB_USERNAME` / `DB_PASSWORD` | `festpass` / `festpass` |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `QR_SECRET` | a dev-only value. **Set your own (at least 16 characters) anywhere shared**, since it signs every ticket's QR code |
| `PORT` | `8080` |

## Gate scanner on a phone

Phone browsers only allow the camera on HTTPS (or `localhost`), so plain `http://<laptop-ip>:8080` shows the scanner without a camera. To scan with a real phone, run FestPass over HTTPS with a self-signed certificate:

1. Start the database: `docker compose up -d postgres mailpit`
2. From the project folder, in PowerShell:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/run-https.ps1
   ```

   The first run creates `backend/dev-https.p12`, a certificate for `localhost` and the laptop's current Wi-Fi address. It then builds the React app into the server and starts it on port 8443 with the `dev,https` profiles. If the laptop's address changes, delete that file and run the script again.
3. The script prints an address like `https://192.168.1.20:8443`. Open it on a phone on the **same Wi-Fi**. The browser warns that the certificate isn't trusted, because it was made on your laptop: choose "Advanced", then "Proceed".
4. Log in as gate staff, open **Gate**, pick the event, tap **Start scanning** and allow the camera.

A valid ticket flashes the screen green with a chime and a short buzz. A used, forged or wrong-event ticket flashes red with the reason. Without a camera, type or paste the ticket's code into the box at the bottom.

Windows Firewall may ask to allow Java on private networks the first time; allow it, or the phone can't connect.

## Frontend at a glance

| Page | Path | Who |
|---|---|---|
| Discover (search, categories) | `/` | everyone |
| Event (live seat map or ticket picker, waiting room for flash drops) | `/events/:slug` | everyone; booking needs a login |
| Checkout (hold timer) → mock payment → confirmation | `/checkout/:id`, `/pay/:id`, `/orders/:id/confirmed` | logged in |
| Ticket wallet and ticket (QR, PDF, calendar) | `/tickets`, `/tickets/:id` | logged in |
| Fest Passport (stamps and milestones) | `/passport` | logged in |
| Gate: pick an event, then the full-screen scanner | `/gate`, `/gate/:eventId` | gate staff and admins |
| Organizer: events, new event, editor (tiers, section generator with live preview, gate staff, publish) | `/organizer/...` | organizers and admins |
| Organizer mission control (live sales chart, tier fill, seat heat map, check-ins) | `/organizer/events/:id/dashboard` | organizers and admins |
| Users and roles | `/admin/users` | admins |

Code lives in `frontend/src`: `api/` (fetch wrapper, query hooks, error messages), `realtime/useStomp.js` (the one shared WebSocket connection), `components/` and `pages/`.

## Project layout

```
backend/     Spring Boot app (package per feature under com.festpass)
frontend/    React + Vite app
docs/        API reference (docs/api.md), architecture and demo script
load-tests/  JMeter plan (Phase 7)
```
