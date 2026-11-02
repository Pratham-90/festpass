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

### Idempotency keys

`POST /api/orders` and `POST /api/payments/mock/{orderId}` need an `Idempotency-Key` header: 8 to 100 letters, digits or `_ . : -`, for example a UUID. Make **one key per attempt** and reuse it when retrying that attempt, such as after a timeout or a double click. A retry with the same key returns the first result. It never creates a second order or a second set of tickets.

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
| `SEAT_UNAVAILABLE` | 409 | One or more seats were just taken (`unavailableSeatIds`) |
| `SOLD_OUT` | 409 | Not enough GA tickets left (`tierId`, `remaining`) |
| `LIMIT_EXCEEDED` | 409 | Over the per-user ticket limit (`limit`, `alreadyHeldOrOwned`) |
| `HOLD_EXPIRED` | 409 | The hold ran out before the order or payment |
| `SALE_NOT_OPEN` | 409 | Outside the sale window (`saleOpensAt`, `saleClosesAt`) |
| `PAYMENT_DECLINED` | 402 | The mock payment was declined; the order stays `PENDING` |
| `NOT_ADMITTED` | 403 | Flash drop: no valid `X-Admission-Token` (join the waiting room) |
| `RATE_LIMITED` | 429 | Too many hold requests; wait `Retry-After` seconds |
| `INTERNAL_ERROR` | 500 | Unexpected server error (details are only in the server log) |

### Roles

| Area | Who |
|---|---|
| `GET /api/events/**`, `/api/health`, register, login | Everyone |
| `/api/organizer/**` | `ORGANIZER` (own events only) and `ADMIN` (any event) |
| `/api/admin/**` | `ADMIN` |
| `/api/checkin` | `GATE_STAFF` (assigned events only) and `ADMIN` |
| `/api/events/{id}/checkin/stats` | Assigned `GATE_STAFF`, the event's `ORGANIZER`, `ADMIN` |
| `/api/events/{id}/queue/**` | Any logged-in user |
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

## Booking

### `POST /api/events/{id}/holds`

Holds seats or GA tickets for the logged-in user for the event's `holdMinutes` (default 5). It's all or nothing: if any requested seat is gone, nothing is held.

Seated events:

```json
{ "seatIds": [101, 102] }
```

General admission:

```json
{ "tierId": 4, "quantity": 2 }
```

Response `201`:

```json
{
  "holdId": "1b5c...", "eventId": 1, "status": "ACTIVE", "expiresAt": "2026-10-20T13:05:00Z",
  "quantity": 2, "tierId": null,
  "seats": [ { "id": 101, "section": "Front", "row": "A", "number": 1, "tierId": 1 } ]
}
```

Errors:

- `SALE_NOT_OPEN`
- `NOT_ADMITTED`: a flash drop without a valid `X-Admission-Token` header (see "Waiting room").
- `SEAT_UNAVAILABLE`
- `SOLD_OUT`
- `LIMIT_EXCEEDED`: tickets held plus tickets owned may not exceed `maxTicketsPerUser`.
- `RATE_LIMITED`: each user gets 5 requests per second and 30 per minute, counting every hold request, including invalid ones.
- `VALIDATION_FAILED`

A seat whose hold has expired counts as free right away, even before the cleanup job (every 5 s) runs.

### `DELETE /api/holds/{id}`

Cancels your hold and frees its seats or tickets. Response `204`. Cancelling twice is harmless. Someone else's hold gives `404`.

### `POST /api/orders`

Header: `Idempotency-Key`. Body:

```json
{ "holdId": "1b5c..." }
```

Creates a `PENDING` order for an active hold. The response is `201` for a new order, or `200` with the existing order when retried. A hold has at most one order, so a second request for the same hold returns that order even with a new key. Errors:

- `HOLD_EXPIRED`
- `NOT_FOUND`: not your hold.
- `CONFLICT`: the key was already used for a different hold.

Response (also returned by `GET /api/orders/{id}` and a successful payment):

```json
{
  "id": 12, "eventId": 1, "eventSlug": "rhythm-night", "eventTitle": "Rhythm Night", "holdId": "1b5c...",
  "status": "PENDING", "amountPaise": 159800, "createdAt": "...", "paidAt": null,
  "holdExpiresAt": "2026-10-20T13:05:00Z",
  "items": [ { "tierId": 1, "tierName": "VIP", "unitPricePaise": 79900, "quantity": 1,
               "seat": { "id": 101, "section": "Front", "row": "A", "number": 1, "tierId": 1 } } ],
  "ticketIds": []
}
```

