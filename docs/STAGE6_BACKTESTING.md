# STAGE 6 — Backtesting & Strategy Validation Engine

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

---

## 1. STAGE OVERVIEW

Stage 6 answers the question every quantitative platform must answer before going live:
**"Do our signals actually work on data the model has never seen?"**

It adds a self-contained historical simulation engine that replays OHLCV price data
through the full ML scoring pipeline, executes virtual trades against target and stop-loss
levels, and computes institutional-grade strategy performance metrics — all without touching
external APIs or the live trading infrastructure.

Five capabilities are introduced:

1. **BacktestRunner** — simulates BUY signals on daily OHLCV bars; entry at next open,
   exit when target is hit, stop is hit, or max holding period expires
2. **StrategyMetricsEngine** — computes Sharpe ratio, max drawdown, win rate, profit
   factor, expectancy, and CAGR from the trade list
3. **WalkForwardValidator** — prevents look-ahead bias by splitting the date range into N
   rolling windows and running each test period independently
4. **GraphQL async interface** — `runBacktest` mutation fires the run non-blocking;
   `backtestProgress` subscription delivers live progress; `walkForwardValidation` query
   runs synchronously
5. **Kafka Processor 5** — optional signal capture (`signals-ml-scored → backtest-results`)
   for live outcome tracking against production signals

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 6 BACKTESTING PIPELINE                                                        ║
║                                                                                      ║
║  GraphQL Mutation: runBacktest(input)                                                ║
║          │                                                                           ║
║          ▼                                                                           ║
║  BacktestRunner (@Async)                                                             ║
║  ┌───────────────────────────────────────────────────────────────────────────┐       ║
║  │  For each symbol in config.symbols:                                       │       ║
║  │    OHLCVDataRepository.findBy(symbol, market, "1D", fromDate, toDate)    │       ║
║  │    │                                                                      │       ║
║  │    ├─ Bar i (warmup ≥ 20 bars)                                            │       ║
║  │    │    computeTA(bars[0..i]) → RSI, SMA20/50/200, EMA9, ATR, BB         │       ║
║  │    │    MLPredictionService.predict() or EnsembleModel (model="B")        │       ║
║  │    │    SignalType.fromScore(compositeScore)                               │       ║
║  │    │                                                                      │       ║
║  │    └─ BUY / STRONG_BUY + confidence ≥ minConfidence:                      │       ║
║  │         entry = bars[i+1].open                                            │       ║
║  │         scan bars[i+1 .. i+maxHoldingDays]:                               │       ║
║  │           high ≥ targetPrice  → EXIT: TARGET_HIT                         │       ║
║  │           low  ≤ stopLoss     → EXIT: STOP_HIT                           │       ║
║  │           time expires        → EXIT: TIME_EXPIRY (at close)             │       ║
║  │         BacktestTrade saved to DB                                         │       ║
║  │                                                                           │       ║
║  │  StrategyMetricsEngine.compute(trades) → BacktestMetrics                 │       ║
║  │  StreamSinkBridge.emitBacktestProgress(run)                               │       ║
║  └───────────────────────────────────────────────────────────────────────────┘       ║
║          │                                                                           ║
║          ▼                                                                           ║
║  BacktestRun (JPA entity, status=COMPLETE, metrics embedded)                        ║
║          │                                                                           ║
║          ▼                                                                           ║
║  GraphQL Subscription: backtestProgress(runId) → live BacktestRun updates           ║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. ARCHITECTURE

### 2.1 Class Responsibility Map

```
com.hft.backtest/
├── BacktestConfig.java          record — symbols, market, dates, model, thresholds
├── BacktestRun.java             @Entity — lifecycle tracker (PENDING→RUNNING→COMPLETE)
├── BacktestTrade.java           @Entity — one simulated trade with full outcome data
├── BacktestMetrics.java         @Embeddable — 12 performance metrics (embedded in BacktestRun)
├── BacktestRunRepository.java   JPA repository
├── BacktestTradeRepository.java JPA repository
├── StrategyMetricsEngine.java   @Component — computes metrics from trade list
├── WalkForwardValidator.java    @Component — splits date range into N windows
└── BacktestRunner.java          @Service — async orchestrator + inline TA engine

com.hft.graphql/
└── BacktestResolver.java        @Controller — @MutationMapping, @QueryMapping, @SubscriptionMapping

com.hft.repository/
└── OHLCVDataRepository.java     NEW — was missing; needed for historical bar queries

com.hft.streams/
└── StreamSinkBridge.java        MODIFIED — added emitBacktestProgress() + backtestFlux(runId)
```

