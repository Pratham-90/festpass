# Load test: the hold endpoint

`festpass-holds.jmx` is an [Apache JMeter](https://jmeter.apache.org/) 5.6 plan for `POST /api/events/{id}/holds`, the busiest endpoint during a flash sale.

What it does:

1. A setup thread looks up the event (default: the sample **Open Air: Sunset Sessions**) and its first general-admission tier.
2. Each virtual fan gets the CSRF cookie, signs up with its own account (`load-<uuid>@loadtest.festpass.local`), then loops until the time is up: **hold 1 ticket → release it → wait 2.5 s**.
3. Releasing gives the ticket back, so the tier never sells out and every request does real work: the conditional `UPDATE` on `ticket_tiers`, the per-user limit and the after-commit WebSocket messages.
4. A hold must answer `201` (held) or `409` (sold out at that instant). Anything else counts as an error in the report.

The 2.5 s think time keeps each fan under the per-user rate limit (5 holds a second, 30 a minute). With the defaults, 200 fans make about 80 holds and 80 releases a second.

## Run it

1. Install JMeter 5.6 (needs Java 8+; the project's JDK 21 is fine) and put its `bin` folder on your `PATH`.
2. Start FestPass with the sample data, for example `docker compose up --build`, or the backend with the `dev` profile.
3. From the project folder, run JMeter without its GUI and write an HTML report:

   ```bash
   jmeter -n -t load-tests/festpass-holds.jmx \
     -JCookieManager.save.cookies=true \
     -Jusers=200 -Jrampup=20 -Jduration=120 \
     -l load-tests/results/holds.jtl \
     -e -o load-tests/results/report
   ```

   On Windows use `jmeter.bat` and put the command on one line.

4. Open `load-tests/results/report/index.html`. The **Statistics** table has the throughput, error rate and the 90th/95th/99th percentile response times for `POST hold`.

`-JCookieManager.save.cookies=true` is required: it lets the plan read the `XSRF-TOKEN` cookie and send it back as the `X-XSRF-TOKEN` header. Without it every write fails with `403 CSRF_INVALID`. `-o` needs an empty or missing folder, so delete `load-tests/results` between runs.

## Settings

| Property | Default | Meaning |
|---|---|---|
| `host` / `port` | `127.0.0.1` / `8080` | Where FestPass runs |
| `eventSlug` | `open-air-sunset-sessions` | A published general-admission event |
| `users` | `200` | Virtual fans |
| `rampup` | `20` | Seconds to start them all |
| `duration` | `120` | Seconds to run |
| `thinkMs` | `2500` | Pause after each hold and release, per fan |

Each run creates one account per fan. They are ordinary student accounts; with the `demo` profile they disappear at the next restart, and `docker compose down -v` removes them too.

## Notes

- This plan measures throughput and latency. The **Stress Arena** (admin → Arena) is the correctness test: it fires up to 2,000 bots at the same instant at one seat map and checks in SQL that nothing was oversold.
- The plan has not been run as part of the project's automated checks (CI runs the JUnit suite only). Run it on the machine you present from, so the numbers match the hardware you talk about.