GA orders have one item with `quantity` and `seat: null`. Once the hold has expired or been cancelled, a seated order may list no items.

### `GET /api/orders/{id}`

Your order. Someone else's gives `404`.

---

## Mock payments

### `POST /api/payments/mock/{orderId}`

Header: `Idempotency-Key`. Body: `{ "outcome": "SUCCESS" }` or `{ "outcome": "FAIL" }`. The fake gateway waits 0.5 to 2 s before answering.

| Result | Status | Body |
|---|---|---|
| Paid | `200` | The order with `status: "PAID"`, `paidAt` and `ticketIds` |
| Declined (`FAIL`) | `402 PAYMENT_DECLINED` | `orderId`, `orderStatus: "PENDING"`. Retry with a new key while the hold lasts |
| Hold ran out | `409 HOLD_EXPIRED` | `orderId`, `orderStatus: "FAILED"`. The hold's seats are freed |

Paying is one transaction with the order row locked. A retry with the same key gets the same answer. Paying an order that is already `PAID`, even with a new key, returns it unchanged. The confirmation email is sent after the payment commits. It contains a PDF with every ticket's QR code, plus an `.ics` calendar file, and is caught by Mailpit in development.

---

## Tickets

All ticket endpoints only show your own tickets. Anyone else's give `404`.

### `GET /api/me/tickets`

Your tickets, soonest event first:

```json
[{
  "id": "9b88d636-...", "orderId": 12, "eventId": 1, "eventSlug": "rhythm-night-saturday-showcase",
  "eventTitle": "Rhythm Night", "venueName": "Main Auditorium, TIET", "startsAt": "...", "endsAt": "...",
  "tierId": 1, "tierName": "VIP", "tierColor": "#ff3d81", "pricePaise": 79900,
  "section": "Front", "row": "A", "seatNumber": 1, "seatLabel": "Front · Row A · Seat 1",
  "holderName": "Aarav Student", "code": "9b88d636-....1.mF3k...", "checkedInAt": null, "issuedAt": "..."
}]
```

`code` is the text in the QR code: `ticketId.eventId.signature`, where the signature is an HMAC-SHA256 with the server's `QR_SECRET`. The seat fields are `null` and `seatLabel` is `"General admission"` for GA tickets.

### `GET /api/tickets/{id}`

One ticket, same shape as above.

### `GET /api/tickets/{id}/qr.png`

The QR code as a 480×480 PNG.

### `GET /api/tickets/{id}/pdf`

The ticket as a PDF download (A6, with the QR code).

### `GET /api/tickets/{id}/calendar.ics`

An iCalendar file for the event.

---

## Check-in

### `POST /api/checkin`

```json
{ "eventId": 1, "code": "9b88d636-....1.mF3k..." }
```

For `GATE_STAFF` assigned to the event, or an `ADMIN`. Always `200`; the result is in `result`:

| `result` | Meaning |
|---|---|
| `VALID` | First scan; the ticket is now checked in |
| `ALREADY_USED` | Scanned before; `checkedInAt` and `checkedInBy` say when and by whom |
| `INVALID` | Not a FestPass code, a forged or changed signature, or an unknown ticket |
| `WRONG_EVENT` | A real ticket for a different event (`message` names it) |

```json
{
  "result": "ALREADY_USED", "message": "Already scanned", "ticketId": "9b88d636-...",
  "holderName": "Aarav Student", "tierName": "VIP", "seatLabel": "Front · Row A · Seat 1",
  "checkedInAt": "2026-10-27T13:42:00Z", "checkedInBy": "Kabir Gate"
}
```

Check-in is a single conditional update, so a ticket is let in only once even if two gates scan it at the same moment. Every scan is logged.

### `GET /api/events/{id}/checkin/stats`

For assigned gate staff, the event's organizer, or an admin:

```json
{ "eventId": 1, "checkedIn": 120, "total": 202, "lastScan": { "result": "VALID", "scannedAt": "..." } }
```

---

## Waiting room (flash drops)

Flash-drop events (`flashDrop: true`) only accept holds from people let in through the waiting room. Once the sale opens, the server admits the queue in order, in batches: 50 people every 5 seconds by default. Each admitted person gets an **admission token**, valid for 10 minutes. It must be sent with every hold for that event as the `X-Admission-Token` header. Without a valid token, a hold gets `403 NOT_ADMITTED`. The token is tied to the user and the event, so it can't be shared.