### 2.2 BacktestRun Lifecycle

```
runBacktest(input) called
        │
        ▼
BacktestRun saved {status: PENDING}   ← returned to GraphQL client immediately
        │
        ▼  (async thread from "analysisExecutor")
BacktestRun {status: RUNNING, progressPercent: 0}
        │
        ▼  (per symbol processed)
StreamSinkBridge.emitBacktestProgress(run)
  → backtestProgress subscription receives update
        │
        ▼  (all symbols done)
StrategyMetricsEngine.compute()
        │
        ▼
BacktestRun {status: COMPLETE, progressPercent: 100, metrics: {...}}
  → final subscription event emitted → Flux completes (takeUntil COMPLETE|FAILED)
```

### 2.3 Trade Simulation Logic

```
For each daily bar i (after 20-bar warmup):

  computeTA(bars[0..i]):
    RSI-14  — Wilder's RSI from last 14 closes
    SMA-20/50/200, EMA-9  — rolling averages
    ATR-14  — average true range for stop sizing
    BB upper/lower  — SMA20 ± 2σ
    TechnicalScore  — 0–100 composite from RSI + SMA alignment

  score(symbol, ta, bars[0..i], modelVariant):
    model="A" → MLPredictionService.predict()  (weighted composite + linear regression)
    model="B" → EnsembleModel.computeScore()   (Momentum + MeanReversion + Trend)

  if signal.isBullish() and confidence ≥ minConfidence and not inTrade:
    entryPrice  = bars[i+1].open
    targetPrice = prediction.predictedTargetPrice
    stopLoss    = prediction.stopLossPrice

    scan bars[i+1 .. i+maxHoldingDays]:
      if bar.high  ≥ targetPrice  → TARGET_HIT,  exitPrice = targetPrice
      if bar.low   ≤ stopLoss     → STOP_HIT,    exitPrice = stopLoss
    if neither:                   → TIME_EXPIRY, exitPrice = lastBar.close

    returnPct = (exitPrice – entryPrice) / entryPrice × 100
    save BacktestTrade
    advance i to exitBar (no overlapping trades per symbol)
```

### 2.4 Walk-Forward Validation

```
Full date range: [2020-01-01 → 2024-12-31] with windows=5

Window layout (each = 1 year of total/5):
  Win 0  test: [2020-10-01 → 2021-03-31]   (last 20% of window 0)
  Win 1  test: [2021-10-01 → 2022-03-31]
  Win 2  test: [2022-10-01 → 2023-03-31]
  Win 3  test: [2023-10-01 → 2024-03-31]
  Win 4  test: [2024-07-01 → 2024-12-31]

Each window runs a fresh BacktestRunner.runSync() on its test period only.
Returns one WalkForwardResult (windowIndex, fromDate, toDate, metrics) per window.
Consistent Sharpe > 0 across all windows = strategy is robust, not curve-fitted.
```

---

## 3. PERFORMANCE METRICS REFERENCE

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  Metric               │ Formula                        │  What it tells you   │
├───────────────────────────────────────────────────────────────────────────────┤
│  totalReturnPct       │ ΣreturnPct (all trades)         │  Raw P&L             │
│  annualizedReturnPct  │ (1+total)^(1/years)–1           │  CAGR                │
│  sharpeRatio          │ (μ–rf) / σ × √252               │  Risk-adj return     │
│                       │  μ=mean trade return, σ=std     │  >1 = good           │
│  maxDrawdownPct       │ max (peak–trough) / peak        │  Worst equity fall   │
│  winRatePct           │ wins / total × 100              │  % profitable trades │
│  avgWinPct            │ grossProfit / wins              │  Avg win size        │
│  avgLossPct           │ grossLoss / losses              │  Avg loss size       │
│  profitFactor         │ grossProfit / grossLoss         │  >1 = net profitable │
│  expectancy           │ avgWin×winRate – avgLoss×lossRate│ Avg $ per trade     │
│  totalTrades          │ count                           │  Sample size         │
│  winningTrades        │ count                           │                      │
│  losingTrades         │ count                           │                      │
│  avgHoldingDays       │ mean(holdingDays per trade)     │  Avg exposure time   │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. NEW GRAPHQL SCHEMA (Stage 6 additions)

