# FestPass REST API

All endpoints are under `/api` and use JSON. Times are ISO-8601 UTC strings (`2026-10-27T13:30:00Z`). Money is in **paise** (`49900` = ₹499).

## Conventions

### Authentication

Login is session-based. A successful `POST /api/auth/login` or `POST /api/auth/register` sets an HTTP-only `JSESSIONID` cookie, which the browser sends with every request.

### CSRF

Every request that changes something (`POST`, `PUT`, `DELETE`), including login and register, needs a CSRF token:

1. Any response sets a readable `XSRF-TOKEN` cookie.
2. Send its value back in the `X-XSRF-TOKEN` header.

Login issues a fresh token straight away, in the login response itself. Always read the cookie just before each request rather than caching it. A missing or wrong token gives `403` with code `CSRF_INVALID`.

### Errors

Every error is an RFC 7807 `ProblemDetail` (`Content-Type: application/problem+json`) with a stable `code`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Some fields are invalid",
  "code": "VALIDATION_FAILED",
  "errors": { "email": "must be a well-formed email address" }
}
```

`errors` (field → message) is present for `VALIDATION_FAILED`.

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A field is missing or invalid (see `errors`) |
| `UNAUTHENTICATED` | 401 | Not logged in |
| `BAD_CREDENTIALS` | 401 | Wrong email or password |
| `FORBIDDEN` | 403 | Logged in, but the role or ownership doesn't allow it |
| `CSRF_INVALID` | 403 | Missing or stale CSRF token |
| `NOT_FOUND` | 404 | No such resource (or not visible to you) |
| `EMAIL_TAKEN` | 409 | An account with this email exists |
| `INVALID_STATE` | 409 | Not allowed in the current state (e.g. publishing an event without tiers) |
| `CONFLICT` | 409 | The request conflicts with existing data |
| `SEAT_UNAVAILABLE`, `SOLD_OUT`, `LIMIT_EXCEEDED`, `HOLD_EXPIRED`, `SALE_NOT_OPEN` | 409 | Booking errors (Phase 2) |
| `NOT_ADMITTED` | 403 | Flash drop: no admission token yet (Phase 3) |
| `RATE_LIMITED` | 429 | Too many requests (Phase 2) |
| `INTERNAL_ERROR` | 500 | Unexpected server error (details are only in the server log) |

### Roles

| Area | Who |
|---|---|
| `GET /api/events/**`, `/api/health`, register, login | Everyone |
| `/api/organizer/**` | `ORGANIZER` (own events only) and `ADMIN` (any event) |
| `/api/admin/**` | `ADMIN` |
| `/api/checkin/**` | `GATE_STAFF` and `ADMIN` |
| Any other `/api/**` | Any logged-in user |

Registration always creates a `STUDENT`. Other roles are given by an admin. A role change takes effect at the user's next login.

---

## Health

### `GET /api/health`

```json
{ "status": "UP", "database": "UP", "time": "2026-10-20T06:30:00Z" }
```

`status` is `DEGRADED` and `database` is `DOWN` when PostgreSQL cannot be reached.

---

## Auth

### `POST /api/auth/register`

Creates a `STUDENT` account and logs it in. Emails are case-insensitive.

Request:

```json
{ "name": "Asha Verma", "email": "asha@example.com", "password": "at-least-8-chars" }
```

Response `201`: the user (same shape as `GET /api/auth/me`). Errors: `VALIDATION_FAILED`, `EMAIL_TAKEN`.

### `POST /api/auth/login`

```json
{ "email": "asha@example.com", "password": "at-least-8-chars" }
```

Response `200`: the user. Error: `BAD_CREDENTIALS`.

### `POST /api/auth/logout`

Response `204`. Ends the session.

### `GET /api/auth/me`

Response `200`:

```json
{ "id": 7, "name": "Asha Verma", "email": "asha@example.com", "role": "STUDENT" }
```

Error: `UNAUTHENTICATED` when not logged in.

---

## Public events

### `GET /api/events?q=&category=&upcoming=true`

Lists published events, soonest first.

| Parameter | Default | Meaning |
|---|---|---|
| `q` | | Case-insensitive match on title, venue or description |
| `category` | | `MUSIC`, `DANCE`, `TECH`, `COMEDY`, `DRAMA`, `SPORTS`, `WORKSHOP` or `OTHER` |
| `upcoming` | `true` | Only events that have not ended yet |

Response `200`, an array of summaries:

```json
[{
  "id": 1, "slug": "rhythm-night-saturday-showcase", "title": "Rhythm Night: Saturday Showcase",
  "category": "DANCE", "bannerUrl": null, "venueName": "Main Auditorium, TIET",
  "startsAt": "2026-10-27T07:55:00Z", "endsAt": "2026-10-27T10:55:00Z",
  "saleOpensAt": "2026-10-19T07:55:00Z", "saleClosesAt": "2026-10-27T07:55:00Z",
  "status": "PUBLISHED", "seatingMode": "SEATED", "flashDrop": false,
  "fromPricePaise": 24900, "accentColor": "#ff3d81"
}]
```

`accentColor` is the colour of the event's first tier.

### `GET /api/events/{slug}`

Response `200`, the event detail:

```json
{
  "id": 1, "slug": "rhythm-night-saturday-showcase", "title": "Rhythm Night: Saturday Showcase",
  "description": "...", "category": "DANCE", "bannerUrl": null, "venueName": "Main Auditorium, TIET",
  "startsAt": "...", "endsAt": "...", "saleOpensAt": "...", "saleClosesAt": "...",
  "status": "PUBLISHED", "seatingMode": "SEATED", "holdMinutes": 5, "maxTicketsPerUser": 4,
  "flashDrop": false, "organizerName": "Riya Organizer", "accentColor": "#ff3d81",
  "tiers": [
    { "id": 1, "name": "VIP", "pricePaise": 79900, "capacity": 32, "remaining": 32, "colorHex": "#ff3d81" }
  ]
}
```

Drafts, cancelled events and Stress Arena sandbox events return `404`.

### `GET /api/events/{id}/seatmap`

Everything needed to draw the seat map. Coordinates are seat centres in SVG units. Each section is centred on `x = 0` and sections are stacked top to bottom. Fit the map to the screen using `bounds`.

```json
{
  "eventId": 1,
  "seatingMode": "SEATED",
  "tiers": [ { "id": 1, "name": "VIP", "pricePaise": 79900, "capacity": 32, "remaining": 32, "colorHex": "#ff3d81" } ],
  "sections": [ { "name": "Front", "tierId": 1, "seatCount": 32, "minX": -238, "minY": 0, "maxX": 238, "maxY": 32 } ],
  "seats": [
    { "id": 1, "section": "Front", "row": "A", "number": 1, "x": -238, "y": 0,
      "status": "AVAILABLE", "tierId": 1, "color": "#ff3d81" }
  ],
  "bounds": { "minX": -294, "minY": 0, "maxX": 294, "maxY": 384 }
}
```

`status` is `AVAILABLE`, `HELD` or `BOOKED`. A seat whose hold has expired is reported as `AVAILABLE` even before the cleanup job runs. For `GENERAL` events, `sections` and `seats` are empty, `bounds` is `null`, and `tiers[].remaining` is what's left to sell.

---

## Organizer

All organizer endpoints need the `ORGANIZER` role (own events) or `ADMIN` (any event). Someone else's event gives `403 FORBIDDEN`.

### `GET /api/organizer/events`

The organizer's events (an admin sees all), newest first. Same summary shape as the public list, including drafts.

### `GET /api/organizer/events/{id}`

The event detail (same shape as the public one), including drafts.

### `GET /api/organizer/events/{id}/seatmap`

The seat map (same shape as the public one), including drafts. Use it to preview a layout while designing it.

### `POST /api/organizer/events`

Creates a `DRAFT` event. The slug is generated from the title (`-2`, `-3`, ... if taken).

```json
{
  "title": "Rhythm Night", "description": "Optional, up to 5000 chars", "category": "DANCE",
  "bannerUrl": "https://example.com/banner.jpg", "venueName": "Main Auditorium",
  "startsAt": "2026-11-01T13:30:00Z", "endsAt": "2026-11-01T16:30:00Z",
  "saleOpensAt": "2026-10-21T04:30:00Z", "saleClosesAt": "2026-11-01T13:30:00Z",
  "seatingMode": "SEATED", "holdMinutes": 5, "maxTicketsPerUser": 4, "flashDrop": false
}
```

`bannerUrl`, `description`, `holdMinutes` (1–30, default 5), `maxTicketsPerUser` (1–20, default 4) and `flashDrop` (default `false`) are optional. The rules:

- The start must be in the future.
- `endsAt` must be after `startsAt`.
- `saleClosesAt` must be after `saleOpensAt` and not after `endsAt`.

Response `201`: the event detail.

### `PUT /api/organizer/events/{id}`

Same body as create. It replaces the event's details; the slug never changes. The seating mode cannot change once tiers exist (`409 INVALID_STATE`). Response `200`: the event detail.

### `POST /api/organizer/events/{id}/publish`

Publishes a draft. It needs at least one tier, and every tier needs capacity: seated tiers need seats, so add a section for each. The sale must not have closed already. Response `200`: the event detail. Otherwise `409 INVALID_STATE` with the reason.

### `POST /api/organizer/events/{id}/tiers`

Draft events only.

```json
{ "name": "VIP", "pricePaise": 79900, "capacity": 100, "colorHex": "#ff3d81" }
```

- `capacity` is **required for `GENERAL`** events and **must be left out for `SEATED`** events, where the seats decide it.
- `colorHex` is optional; a palette colour is picked if it's left out.
- Tier names are unique per event, ignoring case.

Response `201`:

```json
{ "id": 4, "name": "VIP", "pricePaise": 79900, "capacity": 100, "remaining": 100, "colorHex": "#ff3d81" }
```

### `POST /api/organizer/events/{id}/sections`

Seated draft events only. Generates `rows × seatsPerRow` seats for a tier, placed below any existing sections.

```json
{ "name": "Front", "tierId": 4, "rows": 2, "seatsPerRow": 16, "aisleAfter": [4, 12] }
```

- `rows` is 1–52, labelled `A`…`Z`, then `AA`….
- `seatsPerRow` is 1–60, and seats are numbered from 1.
- `aisleAfter` lists the seat numbers followed by an aisle. `[4, 12]` gives 4 seats | 8 seats | 4 seats.
- Section names are unique per event.
- The tier's capacity grows by the number of seats created.

Response `201`:

```json
{ "name": "Front", "tierId": 4, "seatCount": 32, "minX": -238, "minY": 0, "maxX": 238, "maxY": 32 }
```

### `GET /api/organizer/events/{id}/gate-staff`

The gate staff assigned to the event: `[{ "userId": 3, "name": "Kabir Gate", "email": "gate@festpass.local" }]`.

### `POST /api/organizer/events/{id}/gate-staff`

```json
{ "email": "gate@festpass.local" }
```

Assigns a `GATE_STAFF` user to the event. Assigning the same person twice does nothing. Response `200`: the assigned user. An unknown email, or a user who isn't gate staff, gives `400 VALIDATION_FAILED`.

### `GET /api/organizer/events/{id}/dashboard`

Phase 5.

---

## Admin

### `GET /api/admin/users?q=`

All users, newest first. `q` filters by name or email.

```json
[{ "id": 7, "name": "Asha Verma", "email": "asha@example.com", "role": "STUDENT", "createdAt": "..." }]
```

### `PUT /api/admin/users/{id}/role`

```json
{ "role": "ORGANIZER" }
```

Response `200`: the updated user. Admins cannot change their own role (`409 INVALID_STATE`), so there is always at least one admin.

---

## Coming in later phases

- Booking (holds, orders, mock payments): Phase 2
- Tickets: Phase 2
- Check-in: Phase 2
- Waiting room: Phase 3
- Stress Arena: Phase 6