Both endpoints need a login.

### `POST /api/events/{id}/queue`

Joins the queue. Joining again keeps your place. After an admission expires, joining again puts you at the back. Response `200`:

```json
{
  "eventId": 3, "status": "WAITING", "position": 1284, "waiting": 1500, "admittedSoFar": 250,
  "opensAt": "2026-10-21T18:30:00Z", "admissionToken": null, "admissionExpiresAt": null
}
```

Errors:

- `409 INVALID_STATE`: the event isn't a flash drop.
- `409 SALE_NOT_OPEN`: the sale has closed.

### `GET /api/events/{id}/queue/me`

Same shape. `status` is one of:

- `NOT_IN_QUEUE`
- `WAITING`: `position` 1 means you're next.
- `ADMITTED`: `admissionToken` and `admissionExpiresAt` are set.
- `EXPIRED`

The token is also pushed to `/user/queue/admission`, so polling is only a fallback.

---

## Real-time (WebSocket / STOMP)

Connect with STOMP over a plain WebSocket at **`/ws`**, for example with `@stomp/stompjs` and `brokerURL: ws(s)://<host>/ws`. In development the Vite server proxies `/ws` to the backend.

- **Who you are.** The browser's normal session cookie is sent with the WebSocket handshake, so a logged-in user is the STOMP user. Visitors who aren't logged in can connect too and watch public topics.
- **Clients only listen.** `SEND` frames are refused, so nobody can fake a seat update. A refused frame gets a STOMP `ERROR` frame (its `message` header says why) and the connection closes.
- **Heartbeats** are every 10 s in both directions.
- **Only after commit.** Every message describes data the database has already committed. A rolled-back hold is never announced.

| Destination | Who may subscribe | Payload |
|---|---|---|
| `/topic/events/{id}/seats` | anyone | Seat changes, batched at most every 100 ms |
| `/topic/events/{id}/stats` | anyone | Viewers, remaining tickets, sold in the last minute |
| `/topic/events/{id}/queue` | anyone | Waiting-room counters |
| `/topic/events/{id}/checkins` | `GATE_STAFF`, `ORGANIZER`, `ADMIN` | Check-in progress |
| `/topic/arena/{runId}` | `ADMIN` | Stress Arena progress (Phase 6) |
| `/user/queue/holds` | logged-in users | Your holds ending |
| `/user/queue/admission` | logged-in users | Your admission token |

### `/topic/events/{id}/seats`

```json
[ { "seatId": 101, "status": "HELD" }, { "seatId": 102, "status": "AVAILABLE" } ]
```

`status` is `AVAILABLE`, `HELD` or `BOOKED`. Within one batch, a seat that changed twice appears once, with its latest status. Load `GET /api/events/{id}/seatmap` first, then apply the batches as they arrive.

### `/topic/events/{id}/stats`

```json
{ "eventId": 1, "viewers": 42, "remainingByTier": { "1": 29, "2": 90, "3": 80 }, "soldLastMinute": 7 }
```

`viewers` counts connections subscribed to any of the event's topics. Stats are sent at most every 500 ms when something changes, and every 5 s anyway while anyone is watching. `remainingByTier` is keyed by tier id.

### `/topic/events/{id}/queue`

```json
{ "eventId": 3, "waiting": 1284, "admittedSoFar": 250, "opensAt": "2026-10-21T18:30:00Z" }
```

### `/topic/events/{id}/checkins`

```json
{ "eventId": 1, "checkedIn": 120, "total": 202,
  "lastScan": { "result": "VALID", "tierName": "VIP", "seatLabel": "Front · Row A · Seat 1", "at": "..." } }
```

### `/user/queue/holds`

```json
{ "holdId": "1b5c...", "eventId": 1, "status": "EXPIRED" }
```

`status` is `EXPIRED`, `RELEASED` or `CONVERTED`. Use it to end the checkout timer when a hold runs out.

### `/user/queue/admission`

```json
{ "eventId": 3, "admissionToken": "17.3.1792540800.Xy...", "expiresAt": "2026-10-21T18:40:00Z" }
```

---

## Coming in later phases

- `GET /api/me/passport`: Phase 5
- `GET /api/organizer/events/{id}/dashboard`: Phase 5
- Stress Arena (`/api/admin/arena/**`, `/topic/arena/{runId}`): Phase 6