```graphql
# ─── Input ────────────────────────────────────────────────────────────────────
input BacktestInput {
    symbols:            [String!]!
    market:             Market!
    fromDate:           Date!           # ISO-8601 "YYYY-MM-DD"
    toDate:             Date!
    modelVariant:       String          # "A" | "B"  (default "A")
    initialCapital:     BigDecimal      # virtual capital (default 100000)
    minConfidence:      Float           # min confidence% to enter (default 60)
    maxHoldingDays:     Int             # cut if not resolved (default 45)
    walkForward:        Boolean
    walkForwardWindows: Int             # rolling windows (default 5)
}

# ─── Types ────────────────────────────────────────────────────────────────────
type BacktestRun     { id, symbols, market, fromDate, toDate, modelVariant,
                       status, progressPercent, totalTrades, metrics, trades,
                       startedAt, completedAt, failureReason }
type BacktestMetrics { totalReturnPct, annualizedReturnPct, sharpeRatio,
                       maxDrawdownPct, winRatePct, avgWinPct, avgLossPct,
                       profitFactor, expectancy, totalTrades,
                       winningTrades, losingTrades, avgHoldingDays }
type BacktestTrade   { symbol, signal, entryDate, entryPrice, exitDate, exitPrice,
                       exitReason, returnPercent, profitable, holdingDays,
                       compositeScore, confidencePercent, targetPrice, stopLossPrice }
type WalkForwardResult { windowIndex, fromDate, toDate, metrics }

# ─── Mutations ────────────────────────────────────────────────────────────────
type Mutation {
    runBacktest(input: BacktestInput!): BacktestRun!
}

# ─── Queries ──────────────────────────────────────────────────────────────────
type Query {
    backtestRun(runId: String!):                         BacktestRun
    listBacktestRuns(market: Market):                    [BacktestRun!]!
    walkForwardValidation(input: BacktestInput!, windows: Int): [WalkForwardResult!]!
}

# ─── Subscriptions ────────────────────────────────────────────────────────────
type Subscription {
    backtestProgress(runId: String!): BacktestRun!
    # Flux completes automatically when status = COMPLETE or FAILED
}
```

---

## 5. HOW TO RUN

### 5.1 Start the application (dev mode, no Kafka required)

```bash
gradle bootRun --args='--spring.profiles.active=dev'
```

Startup log confirms Stage 6 tables created by Hibernate:

```
Hibernate: create table backtest_runs (...)
Hibernate: create table backtest_trades (...)
Hibernate: create table backtest_run_symbols (...)
[KafkaStreams] Processor 5 (Backtest Capture) NOT registered (hft.backtest.kafka-capture.enabled=false)
[KafkaStreams] Topology registered: QuoteKTable + CandleBuilder + SignalEnricher + MLRescorer + BacktestCapture
```

### 5.2 Launch a backtest via GraphiQL

Open `http://localhost:8080/graphiql` and run:

```graphql
mutation {
    runBacktest(input: {
        symbols:        ["AAPL", "MSFT", "NVDA", "GOOGL"]
        market:         US_NASDAQ
        fromDate:       "2022-01-01"
        toDate:         "2024-12-31"
        modelVariant:   "A"
        initialCapital: 100000
        minConfidence:  65.0
        maxHoldingDays: 45
    }) {
        id
        status
        progressPercent
    }
}
```

Expected response (immediately, run started async):

```json
{
    "data": {
        "runBacktest": {
            "id": null,
            "status": "PENDING",
            "progressPercent": 0
        }
    }
}
```

### 5.3 Subscribe to live progress

Open a second tab in GraphiQL:

```graphql
subscription {
    backtestProgress(runId: "8f3a1b2c-4d5e-6f7a-8b9c-0d1e2f3a4b5c") {
        id
        status
        progressPercent
        totalTrades
        metrics {
            winRatePct
            sharpeRatio
            maxDrawdownPct
            totalReturnPct
        }
    }
}
```

