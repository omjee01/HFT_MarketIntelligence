# STAGE 7 — Real Intelligence Data Sourcing

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Numbering note:** the original plan (see `docs/SESSION_CONTEXT.md` §12) reserved Stage 7
> for ONNX/deep-learning model serving. A 2026-08-16 code audit found that the platform's
> sentiment/macro pipeline — the features any such model would train on — was partly
> synthetic (`Math.random()`) or hardcoded. Training a model on fake features would just
> teach it to fit noise, so real data sourcing was inserted ahead of it and claims the
> Stage 7 slot. **ONNX/deep-learning work becomes Stage 8.**

---

## 1. STAGE OVERVIEW

Stage 7 answers the question the platform's own pitch had been begging since Stage 1:
**"Is the news/social/macro intelligence actually real, or is some of it fiction?"**

The audit's answer was: partly fiction. This stage replaces every fake or hardcoded
intelligence input it was possible to replace with a real, ToS-compliant, no-cost source —
and, just as importantly, leaves an honest, specific, in-code record of the handful that
couldn't be replaced yet, and why.

This stage deliberately does **not** change the fusion logic (composite score weighting is
untouched), add new domain entities, change the GraphQL schema, or add new Kafka topics/ports.
It is a pure input-quality fix to three existing services. The smarter fusion layer (ASRB —
see `docs/HFT_ARCHITECTURE.md` §24 and `docs/ASRB_TECHNICAL_DISCLOSURE.md`) is separate,
later work that builds on top of real inputs rather than replacing them.

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 7 — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  SentimentAnalysisService.analyzeSentiment()                                          ║
║    news       Alpha Vantage NEWS_SENTIMENT ─────────────────────── unchanged, was real ║
║    news       NewsAPI.org ───────────────────── was real but OFF by default → now ON   ║
║    news       SEC EDGAR filings (8-K/10-K/10-Q) ────────────────────── NEW, real, live ║
║    social     Reddit (r/investing, r/stocks, r/wallstreetbets, r/IndiaInvestments)     ║
║                 ─────────────────────────── NEW, real OAuth flow, needs your API keys  ║
║    social     StockTwits ──────── NEW, code real, blocked by Cloudflare bot-challenge  ║
║    social     Math.random() noise ──────────────────────────────────────── REMOVED     ║
║                                                                                         ║
║  MacroGeopoliticalService.getMacroData()                                              ║
║    US geopolitical risk    hardcoded 5.0 ──────── GDELT tone score (unverified reach)  ║
║    IN geopolitical risk    hardcoded 4.0 ──────── GDELT tone score (unverified reach)  ║
║    IN FII/DII flow         hardcoded "BUYING"/5 days ────────────── NEW, real, live    ║
║    IN repo rate/CPI/GDP    hardcoded ───────────────────────── unchanged, no free API  ║
║    US rate/CPI/GDP/VIX     FRED (was real but OFF by default) ────────────── now ON    ║
║                                                                                         ║
║  FundamentalAnalysisService.fetchIndiaFundamentals()                                  ║
║    India fundamentals      DB passthrough ──── unchanged, Screener.in blocked by       ║
║                                                  their own robots.txt (checked, honored)║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. SOURCE-BY-SOURCE STATUS (the part that matters most)

