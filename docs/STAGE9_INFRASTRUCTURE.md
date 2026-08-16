# STAGE 9c — Real Infrastructure (MySQL, ClickHouse, Redis, Kafka)

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Companion docs:** `docs/HFT_ARCHITECTURE.md` §26 (design rationale, two-datasource
> wiring), `docs/STAGES_OVERVIEW.md` §3 Stage 9 (where this fits alongside 9a IPO Engine
> and 9b ASRB). This doc is the operational detail: what to run, what to expect, what's
> still open.

---

## 1. STAGE OVERVIEW

Every prior stage's config *described* Redis, Kafka, and a production RDBMS — dev ran on
H2-in-memory with `cache.type: simple` and Kafka's listeners disabled (`auto-startup: false`).
That was a reasonable sequencing choice early on (get the logic right before standing up
infrastructure), but it meant nothing in this repo had ever actually talked to a real
database, cache, or broker. This stage closes that gap:

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 9c — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  User/IPO/TradeRecommendation/Backtest data   H2 in-memory ──────── real MySQL 8.0    ║
║  Analytics / signal-transaction history       didn't exist ─────── ClickHouse 24.8     ║
║  Cache + pub/sub                              in-process "simple" ──────── real Redis  ║
║  Event streaming                              listeners disabled ──── real Kafka (KRaft)║
║  Alpha Vantage                                "demo" key ─────── real key (rate-limited)║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

**Nothing about `dev`/`gradle test` changed.** A new `docker` profile layers real infra on
top of `dev` — it's additive, not a replacement. The 37-test suite runs exactly as it always
has, on H2, with zero external dependencies.

---

## 2. COMPONENT-BY-COMPONENT STATUS

| Component | Status | Detail |
|---|---|---|
| MySQL 8.0 | **Real, verified live** | Primary datasource for `docker`/`prod`. All JPA entities create cleanly — see §5 for a bug this surfaced and fixed. |
| ClickHouse 24.8 | **Real, verified live** | Secondary datasource, plain JDBC (no JPA). `trade_signals` is wired live; `candles_1m`/`market_ticks` are schema-only. |
| Redis 7 | **Real, verified live** | `cache.type: redis`, `hft.redis-pubsub.enabled: true`. Confirmed via `/actuator/health` (`redis: UP`, version 7.4.10). |
| Kafka (KRaft) | **Real, verified live** | `apache/kafka:latest`, no ZooKeeper. Streams topology (Stage 3/5/6) runs against it; new `hft-clickhouse-sink` consumer group confirmed assigned all 16 `signals-ml-scored` partitions. |
| Alpha Vantage | **Real key, confirmed valid — free tier far stricter than assumed** | See §6. 25 requests/day total; dev's polling cadence exhausts it almost immediately. Not fixed in this stage. |

---

## 3. ARCHITECTURE — WHAT ACTUALLY CHANGED

```
docker-compose.yml                          (new) — mysql, clickhouse, redis, kafka
.env.example                                 (new) — override template, no real secrets
src/main/resources/application-docker.yml   (new) — the "docker" profile, layers on "dev"
src/main/resources/application-secrets.yml  (new, git-ignored) — real Alpha Vantage key
src/main/java/com/hft/config/DatabaseConfig.java        (new) — see §26.2 of the architecture doc
src/main/java/com/hft/analytics/ClickHouseSchemaInitializer.java  (new)
src/main/java/com/hft/analytics/ClickHouseSignalSink.java         (new)
build.gradle.kts                             (+mysql-connector-j, +clickhouse-jdbc)
src/main/resources/application.yml           (+hft.clickhouse.enabled: false default)
src/main/resources/application-prod.yml      (+hft.clickhouse.* env-var block)
src/main/java/com/hft/model/domain/TradeRecommendation.java  (bug fix — see §5)
src/main/java/com/hft/backtest/BacktestTrade.java             (bug fix — see §5)
.gitignore                                    (+!.env.example exception)
```

No changes to any existing service's business logic, no new REST/GraphQL endpoints, no new
Kafka topics — this stage is infrastructure wiring plus the two bug fixes it surfaced.

---

## 4. CONFIGURATION REFERENCE

```yaml
# application.yml (base — always applied)
hft:
  clickhouse:
    enabled: false     # only "docker" and "prod" turn this on

# application-docker.yml (new — activate alongside "dev")
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/${MYSQL_DATABASE:hft_market_intelligence}?...
  data:
    redis:
      host: localhost
      port: 6380
  kafka:
    listener: { auto-startup: true }
    streams: { auto-startup: true }
hft:
  clickhouse:
    enabled: true
    jdbc-url: jdbc:clickhouse://localhost:8123/${CLICKHOUSE_DATABASE:hft_analytics}
```

**Host ports are non-default: MySQL is 3307, Redis is 6380.** Both defaults (3306/6379) were
already bound by other things on the machine this was built on — one by an unrelated
already-running Docker container (`spring-boot-redis-redis-1`), one by something outside
Docker entirely. Container-internal ports are untouched; this is purely a host-side mapping
decision, made to avoid touching services this stage doesn't own. If 3306/6379 are free on a
different machine, there's no need to change anything — the docker-compose mapping is what it
is regardless, just adjust `application-docker.yml`'s host port references if you'd rather use
the defaults there.

