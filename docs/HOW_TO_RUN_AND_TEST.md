# HOW TO RUN & TEST — HMIP

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

Step-by-step: run the app, log in as Admin and as a plain User, exercise every major feature,
and inspect both databases directly. No live secrets are in this file — see
**`docs/CREDENTIALS.local.md`** (git-ignored, created locally, not in this repo's history) for
actual usernames/passwords. If that file doesn't exist on your machine, ask your assistant —
the values are recorded there from setup.

---

## 1. PREREQUISITES

| Tool | Why | Check |
|---|---|---|
| Java 21 | runtime | `java -version` |
| Gradle | build (this repo has no `./gradlew` wrapper — uses whatever `gradle` is on your PATH) | `gradle -v` |
| Docker Desktop | MySQL, ClickHouse, Redis, Kafka | `docker info` |

---

## 2. ONE-TIME SETUP

1. Real Alpha Vantage key: already stored at `src/main/resources/application-secrets.yml`
   (git-ignored — see `docs/CREDENTIALS.local.md` if you need the raw value again).
2. Nothing else — `docker-compose.yml` and `application-docker.yml` need no per-machine edits
   unless ports 3307/6380/8123/9000/9092 are already taken on your machine (see the comments
   at the top of `docker-compose.yml` if you need to change them).

---

## 3. STARTING EVERYTHING

```bash
# Terminal 1 — infrastructure (MySQL, ClickHouse, Redis, Kafka)
docker compose up -d --wait

# Terminal 2 — the application
SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```

Wait for `Started HFTApplication in N seconds` in Terminal 2's output. Then:

```bash
curl -s http://localhost:8080/actuator/health
```
should show `"status":"UP"` with `db`, `redis` all `UP`.

Open **http://localhost:8080/** in a browser — you should land on the login page.

**Profiles explained:** `dev` = base config (H2 fallback settings, etc.); `docker` = layers
real MySQL/ClickHouse/Redis/Kafka on top (see `docs/STAGE9_INFRASTRUCTURE.md`); `secrets` =
loads the real Alpha Vantage key from the git-ignored file. Drop `docker` to run against H2
in-memory instead (faster, zero external deps, but data doesn't persist across restarts and
the test account's password regenerates every boot).

---

## 4. STOPPING EVERYTHING

```bash
# Ctrl-C in Terminal 2 to stop the app

docker compose down          # stops MySQL/ClickHouse/Redis/Kafka, KEEPS their data
docker compose down -v       # same, but also WIPES all data (use only if you want a clean slate)
```

---

## 5. CREDENTIALS AT A GLANCE

Full values in `docs/CREDENTIALS.local.md`. What each one is for:

| Credential | Purpose |
|---|---|
| App Admin (PTD2315) | Log into the web UI with both USER + ADMIN roles |
| App User (testuser01) | Log into the web UI with USER role only — for testing that admin features are actually blocked |
| MySQL app user (`hft_app`) | What the application itself connects as — scoped to one database |
| MySQL root | Full admin: every database, every privilege, user management |
| ClickHouse `default` | Full admin — ClickHouse's `default` user isn't privilege-restricted in this setup |
| Redis | Needed for any `redis-cli` connection |
| Alpha Vantage | External market-data API key (real, rate-limited — see `docs/STAGE12_ALPHA_VANTAGE_BUDGET.md`) |

**If PTD2315's password stops working:** the MySQL volume may have been reset. Check the boot
log — a fresh seed prints the new password once, at WARN:
```bash
grep "Seeded test user" <bootRun output>
```
`docs/CREDENTIALS.local.md` should be updated with whatever it prints.

---

## 6. ADMIN WALKTHROUGH

Log in as **PTD2315** (Admin credentials).

### 6.1 Log in and confirm admin access
1. Go to `http://localhost:8080/`, enter the Admin username/password, click **Log in**.
2. You should see **Dashboard**, **Portfolio**, *and* **Admin** in the left nav (the Admin
   link only appears for accounts with the ADMIN role).

### 6.2 Manage platform API credentials
This is the one admin-exclusive screen — it manages *platform-wide* keys (used for every
user's predictions), not a personal account.
1. Click **Admin** in the nav.
2. You'll see cards for **NewsAPI.org**, **FRED**, **Reddit** — each shows `Configured` or
   `Not set`.
3. To set one: type a value into its field, click **Save**. The card should flip to
   `Configured` with an "Updated <time>" note. The raw value is never echoed back — the field
   just shows a masked placeholder afterward.
4. To clear one: click **Clear** (only enabled once a value is set).

### 6.3 Confirm role-gating actually works
This is worth checking explicitly, not just assumed:
```bash
# Log in as the plain user and try to hit the admin endpoint
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser01","password":"<see CREDENTIALS.local.md>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/admin/settings/credentials \
  -H "Authorization: Bearer $TOKEN"
# Expect: 403
```

### 6.4 Everything a plain User can do
PTD2315 also carries the USER role, so every step in §7 below works identically when logged in
as Admin — Admin is a strict superset, not a separate account with a different dashboard.

---

## 7. USER WALKTHROUGH

Log in as **testuser01** (User credentials) — or as PTD2315, either works identically for
everything below.

### 7.1 Log in
Go to `http://localhost:8080/`, enter the User username/password, click **Log in**. You should
see **Dashboard** and **Portfolio** in the nav — no **Admin** link (testuser01 has no ADMIN
role; confirmed in §6.3 above).

### 7.2 Browse recommendations
1. You land on **Dashboard**, **IPOs** tab is the reliable one to start with — it always has
   data (seeded sample IPOs, no external API dependency). **US Markets**/**Indian Markets**
   depend on live quote providers that are frequently rate-limited/blocked in this dev setup
   (see `docs/STAGE12_ALPHA_VANTAGE_BUDGET.md` §6, `docs/STAGE7_DATA_SOURCING.md` — this is a
   known, pre-existing external-data limitation, not an app bug) — if either shows "0
   recommendations," that's the honest empty state, not broken.
2. Cards are grouped: IPOs by recommendation category (Strong Apply → Apply → Risky → Avoid),
   stocks by market-cap tier (Mega → Micro) — both sorted best-confidence-first within group.

### 7.3 View full analysis detail
1. Click any card.
2. The modal shows: price/target/stop-loss, model confidence, risk metrics, a score-by-score
   breakdown (technical/fundamental/sentiment/macro/ML), why (reasons), and risks.
3. Note the two labels are deliberately precise: **"Model confidence"** (this model's own
   confidence, not a backtested win rate) and the risk stats (not a fabricated loss
   probability) — see `docs/STAGE13_UI_COMPLETION.md` §30.4 for why.

### 7.4 Buy/Sell hand-off
1. In the detail modal, click **"Buy / Sell \<SYMBOL> on Zerodha Kite"** (India) or
   **"... on INDmoney"** (US) — opens the broker's own site in a new tab. This app never
   executes the trade itself; you log in and trade there.

### 7.5 Record a purchase ("I bought this")
1. Still in the detail modal, click **"I already bought this →"** (or **"...got an allotment
   / bought this →"** for an IPO).
2. Enter **Quantity** and **Price you paid**, click **Save to portfolio**.
3. You should see "Saved — see it under Portfolio."

### 7.6 Check your portfolio
1. Click **Portfolio** in the nav.
2. Your new position appears under **Open positions**: symbol, quantity, avg buy price,
   current price, live unrealized P&L, target/stop-loss.

### 7.7 See an alert (target hit / stop-loss hit / outlook worsened)
Alerts are raised by a scheduled job (default: every 15 minutes) — to see one without waiting:
```bash
# Restart the app with a much shorter monitor interval, just for this test
HFT_PORTFOLIO_MONITOR_POLL_MS=15000 SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```
Then set a position's stop-loss above its current price directly (simulates a breach):
```sql
UPDATE portfolio_positions SET stop_loss_price = <above current_price> WHERE symbol='<SYMBOL>';
```
Within ~15 seconds, reload the Portfolio tab — a red alert banner should appear at the top:
*"Stop-loss hit — \<SYMBOL>... consider selling to limit further loss."* Click **Dismiss** to
acknowledge it. (Revert `HFT_PORTFOLIO_MONITOR_POLL_MS` to the default 15-min cadence for
normal use — this override is for testing only.)

### 7.8 Close a position ("I sold this")
1. On an open position, click **"I sold this →"**.
2. Enter the price you actually sold at, click **Confirm**.
3. The position moves to **Closed positions**, showing realized P&L —
   `(sell price − buy price) × quantity`.

### 7.9 Clean up a mistaken entry
Click **Remove** on any position (open or closed) to delete it outright.

---

## 8. INSPECTING MYSQL DATA

**Option A — command line:**
```bash
mysql -h127.0.0.1 -P3307 -uhft_app -phft_dev_password hft_market_intelligence
```
```sql
SHOW TABLES;
SELECT * FROM users;
SELECT * FROM trade_recommendations ORDER BY generated_at DESC LIMIT 10;
SELECT * FROM ipo_data;
SELECT * FROM portfolio_positions;
SELECT * FROM portfolio_alerts ORDER BY created_at DESC;
```

**Option B — a GUI client** (TablePlus, DBeaver, Sequel Ace, MySQL Workbench — any of them):
| Field | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `3307` |
| Database | `hft_market_intelligence` |
| User | `hft_app` (or `root` for full access — see §11) |
| Password | see `docs/CREDENTIALS.local.md` |

**Key tables:** `users`/`user_roles` (accounts), `trade_recommendations` (+ `reco_reasons`/
`reco_risks`/`reco_news`/`reco_data_sources` child tables), `ipo_data`, `portfolio_positions`,
`portfolio_alerts`, `platform_api_credentials` (admin-managed keys, encrypted at rest),
`stock_quotes`, `ohlcv_data`, `sentiment_data`, `macro_data`, `technical_indicators`,
`backtest_runs`/`backtest_trades`.

---

## 9. INSPECTING CLICKHOUSE DATA

**Option A — the built-in web UI (no install needed):** open
**http://localhost:8123/play** in a browser, enter user `default` / password from
`docs/CREDENTIALS.local.md`, run SQL directly:
```sql
SHOW TABLES FROM hft_analytics;
SELECT count() FROM hft_analytics.trade_signals;
SELECT * FROM hft_analytics.trade_signals ORDER BY generated_at DESC LIMIT 20;
```

**Option B — command line via Docker:**
```bash
docker exec -it hft-clickhouse clickhouse-client --user default --password <see CREDENTIALS.local.md>
```

**Option C — a GUI client** (DBeaver, TablePlus both support ClickHouse):
| Field | Value |
|---|---|
| Host | `127.0.0.1` |
| HTTP Port | `8123` |
| Native Port | `9000` |
| Database | `hft_analytics` |
| User | `default` |
| Password | see `docs/CREDENTIALS.local.md` |

**Tables:** `trade_signals` (live — every ML-scored signal mirrored here, see
`docs/HFT_ARCHITECTURE.md` §26.3), `candles_1m` and `market_ticks` (schema exists, nothing
writes to them yet — a disclosed, not-yet-built follow-up).

---

## 10. INSPECTING REDIS (bonus)

```bash
docker exec -it hft-redis redis-cli -a <see CREDENTIALS.local.md>
> KEYS *
> GET hft:asrb:evidence:<SYMBOL>:<MARKET>
```

---

## 11. DATABASE ADMIN PRIVILEGES

**MySQL root** (`root` / see `docs/CREDENTIALS.local.md`) — unrestricted: create/drop any
database, manage users and grants, view/modify every table regardless of the app's own
`hft_app` user's scope. Use this for anything beyond querying the app's own schema (e.g.
creating a read-only reporting user, taking a manual backup with `mysqldump`).

**ClickHouse `default`** (see `docs/CREDENTIALS.local.md`) — this deployment does not restrict
the `default` user, so it already has full admin rights: create/drop databases and tables,
manage other users, adjust settings.

Both are scoped to `localhost`-bound Docker containers on this machine only (see
`docker-compose.yml`'s header comment) — not reachable from outside this machine as configured.

---

## 12. API QUICK REFERENCE

| What | URL |
|---|---|
| Web UI | http://localhost:8080/ |
| Swagger / OpenAPI (REST) | http://localhost:8080/swagger-ui.html |
| GraphiQL (GraphQL IDE) | http://localhost:8080/graphiql |
| Actuator health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| ClickHouse Play UI | http://localhost:8123/play |

All `/api/v1/**` REST endpoints require `Authorization: Bearer <accessToken>` except
`/api/v1/auth/*`, `/api/v1/market/**`, `/api/v1/recommendations/**`, `/api/v1/analysis/**`,
`/api/v1/ipo/**` (public/read-only by design — see `SecurityConfig.PUBLIC_ENDPOINTS`).
`/api/v1/portfolio/**` and `/api/v1/admin/**` always require auth (the latter additionally
requires the ADMIN role).

---

## 13. TROUBLESHOOTING

| Symptom | Explanation | Doc |
|---|---|---|
| US/India dashboard tabs show 0 recommendations | Alpha Vantage daily quota exhausted, or NSE returning a bot-detection 403 — both external, pre-existing, not app bugs | `STAGE9_INFRASTRUCTURE.md` §6, `STAGE12_ALPHA_VANTAGE_BUDGET.md` |
| A newly-bought IPO position never gets monitored/alerted | No live quote route exists for a symbol before it actually lists | `STAGE13_UI_COMPLETION.md` §30.6 |
| `app.js`/dashboard changes don't show up after editing | Static files are served from `build/resources/main`, copied from `src/main/resources` by Gradle's `processResources` — a running `bootRun` doesn't pick up source edits live; stop and restart it | — |
| MySQL connection refused on 3306 | This setup uses **3307**, not the default 3306 (something else already had 3306 on the machine this was built on) | `STAGE9_INFRASTRUCTURE.md` §4 |

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