Progressive updates (one per symbol processed):

```json
{ "data": { "backtestProgress": { "status": "RUNNING",   "progressPercent": 25, "totalTrades": 0 } } }
{ "data": { "backtestProgress": { "status": "RUNNING",   "progressPercent": 50, "totalTrades": 7 } } }
{ "data": { "backtestProgress": { "status": "RUNNING",   "progressPercent": 75, "totalTrades": 14 } } }
{ "data": { "backtestProgress": { "status": "COMPLETE",  "progressPercent": 100, "totalTrades": 21,
    "metrics": {
        "winRatePct":     61.9,
        "sharpeRatio":    1.34,
        "maxDrawdownPct": 18.2,
        "totalReturnPct": 47.6
    }
} } }
```

Subscription auto-completes after the COMPLETE event.

### 5.4 Query results after completion

```graphql
query {
    backtestRun(runId: "8f3a1b2c-4d5e-6f7a-8b9c-0d1e2f3a4b5c") {
        status
        metrics {
            totalReturnPct
            annualizedReturnPct
            sharpeRatio
            maxDrawdownPct
            winRatePct
            avgWinPct
            avgLossPct
            profitFactor
            expectancy
            totalTrades
            winningTrades
            losingTrades
            avgHoldingDays
        }
        trades {
            symbol
            signal
            entryDate
            entryPrice
            exitDate
            exitPrice
            exitReason
            returnPercent
            profitable
            holdingDays
        }
    }
}
```

### 5.5 Compare Model A vs Model B on same period

Run two backtests with `modelVariant: "A"` and `modelVariant: "B"` over the same
date range, then compare results:

```graphql
query compareModels {
    modelA: backtestRun(runId: "run-id-for-model-a") {
        metrics { sharpeRatio winRatePct totalReturnPct maxDrawdownPct }
    }
    modelB: backtestRun(runId: "run-id-for-model-b") {
        metrics { sharpeRatio winRatePct totalReturnPct maxDrawdownPct }
    }
}
```

### 5.6 Walk-forward validation (anti-overfitting check)

```graphql
query {
    walkForwardValidation(
        input: {
            symbols:      ["RELIANCE", "TCS", "INFOSYS", "HDFC"]
            market:       INDIA_NSE
            fromDate:     "2020-01-01"
            toDate:       "2024-12-31"
            modelVariant: "B"
        }
        windows: 5
    ) {
        windowIndex
        fromDate
        toDate
        metrics {
            sharpeRatio
            winRatePct
            totalReturnPct
            maxDrawdownPct
        }
    }
}
```

Expected response (5 windows, each a 6-month test period):

```json
{
    "data": {
        "walkForwardValidation": [
            { "windowIndex": 0, "fromDate": "2020-10-01", "toDate": "2021-03-31",
              "metrics": { "sharpeRatio": 1.12, "winRatePct": 60.0, "totalReturnPct": 12.4 } },
            { "windowIndex": 1, "fromDate": "2021-10-01", "toDate": "2022-03-31",
              "metrics": { "sharpeRatio": 0.91, "winRatePct": 55.6, "totalReturnPct": 7.3  } },
            { "windowIndex": 2, "fromDate": "2022-10-01", "toDate": "2023-03-31",
              "metrics": { "sharpeRatio": 1.38, "winRatePct": 66.7, "totalReturnPct": 18.1 } },
            { "windowIndex": 3, "fromDate": "2023-10-01", "toDate": "2024-03-31",
              "metrics": { "sharpeRatio": 1.05, "winRatePct": 58.3, "totalReturnPct": 9.8  } },
            { "windowIndex": 4, "fromDate": "2024-07-01", "toDate": "2024-12-31",
              "metrics": { "sharpeRatio": 1.21, "winRatePct": 63.6, "totalReturnPct": 14.2 } }
        ]
    }
}
```

Sharpe > 0 and win rate consistently > 50% across all windows → strategy generalises.

### 5.7 List all past runs for a market

```graphql
query {
    listBacktestRuns(market: US_NASDAQ) {
        id
        modelVariant
        fromDate
        toDate
        status
        metrics { sharpeRatio winRatePct totalReturnPct }
    }
}
```

### 5.8 Enable Kafka signal capture (live outcome tracking)