| Source | Feeds | Status | Detail |
|---|---|---|---|
| NewsAPI.org | News headlines | **Real, now on by default** | Integration pre-existed; was gated `enabled: false`. Needs `NEWSAPI_API_KEY` (free tier) — no-ops safely without one. |
| FRED | US rate/CPI/GDP/unemployment/yields/VIX | **Real, now on by default** | Same as above — needs `FRED_API_KEY` (free), no-ops safely without one. |
| SEC EDGAR full-text search | Company filing events (8-K/10-K/10-Q) as headlines | **Real, verified live** | No key required. `efts.sec.gov` confirmed reachable, returning real JSON matching expected shape. US-only (SEC has no India equivalent; NSE/BSE corporate-announcement scraping is a future gap, not built here). |
| NSE FII/DII flow | India `fiiFlowTrend`, `fiiNetFlowCrores`, `diiNetFlowCrores` | **Real, verified live** | Uses the exact header set `NSEIndiaService` already established (User-Agent/Referer/Accept). Confirmed returning real data. |
| Reddit | Social sentiment | **Real OAuth flow implemented, needs your credentials** | App-only OAuth2 client-credentials flow confirmed reachable (verified via a real `401` on unauthenticated calls, not a network failure). Fails closed — zero data, zero error — until `REDDIT_CLIENT_ID`/`REDDIT_CLIENT_SECRET` are supplied. See §5. |
| StockTwits | Social sentiment | **Code correct, currently blocked** | The public endpoint returns a Cloudflare JS-challenge page to server-side callers, not JSON. Fails closed to "no data" rather than crashing. Needs re-verification from the real deploy environment — this may be specific to where the code ran during development. |
| GDELT | Geopolitical risk score (replaces hardcoded 5.0 US / 4.0 India) | **Implemented, unverified reachability** | `api.gdeltproject.org` timed out at the TCP level in the dev sandbox while general internet and the non-API GDELT subdomain both worked — inconclusive, not a confirmed dead end. Falls back to the original hardcoded values when unreachable, so behavior is unchanged until this is confirmed live elsewhere. |
| India repo rate / CPI / GDP | India macro | **Still hardcoded** | No publicly documented free RBI REST API was found. Real fix requires either RBI publishing one, building XBRL/bulletin scraping (its own scoped effort, and would need a ToS check), or a paid data vendor. |
| Screener.in / India fundamentals | India P/E, ROE, etc. | **Still DB-only, correctly not scraped** | Their only JSON-returning endpoint (`/api/company/search/?q=`) is explicitly disallowed by their own `robots.txt` (`Disallow: /*?q=`). Per the platform's standing no-ToS-violating-scraping rule, left alone rather than worked around. |
| Twitter/X | Social sentiment | **Deliberately deferred** | No free API tier — budget decision, not an engineering gap (`HFT_ARCHITECTURE.md` §24.2). |

---

## 3. ARCHITECTURE — WHAT ACTUALLY CHANGED

No new files, no new entities, no new GraphQL fields, no new Kafka topics, no new ports.
Three existing services gained private fetch methods, following the exact pre-existing
convention (`fetchAlphaVantageNews`/`fetchNewsApi`/`fetchFredLatestValue`): shared `OkHttpClient`
bean, Jackson `JsonNode` parsing, `@Slf4j` try/catch → log `.warn` → return empty/null on
failure, gated by an `enabled` flag plus a blank-credential check.

```
SentimentAnalysisService.java
  + fetchSecEdgarFilings(symbol, market)      → List<String> headlines
  + fetchRedditPosts(symbol, market)          → List<String> post titles/selftext
  + getRedditAccessToken()                     → cached OAuth2 bearer token (synchronized)
  + fetchStockTwitsScores(symbol)              → List<Double> pre-scored or NLP-scored
  ~ analyzeSentiment(): headlines += SEC EDGAR; socialSentiment now built from
    Reddit+StockTwits scores (falls back to newsSentiment × 0.8 — an honest "no independent
    signal" default — when neither source returns anything, instead of inventing noise)

MacroGeopoliticalService.java
  + fetchNseFiiDiiFlows()                      → Map<String,BigDecimal> {FII, DII}
  + fetchGdeltRiskScore(countryCode)           → Double, 0–10 scale (5.0 - avgTone, clamped)
  + labelForRiskScore(score)                    → EXTREME/HIGH/ELEVATED/LOW
  ~ buildUSMacroData()/buildIndiaMacroData(): geopoliticalRiskScore now computed via GDELT
    when reachable, else the original Phase-1 constant; India macro now includes real
    FII/DII when NSE responds, else the original hardcoded flow/streak

FundamentalAnalysisService.java
  ~ fetchIndiaFundamentals(): comment replaced with a specific, checked explanation
    (Screener.in robots.txt) instead of a vague "Phase 2" TODO — behavior unchanged
```

