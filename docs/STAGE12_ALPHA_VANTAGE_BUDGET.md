# STAGE 12 — Alpha Vantage Call Budget

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Companion doc:** `docs/HFT_ARCHITECTURE.md` §29 — the two root causes (not one), why the
> fix has three parts, and two unrelated stale comments fixed along the way.

---

## 1. STAGE OVERVIEW

Stage 9c first surfaced the symptom: Alpha Vantage's real key, confirmed valid, returned empty
quotes from the very first call. Direct cause, confirmed via a raw curl to Alpha Vantage
itself: the free tier is **25 requests/day, total, across every function** — far stricter than
assumed, and the existing 24-symbol watchlist on a 5-second poll cycle exhausted it almost
instantly. This stage traces the problem to its actual root causes and fixes them.

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 12 — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  pollUSMarket()          @CacheEvict(allEntries=true) every cycle ── removed;         ║
║                            getQuote()'s 30s @Cacheable TTL now actually works          ║
║                                                                                        ║
║  Alpha Vantage calls     no shared budget awareness ──────── AlphaVantageBudgetGuard,  ║
║                            shared across GLOBAL_QUOTE/NEWS_SENTIMENT/TIME_SERIES         ║
║                                                                                        ║
║  Dev poll interval       5000ms (faster than the cache TTL above it) ──── 60000ms      ║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. WHAT ACTUALLY CHANGED

```
src/main/java/com/hft/service/data/AlphaVantageBudgetGuard.java   (new)
src/main/java/com/hft/service/data/AlphaVantageService.java       (guard checks in getQuote(),
                                                                     getHistoricalData())
src/main/java/com/hft/service/analysis/SentimentAnalysisService.java
    (guard check in fetchAlphaVantageNews() — shares the same daily budget)
src/main/java/com/hft/service/data/MarketDataAggregatorService.java
    (removed @CacheEvict(allEntries=true) from pollUSMarket(); corrected a stale class
     javadoc claiming Yahoo Finance/BSE India failover that was never actually implemented)
src/main/resources/application.yml
    (+hft.alpha-vantage.daily-call-budget: 20; fixed a stale "500/day" comment; added a
     "configured but not implemented" note to the yahoo-finance block)
src/main/resources/application-dev.yml           (market-data-poll-ms: 5000 -> 60000)
src/test/java/com/hft/service/data/AlphaVantageBudgetGuardTest.java   (new — 4 tests)
```

No new entities, no new REST/GraphQL endpoints, no new Kafka topics.

---

## 3. CONFIGURATION REFERENCE

```yaml
hft:
  alpha-vantage:
    daily-call-budget: 20   # real cap is 25/day; this leaves headroom for ad-hoc/manual calls
```

Prod (a paid Alpha Vantage tier, if ever adopted) should override this via
`ALPHA_VANTAGE_DAILY_CALL_BUDGET` or a higher value in `application-prod.yml` — not done here,
since no paid tier is currently in use.

---

## 4. HOW TO VERIFY

```bash
gradle compileJava test    # 49/49 — 45 baseline + 4 new AlphaVantageBudgetGuardTest

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```

Watch the logs for the poller's first cycle (fires immediately on boot):
```
[AlphaVantage] Daily call budget (20) exhausted for <date> — further calls skipped locally...
```
should appear exactly once, after exactly 20 real `GET .../GLOBAL_QUOTE` calls — count them:
```bash
grep -c "GET https://www.alphavantage.co/query?function=GLOBAL_QUOTE" <bootRun log>
```

---

## 5. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
Single-instance only  — AlphaVantageBudgetGuard's counter is in-memory, not coordinated across
                         multiple app instances sharing one Alpha Vantage key. Correct for this
                         platform's current single-instance deployment shape; would need a
                         shared store (Redis) if that changes.
Doesn't retroactively know about quota already spent by earlier separate process runs today —
                         each fresh boot resets the LOCAL guard to a full 20-call budget, but
                         Alpha Vantage's own ACCOUNT-level quota is independent of this process.
                         If the account is already exhausted for the day from prior testing,
                         this guard's 20 "allowed" calls will still come back empty from Alpha
                         Vantage's side — expected, not a bug in this fix.
No real fallback provider — "demote behind Yahoo Finance" (suggested in §26.6) isn't actually
                         available: no MarketDataService implementation exists for Yahoo
                         Finance despite the config block. Building one is a separate,
                         standalone data-source integration, not part of this stage's scope
                         (see HFT_ARCHITECTURE.md §29.3).
```

---

## 6. WHAT'S NEXT

- No further planned stage from the current backlog — this closes out the three-item list
  ("ASRB wiring, ONNX, Alpha Vantage fix") the user asked to work through in order.
- Candidate follow-ups, not scheduled: a real Yahoo Finance `MarketDataService` implementation
  (would give US quotes an actual unlimited fallback); Redis-backed multi-instance coordination
  for `AlphaVantageBudgetGuard` if this platform ever runs more than one instance; a paid Alpha
  Vantage tier if 20 calls/day proves too restrictive for real use.

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