```yaml
# application-prod.yml
hft:
  backtest:
    kafka-capture:
      enabled: true   # mirrors signals-ml-scored → backtest-results topic
```

Startup log confirms Processor 5:

```
[KafkaStreams] Processor 5 (Backtest Capture) registered → backtest-results
```

Consume from `backtest-results` to reconcile live signals against actual outcomes:

```bash
kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic backtest-results \
    --property print.key=true
```

### 5.9 Run all tests

```bash
gradle test
# Expected: BUILD SUCCESSFUL — all tests pass
```

---

## 6. EXPECTED OUTPUTS

### Application startup (Stage 6 tables visible)

```
Hibernate: create table backtest_runs (
    id varchar(36) not null,
    market varchar(20) not null,
    from_date date not null,
    to_date date not null,
    model_variant varchar(10),
    initial_capital numeric(20,2),
    status varchar(20) not null,
    progress_percent integer,
    total_bars_processed integer,
    total_trades_simulated integer,
    started_at timestamp,
    completed_at timestamp,
    -- BacktestMetrics @Embedded columns:
    total_return_pct float,
    annualized_return_pct float,
    sharpe_ratio float,
    max_drawdown_pct float,
    win_rate_pct float,
    ...
    primary key (id)
)
```

### BacktestRunner progress log (INFO level)

```
[Backtest] Run a1b2c3d4 — RUNNING symbol=AAPL (1/4)
[Backtest] AAPL — 18 trades simulated from 756 bars
[Backtest] Run a1b2c3d4 — RUNNING symbol=MSFT (2/4)
[Backtest] MSFT — 14 trades simulated from 756 bars
[Backtest] Run a1b2c3d4 — RUNNING symbol=NVDA (3/4)
[Backtest] NVDA — 22 trades simulated from 756 bars
[Backtest] Run a1b2c3d4 — RUNNING symbol=GOOGL (4/4)
[Backtest] GOOGL — 11 trades simulated from 756 bars
[Backtest] Run a1b2c3d4 complete: 65 trades | winRate=61.5% | sharpe=1.34 | maxDD=18.2%
```

### Trade record (BacktestTrade entity)

```
id:               1
symbol:           AAPL
signal:           BUY
entryDate:        2022-03-15
entryPrice:       154.73
exitDate:         2022-04-22
exitPrice:        172.57
exitReason:       TARGET_HIT
returnPercent:    11.52
profitable:       true
holdingDays:      28
compositeScore:   72.4
confidencePercent: 68.1
targetPrice:      172.57
stopLossPrice:    144.31
```

### BacktestMetrics (embedded in BacktestRun)

```
totalReturnPct:      47.6
annualizedReturnPct: 17.8
sharpeRatio:         1.34
maxDrawdownPct:      18.2
winRatePct:          61.5
avgWinPct:           12.3
avgLossPct:           5.8
profitFactor:         2.04   ← gross profit / gross loss
expectancy:           5.3    ← avg return per trade
totalTrades:          65
winningTrades:        40
losingTrades:         25
avgHoldingDays:       24
```

---

## 7. ENHANCEMENTS VS STAGE 5

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Capability              │  Before (Stage 5)          │  After (Stage 6)     │
├──────────────────────────────────────────────────────────────────────────────┤
│  Signal validation       │  Live only, no history     │  Historical backtest │
│  Model comparison        │  A/B routing (live)        │  A/B on same data    │
│  Overfitting risk        │  Unknown                   │  Walk-forward test   │
│  Performance metrics     │  hit rate + avg return     │  + Sharpe, drawdown, │
│                          │  (per model, cumulative)   │  profit factor, CAGR │
│  Trade record            │  None                      │  @Entity, full audit │
│  Async progress          │  None                      │  Subscription updates│
│  Kafka topics            │  4 (incl. ml-scored)       │  5 (+ backtest-results)│
│  JPA tables              │  12 entities               │  14 (+run, +trade)   │
│  GraphQL Mutations       │  recordSignalOutcome        │  + runBacktest       │
│  GraphQL Subscriptions   │  liveQuote, liveSignals,   │  + backtestProgress  │
│                          │  watchlistSignals           │                      │
│  Inline TA engine        │  None                      │  RSI, SMA, EMA, ATR, │
│                          │  (needed external service) │  BB — from raw OHLCV │
│  OHLCVDataRepository     │  Missing (entity existed)  │  Added               │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. CONFIGURATION REFERENCE

