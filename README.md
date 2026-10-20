# FestPass

Flash-sale event ticketing for college fests and society events: live seat maps, flash drops with a waiting room, QR tickets, a gate scanner and a Stress Arena that proves zero overselling.

Course project for **UCS553 Enterprise Web Application** (TIET) by Pratham Arora, Krishna Pandey and Nipun Behl.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL 16, Flyway, Spring Mail
- **Frontend:** React 18, Vite, Tailwind CSS
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

Open http://localhost:5173. The page shows "API UP" when the frontend can reach `/api/health` through the Vite proxy.

Emails sent by the app are caught by Mailpit at http://localhost:8025.

### Dev accounts and sample data

The `dev` profile creates these accounts. They all use the password `festpass123`. They're for local development only.

| Email | Role |
|---|---|
| `student@festpass.local` | Student |
| `organizer@festpass.local` | Organizer |
| `gate@festpass.local` | Gate staff |
| `admin@festpass.local` | Admin |

It also creates three published events:

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
| `PORT` | `8080` |

## Project layout

```
backend/     Spring Boot app (package per feature under com.festpass)
frontend/    React + Vite app
docs/        API reference (docs/api.md), architecture and demo script
load-tests/  JMeter plan (Phase 7)
```