**Real Alpha Vantage key:** stored in `src/main/resources/application-secrets.yml`, which is
git-ignored (`.gitignore` already excluded this exact filename before this stage). Never
committed, never logged. Activate with the `secrets` profile.

**Full local run:**
```bash
docker compose up -d --wait
SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```

---

## 5. BUG FOUND & FIXED: MYSQL RESERVED WORDS

`TradeRecommendation.signal`/`.rank` and `BacktestTrade.signal` mapped to unquoted columns
literally named `signal` and `rank`. H2 doesn't reserve either word, so this was silently
fine through every prior stage's tests. MySQL 8 reserves both (`SIGNAL` — the statement used
in stored-procedure error handling; `RANK()` — a window function, reserved since window
functions landed in 8.0). The first real MySQL boot failed `trade_recommendations`' own
`CREATE TABLE` outright, which cascaded into `backtest_trades`' foreign-key constraint
("Failed to open the referenced table 'trade_recommendations'") even though
`backtest_trades`'s own base table had created fine.

**Fix:** explicit `@Column(name = "signal_type")` / `@Column(name = "reco_rank")` overrides
on both entities. For `TradeRecommendation`, also had to update
`@Index(columnList = "market, signal")` to `"market, signal_type"` — Hibernate does not
retroactively rewrite a raw index column-list string when a field's `@Column` name changes;
that index kept referencing the old name and threw its own syntax error even after the base
table fix.

Verified clean against a MySQL schema dropped and recreated mid-session specifically to get
an unambiguous signal: **zero DDL errors**, all entities create correctly.

**Worth a broader sweep later:** this fix covers the two collisions this stage's boot actually
surfaced. A full audit of every `@Entity` field against the MySQL 8 reserved-word list
(`ORDER`, `GROUP`, `WINDOW`, `ROWS`, `VALUES`, etc.) wasn't done — Java field names avoiding
SQL keywords by convention makes further collisions unlikely but not impossible.

---

## 6. ALPHA VANTAGE — REAL KEY, REAL LIMITS

The key resolves and is genuinely valid — confirmed by calling Alpha Vantage directly outside
the app (`curl .../query?function=GLOBAL_QUOTE&symbol=IBM&apikey=...`). Inside the app,
though, every single quote request came back with an empty body from the very first call.
The raw response explains why:

```json
{"Information": "Thank you for using Alpha Vantage! Please consider spreading out your
free API requests more sparingly (1 request per second). ... free key rate limit
(25 requests per day) ..."}
```

25 requests/day, **total, across every function** — much stricter than the
`hft.alpha-vantage.rate-limit-per-minute: 5` value already in `application.yml` accounts for.
Dev's 5-second market-data poll cycle across ~24 US symbols (`MarketDataAggregatorService`)
burns the entire daily allowance in well under a minute.

This is a pre-existing polling-frequency assumption from when the key was always `demo`
(itself far more restricted, so the mismatch was invisible) — not something this stage
introduced, just something a real key finally made visible. **Not fixed here.** Options for
a follow-up, in no particular order:
- Poll Alpha Vantage far less often specifically (it doesn't need to match the 500ms–5s
  cadence used for other sources), independent of the other sources' cadence.
- Demote it behind Yahoo Finance/NSE (already unlimited, no key, already `enabled: true`) for
  US-market coverage, keeping Alpha Vantage as an occasional/fallback call only.
- Accept a paid Alpha Vantage tier if its specific data (adjusted historical series) is worth it.

---

## 7. HOW TO VERIFY

```bash
docker compose up -d --wait
gradle compileJava test                              # unaffected — still 37/37, H2 only

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
curl -s localhost:8080/actuator/health                # db.primaryDataSource, db.clickHouseDataSource,
                                                        # redis all "UP"
curl -s localhost:8080/api/v1/ipo/recommendations      # 3 sample IPOs, now MySQL-backed
curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"PTD2315","password":"<see TestUserSeeder WARN log on first boot>"}'

# ClickHouse directly:
curl -s "http://default:<password>@localhost:8123/?query=SHOW+TABLES+FROM+hft_analytics"
```

---

## 8. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
candles_1m, market_ticks  — ClickHouse tables exist but nothing writes to them yet. Only
                             trade_signals (off signals-ml-scored) is actually wired.
Alpha Vantage             — 25 req/day free tier; current polling exhausts it in under a
                             minute. See §6 — needs a product decision, not a silent patch.
MySQL reserved words      — this stage fixed the two collisions its own boot surfaced, not
                             a full audit of every @Entity against the MySQL 8 keyword list.
Host ports                — 3307/6380, not 3306/6379, specific to the machine this was built
                             on. No functional impact, just don't assume the defaults elsewhere.
ClickHouseSignalSink      — best-effort: a failed insert is logged and dropped, not retried.
                             It's an analytics mirror; the primary-DB row stays authoritative.
```

---

## 9. WHAT'S NEXT

- Decide the Alpha Vantage polling-frequency question (§6) before relying on it for anything
  time-sensitive.
- Wire a real producer for `candles_1m`/`market_ticks`, or drop the schema-only tables if
  they turn out not to be needed.
- Consider whether ASRB (Stage 9b) wiring into the live pipeline should now use ClickHouse's
  `trade_signals` history as calibration input, since it exists for the first time.

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
