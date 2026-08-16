# STAGE 10 — ASRB Live Wiring

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Companion docs:** `docs/HFT_ARCHITECTURE.md` §27 (design rationale — the context-vector
> chicken-and-egg, the reward-loop split, the scope decision to wire sentiment but not macro),
> `docs/ASRB_TECHNICAL_DISCLOSURE.md` (the algorithm itself, built standalone in Stage 9b).
> This doc is the operational detail: what changed, how to verify, what's still open.

---

## 1. STAGE OVERVIEW

Stage 9b built ASRB — correlation-discounted, misinformation-risk-discounted, reliability-
weighted source fusion — as a standalone, unwired module. Stage 10 wires it into the live
recommendation pipeline, specifically replacing `SentimentAnalysisService`'s flat news/social
blend (see §27.1 for why sentiment and not macro).

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 10 — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  SentimentAnalysisService.overallSentimentScore                                       ║
║    flat 0.6·newsSentiment + 0.4·socialSentiment ──────── ASRB-fused (context-aware,   ║
║                                                            correlation/misinfo-aware)   ║
║                                                                                        ║
║  Reliability learning                                                                 ║
║    none — every source trusted equally, every pass ──── per-source Bayesian posterior, ║
║                                                            learns from recordSignalOutcome║
║                                                                                        ║
║  Misinformation-risk narratives                                                       ║
║    undetected ──────────────────────────────── flagged via SentimentData.specialAlert  ║
║                                                                                        ║
║  GET /api/v1/recommendations/stock/{symbol}                                           ║
║    threw IllegalStateException on every call ─────────────── fixed, pre-existing bug  ║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. WHAT ACTUALLY CHANGED

```
src/main/resources/application.yml           (+hft.asrb.* — 14 hyperparameters + enabled flag)
src/main/java/com/hft/intelligence/AsrbConfig.java   (new — Spring-wires the Stage 9b POJOs)
src/main/java/com/hft/ml/MLFeatureVector.java        (+toContextArray() — 41-dim flatten,
                                                        declaration order, tested precisely)
src/main/java/com/hft/service/analysis/SentimentAnalysisService.java
    (new 3-arg analyzeSentiment(symbol, market, context) overload — ASRB fusion; 2-arg
     overload unchanged; +buildSourceSignals/addSourceIfPresent/to0to100 helpers;
     +persistEvidenceForReward/recordOutcome for the reward loop)
src/main/java/com/hft/service/signal/RecommendationEngine.java
    (fundamental/macro moved ahead of sentiment; builds the ASRB context; calls the new
     3-arg overload; removed the broken @Async annotation — see §5)
src/main/java/com/hft/graphql/MLResolver.java
    (recordSignalOutcome now also calls SentimentAnalysisService.recordOutcome)
src/test/java/com/hft/ml/MLFeatureVectorTest.java    (new — 3 tests on toContextArray())
```

No new entities, no new REST/GraphQL endpoints, no new Kafka topics/ports. `SentimentData`'s
schema is unchanged (ASRB writes into the existing `overallSentimentScore`/`specialAlert`
fields, nothing new persisted).

---

## 3. CONFIGURATION REFERENCE

```yaml
hft:
  asrb:
    enabled: true          # false reverts every caller to the pre-Stage-10 flat blend
    lambda-corr: 0.97       # correlation EWMA decay
    kappa: 0.5               # correlation-discount sensitivity
    lambda-time: 0.99        # posterior temporal forgetting
    prior-precision: 1.0      # Bayesian prior strength
    rho-threshold: 0.3        # independence threshold for corroboration counting
    w1: 2.0                   # misinfo-risk weight: (1 - credibility)
    w2: 0.5                   # misinfo-risk weight: claim-velocity anomaly
    w3: 0.7                   # misinfo-risk weight: independent corroboration (reduces risk)
    v0: 1.5                   # velocity z-score baseline
    tau-risk: 0.7              # narrative-risk-alert threshold
    tau-velocity: 2.0          # narrative-risk-alert velocity threshold
    risk-aversion: 0.6         # misinfo-risk evidence discount strength
    tau-stability: 0.6         # Gittins-vs-Thompson gate
    gittins-z: 1.5              # UCB exploration bonus
```

All 14 hyperparameters are **structural starting defaults, not calibrated constants** —
`ASRB_TECHNICAL_DISCLOSURE.md` §10 explicitly says these require calibration against real
backtest data. Tunable without a code change; revisit once enough `recordSignalOutcome` volume
exists to calibrate against.

---

## 4. HOW TO VERIFY

```bash
docker compose up -d --wait
gradle compileJava test                        # 40/40 — 37 baseline + 3 MLFeatureVectorTest

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
curl -s "localhost:8080/api/v1/recommendations/stock/MSFT?market=US_NASDAQ"
```

Watch the logs for:
```
[Sentiment] Analyzing: <SYMBOL> on <MARKET> (ASRB context present)   ← confirms the 3-arg path fired
```

To inspect what ASRB actually did with a given pass:
```bash
docker exec hft-redis redis-cli -a hft_dev_redis GET "hft:asrb:evidence:<SYMBOL>:<MARKET>"
```
Returns `{"context": [...41 values...], "weights": {"sourceId": weight, ...}}` — only sources
that returned real evidence that pass appear.

To test the reward loop directly:
```graphql
mutation {
  recordSignalOutcome(symbol: "MSFT", market: US_NASDAQ, model: "A",
                       actualReturnPercent: 3.5, wasBullishCall: true) {
    model totalPredictions hitRatePct
  }
}
```
Watch for `[Sentiment] ASRB: N source posterior(s) updated from <SYMBOL> outcome (label=...)`.

---

## 5. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
Macro sources (FRED, NSE FII/DII, GDELT) — NOT wired into ASRB, deliberately (§27.1). Only
    the 5 news/social sentiment sources are. A reasonable follow-up, not started.
Claim clustering — symbol-level, not per-headline/per-claim NLP clustering (none exists in
    this codebase). Two sources reporting genuinely different news about the same symbol in
    the same pass are treated as corroborating/contradicting each other regardless.
Calibration — all 14 hft.asrb.* hyperparameters are structural defaults per the disclosure
    doc's own §10, not backtested/calibrated values. Needs real recordSignalOutcome volume
    (or a WalkForwardValidator-style backtest pass) before treating them as tuned.
Live verification used a hand-seeded synthetic quote (Alpha Vantage's daily quota was
    exhausted and NSE returned a bot-detection 403 during testing — both pre-existing,
    unrelated conditions, not caused by this stage). The seeded row was deleted after
    verification; the pipeline wiring itself was confirmed for real (see HFT_ARCHITECTURE.md
    §27.6), but a fully organic live pass (real quote → real everything) hasn't happened yet.
```

---

## 6. WHAT'S NEXT

- Stage 11: ONNX / deep-learning model serving (renumbered again — was Stage 7, then 8, then
  9, now 10's slot went to this ASRB wiring instead, per direct user instruction to do ASRB
  first).
- Stage 12: Alpha Vantage polling-frequency fix (§26.6) — the 25-req/day free tier keeps
  colliding with the existing 5-second poll cadence; needed before Alpha Vantage-sourced
  recommendations (including ASRB's `alpha-vantage-news` source) can be relied on consistently.
- A real organic live pass once Stage 12 unblocks Alpha Vantage (or against an NSE symbol once
  NSE's bot-detection is worked around) — to see ASRB's correlation/misinformation logic
  activate under real repeated multi-pass conditions (today's verification was necessarily a
  first-ever pass for its sources, so correlation/misinfo history was empty by construction).

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