`@CircuitBreaker` annotations were added to the new fetch methods for consistency with
`FundamentalAnalysisService.fetchUSFundamentals`'s existing pattern. Note inherited from that
pattern, not introduced here: these annotations are currently inert on private/self-invoked
methods (Spring AOP can't proxy them) — a pre-existing gap in the codebase, worth fixing
across all of them in a future pass, not specific to Stage 7.

---

## 4. CONFIGURATION REFERENCE

New/changed keys in `application.yml` (defaults shown; `application-dev.yml` mirrors the
`newsapi`/`reddit` on-by-default treatment, `application-prod.yml` was already correct):

```yaml
hft:
  newsapi:
    enabled: true              # was false — safe, no-ops without NEWSAPI_API_KEY
  fred:
    enabled: true               # was false — safe, no-ops without FRED_API_KEY
  reddit:
    enabled: true               # was false — safe, no-ops without client-id/secret
    client-id: ${REDDIT_CLIENT_ID:}
    client-secret: ${REDDIT_CLIENT_SECRET:}
  stocktwits:
    enabled: true
    base-url: https://api.stocktwits.com/api/2
  sec-edgar:
    enabled: true
    base-url: https://efts.sec.gov/LATEST/search-index
    user-agent: "HFT-Market-Intelligence-Platform research@hmip.local"   # required by SEC's fair-access policy
  gdelt:
    enabled: true
    base-url: https://api.gdeltproject.org/api/v2
  rbi-nse:
    enabled: true
    nse-base-url: https://www.nseindia.com/api
    rbi-base-url: https://api.rbi.org.in    # reserved — no real RBI endpoint wired yet
  screener-in:
    enabled: true               # reserved — fetchIndiaFundamentals() doesn't call out yet
  twitter:
    enabled: false               # deliberately deferred, see §2
```

**To get real Reddit data:** create a Reddit "script" app at reddit.com/prefs/apps (free),
set `REDDIT_CLIENT_ID`/`REDDIT_CLIENT_SECRET` as environment variables. No other source in
this stage needs a key to produce real data.

---

## 5. HOW TO VERIFY

```bash
# Compile + full test suite (baseline: 24 tests, 0 failures, unchanged by this stage)
gradle compileJava test

# Run in dev mode and watch the logs for real vs. fallback behavior per source:
gradle bootRun --args='--spring.profiles.active=dev'
```

Expected log signatures:
```
[Sentiment] SEC EDGAR fetch failed for ...     ← should NOT appear for a real US symbol
[Sentiment] Reddit fetch failed for ...        ← expected until REDDIT_CLIENT_ID/SECRET are set
[Sentiment] StockTwits fetch failed for ... (see class doc — likely bot-challenge)
                                                 ← currently expected everywhere, see §2
[Macro] NSE FII/DII fetch failed: ...          ← should NOT appear during NSE market hours
[Macro] GDELT fetch failed for ...             ← currently expected, see §2 (unverified reachability)
```
A quick before/after sanity check worth running once real keys are supplied: diff
`WalkForwardValidator` output (Stage 6) on the same symbol/date range before and after this
stage, to confirm real sentiment/macro data actually moves backtest metrics rather than
being inert.

---

## 6. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
StockTwits    — blocked by Cloudflare bot-challenge from the dev sandbox. Re-test from the
                real deploy environment before assuming it's dead; may just be sandbox-specific.
GDELT         — TCP-level timeout to api.gdeltproject.org from the dev sandbox, general
                internet unaffected. Inconclusive — re-test from the real deploy environment.
India macro   — repo rate/CPI/GDP remain hardoded; no free RBI REST API found. Real fix is a
                separate scoped effort (RBI bulletin/XBRL parsing) or a paid vendor.
India fundamentals — Screener.in's only JSON endpoint is robots.txt-disallowed. No workaround
                attempted, per the platform's standing no-ToS-violating-scraping rule.
@CircuitBreaker — inert on private methods platform-wide (pre-existing gap, not introduced
                here); fixing it is a separate, small, cross-cutting cleanup.
```

---

## 7. WHAT'S NEXT

- **Stage 8 (was "Stage 7"): ONNX/deep-learning model serving** — now unblocked, since the
  features it would train on are real rather than synthetic. See `docs/SESSION_CONTEXT.md`
  §12 for the original plan (renumber references to Stage 8 when picked back up).
- **ASRB** (`docs/HFT_ARCHITECTURE.md` §24, `docs/ASRB_TECHNICAL_DISCLOSURE.md`) — the
  correlation/misinformation-aware fusion algorithm — can now be built on top of real sources
  instead of needing to wait for them.
- **Web UI + minimal Identity/Admin** — next immediate step per user direction: a UI to test
  as both admin and user, including an admin screen to manage the API keys referenced in §4
  through the UI instead of environment variables.

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