```yaml
hft:
  backtest:
    interval-type: 1D          # OHLCV interval for simulation ("1D", "1W", etc.)
    kafka-capture:
      enabled: false           # true → mirrors signals-ml-scored → backtest-results
```

All other backtest parameters are passed per-run in the `BacktestInput` GraphQL argument:

| Parameter         | Default | Description                                    |
|-------------------|--------:|------------------------------------------------|
| `modelVariant`    | `"A"`   | `"A"` = WeightedComposite, `"B"` = Ensemble   |
| `initialCapital`  | 100,000 | Virtual capital for equity curve tracking      |
| `minConfidence`   | 60.0    | Minimum confidence % to act on a BUY signal   |
| `maxHoldingDays`  | 45      | Max bars to hold before TIME_EXPIRY            |
| `walkForwardWindows` | 5    | Rolling windows in walk-forward validation     |

---

## 9. NEW FILES SUMMARY

```
src/main/java/com/hft/backtest/
  BacktestConfig.java          record — immutable run input (symbols, dates, model, thresholds)
  BacktestRun.java             @Entity — status tracker with @Embedded BacktestMetrics
  BacktestTrade.java           @Entity — per-trade outcome record
  BacktestMetrics.java         @Embeddable — 12-field metric result
  BacktestRunRepository.java   findByMarket, findTop20ByOrderByStartedAtDesc
  BacktestTradeRepository.java findByRunId, findByRunIdAndProfitable
  StrategyMetricsEngine.java   Sharpe (annualized), max drawdown, win rate, profit factor
  WalkForwardValidator.java    80/20 window split, runs BacktestRunner.runSync per window
  BacktestRunner.java          @Async orchestrator, inline TA (RSI/SMA/EMA/ATR/BB), Model A/B dispatch

src/main/java/com/hft/graphql/
  BacktestResolver.java        @MutationMapping(runBacktest), @QueryMapping(backtestRun,
                               listBacktestRuns, walkForwardValidation),
                               @SubscriptionMapping(backtestProgress)

src/main/java/com/hft/repository/
  OHLCVDataRepository.java     findBySymbolAndMarketAndIntervalTypeAndBarDateBetween

Modified:
  StreamSinkBridge.java        +emitBacktestProgress(), +backtestFlux(runId)
  KafkaStreamsTopology.java    Processor 5 (backtest-capture, conditional)
  KafkaConfig.java             TOPIC_BACKTEST_RESULTS + @Bean backtestResultsTopic()
  schema.graphqls              BacktestInput, BacktestRun, BacktestTrade, BacktestMetrics,
                               WalkForwardResult types; Mutation.runBacktest;
                               Query.backtestRun/listBacktestRuns/walkForwardValidation;
                               Subscription.backtestProgress
  application.yml              hft.backtest config block
  docs/STAGES_OVERVIEW.md      Stage 6 entries added
```

---

## 10. PORT AND TOPIC MAP (cumulative through Stage 6)

```
HTTP  :8080   /graphql           — Queries + Mutations (incl. runBacktest)
              /graphql-ws        — Subscriptions (incl. backtestProgress)
              /graphiql          — Dev IDE
              /actuator/prometheus — hft_ml_* metrics (Stages 5–6)

gRPC  :9090                     — MarketData, Signal, Portfolio services

Kafka Topics (5 stream outputs + 1 capture):
  market-data-raw     → [P1] → quotes-aggregated
                      → [P2] → candles-1m
  trading-signals     → [P3] → signals-enriched
  signals-enriched    → [P4] → signals-ml-scored    (ML re-score)
  signals-ml-scored   → [P5] → backtest-results     (capture, conditional)

DB Tables (14 JPA entities after Stage 6):
  stock_quotes, technical_indicators, sentiment_data, fundamental_data,
  macro_data, trade_recommendations, ohlcv_data, portfolio_positions,
  ipo_data, reco_reasons, reco_risks, reco_news, reco_data_sources,
  backtest_runs, backtest_run_symbols, backtest_trades              ← Stage 6
```
