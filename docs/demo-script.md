# FestPass demo script (10 minutes)

A run-through for presenting FestPass: a flash drop with two browsers and a phone, a booking with the ticket reveal, a gate scan, the organizer's mission control, and the Stress Arena as the finale.

## Before the demo (10 minutes ahead)

1. Start Docker Desktop, then from the project folder:

   ```bash
   docker compose up --build
   ```

   The app runs the `demo` profile: it **wipes the database** and recreates the sample data on every start, and the **Midnight Drop** flash drop opens **2 minutes after startup**. Wait for `DEMO MODE: all data was reset` in the log. Restart the `app` container (`docker compose restart app`) to reset the clock again just before you present.

2. Open these windows (all accounts use the password `festpass123`):

   | Window | URL | Log in as |
   |---|---|---|
   | A: Chrome | http://localhost:8080 | `student@festpass.local` |
   | B: Chrome incognito or a second browser | http://localhost:8080 | sign up a new student on stage, or use another sample account |
   | C: a third window | http://localhost:8080 | `organizer@festpass.local` |
   | D: phone | see step 3 | `gate@festpass.local` |
   | Mailpit | http://localhost:8025 | (no login) |

3. **Phone for the gate scanner.** Phone browsers only allow the camera over HTTPS. Either run the HTTPS profile (`scripts/run-https.ps1`, see the README) and open `https://<laptop-ip>:8443` on the phone, or use the scanner's manual code box on the laptop if the phone can't connect. The phone and laptop must be on the same Wi-Fi.

4. Close other heavy apps: the Stress Arena uses all CPU cores for a few seconds.

## The script

### 0:00 – 1:00 · The problem

"When a college fest opens ticket sales, a thousand students hit refresh at the same second. Most ticketing apps either crash or sell the same seat twice. FestPass is built so that can't happen, and we'll prove it live."

Show the **Discover** page in window A: four sample events, categories, search.

### 1:00 – 3:30 · Flash drop with a waiting room

1. In windows A and B, open **Midnight Drop: DJ Night**. Both show the countdown and **Join the queue**. Join in both.
2. Point out the live counters (people waiting, position) updating in both windows without refresh: they come over WebSocket.
3. When the countdown hits zero, the queue admits batches every few seconds. Each window shows **You're in!** with confetti and the live seat map.
4. In window A, pick two seats. In window B, the same seats turn amber within a fraction of a second. Try to take one of them in B: "Someone just grabbed that seat!".

Say: "The seat was taken by one SQL `UPDATE ... WHERE status = 'AVAILABLE'`. PostgreSQL lets exactly one of those win. No Java locks, no race."

### 3:30 – 5:00 · Checkout and the ticket reveal

1. Window A: **Hold seats** → checkout with the hold timer (5 minutes for the sample events) → **Pay** (mock gateway).
2. The ticket flips in with confetti in the tier's colour. Open **My tickets**: QR code, PDF download, calendar file.
3. Switch to Mailpit: the confirmation email with the QR code arrived after the payment committed.

Optional: in window B, hold seats and choose **Simulate a declined card** on the payment page to show a failed payment that can be retried while the hold lasts.

### 5:00 – 6:30 · The gate

1. On the phone (window D): **Gate** → **Midnight Drop** → **Start scanning**.
2. Scan the QR from window A's ticket page (or paste its code). Full-screen **green**, a chime and a buzz: "Valid, Row A, Seat 5".
3. Scan the same ticket again: **red**, "Already scanned at 18:42 by Kabir Gate". Say: "Two gates scanning the same ticket at the same moment still let it in once: it's one conditional `UPDATE`."
4. Optional: change a character of the code and paste it: "Invalid ticket" (QR codes are HMAC-signed).

### 6:30 – 7:30 · Mission control

Window C (organizer): **Organizer** → **Midnight Drop** → **Dashboard**.

- Tickets sold and revenue, the sales-over-time chart, a meter per tier, check-in progress, and the seat heat map of what sold first.
- Book one more seat in window B while C is visible: the numbers move without a refresh.

### 7:30 – 9:30 · Stress Arena finale

1. Log in as `admin@festpass.local` in window C (or any window) → **Arena**.
2. Event **Studio Session: Unplugged** (50 seats), **500 bots**, **Rush same seats** → **Start the fight**.
3. Narrate while it runs (about 5 to 10 seconds): "500 bot accounts just logged in through the real login and CSRF flow. They are all released at the same instant against a hidden copy of the event, every one going for the same front-row seats, through the same HTTP API the browser uses."
4. The seat map fills live. The report shows hold attempts, conflicts, p50 / p95 latency and five integrity checks, all **0**: the **✓ 0 oversold** badge.
5. If there's time: **Open Air: Sunset Sessions**, **1,000 bots**, **GA rush**: exactly 100 Fan Pit tickets allocated, never 101.

Say: "Real events were never touched; that was a sandbox copy. **Reset arena** removes it and all the bots."

### 9:30 – 10:00 · Wrap up

"One Spring Boot app, one PostgreSQL database. Every guarantee (no seat sold twice, no tier over capacity, one check-in per ticket, safe retries, no ticket from an expired hold) is enforced by the database and proven by integration tests against real PostgreSQL, plus the arena you just saw." Point to `docs/architecture.md` for the diagrams.

## If something goes wrong

| Problem | Fix |
|---|---|
| The flash drop already opened before you got there | `docker compose restart app` (resets the data and the 2-minute timer), then log in again |
| Phone can't reach the laptop | Same Wi-Fi? Windows Firewall may block Java: allow it on private networks. Fall back to the manual code box |
| Phone camera doesn't start | The page must be HTTPS (port 8443 with `run-https.ps1`). Accept the certificate warning first |
| No email in Mailpit | Check http://localhost:8025 is the Docker Mailpit; emails go out a moment after the payment |
| Arena says "A run is already in progress" | Wait for it to finish (the page shows its progress), then start again |
| The arena is slow | Close other apps, or use fewer bots (200); the guarantees don't depend on the number |
