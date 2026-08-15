# STAGE 5 — Real-Time ML Scoring Pipeline & A/B Model Testing
## HFT Market Intelligence Platform

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  Stage 5: ML Model Integration                                                   ║
║                                                                                  ║
║  What's new:                                                                     ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  MODEL A (existing)          MODEL B (new — EnsembleModel)             │     ║
║  │  Weighted Composite Score    Momentum + MeanReversion + Trend          │     ║
║  │  Linear Regression target    Regime-aware blending (VIX-driven)       │     ║
║  │  ATR stop-loss              Cross-signal confirmation bonus            │     ║
║  └──────────────────┬────────────────────────┬────────────────────────────┘     ║
║                     │                        │                                   ║
║              ┌──────▼────────────────────────▼──────┐                           ║
║              │        ModelABRouter                  │                           ║
║              │  consistent hash per symbol           │                           ║
║              │  hft.ml.model-router.model-b-fraction │                           ║
║              └──────────────┬────────────────────────┘                           ║
║                             │                                                    ║
║              ┌──────────────▼────────────────────────┐                           ║
║              │     ModelPerformanceTracker            │                           ║
║              │  Micrometer gauges + Redis TTL-90d    │                           ║
║              │  hft_ml_hit_rate{model="A|B"}         │                           ║
║              │  hft_ml_avg_return_pct{model="A|B"}   │                           ║
║              └──────────────┬────────────────────────┘                           ║
║                             │                                                    ║
║  Kafka Streams Processor 4  │                                                    ║
║  signals-enriched → ML rescore (60% original + 40% ensemble) → signals-ml-scored║
╚══════════════════════════════════════════════════════════════════════════════════╝
```

---

## Architecture

### Full Scoring Pipeline

```
RecommendationEngine
  Step 1: MarketData → StockQuote
  Step 2: TechnicalAnalysis → TechnicalIndicators
  Step 3: SentimentAnalysis → SentimentData
  Step 4: FundamentalAnalysis → FundamentalData
  Step 5: MacroGeopolitical → MacroData
  Step 6: ModelABRouter.route() ◄─── NEW in Stage 5
           ├── selectModelB(symbol)  →  consistent hash, fraction-based
           ├── Model A: MLPredictionService.predict()
           │    └── weights: TA 35%, FA 25%, sentiment 20%, macro 15%, ML 5%
           │    └── linear regression on 30 OHLCV bars for price target
           └── Model B: EnsembleModel.computeScore()
                └── 3 sub-models blended by VIX-driven market regime:
                     MomentumModel   — RSI, MACD, volume, OBV
                     MeanReversion   — BB position, 52-week range
                     TrendModel      — SMA alignment, macro, FII flow
  Step 7-10: Risk filters → signal → recommendation assembly

Kafka Streams Topology — Processor 4 (new):
  signals-enriched  →  [mlRescore()]  →  signals-ml-scored
        │                    │
        │         Builds proxy MLFeatureVector from
        │         embedded TA/FA/sentiment/macro scores,
        │         blends 60% original + 40% EnsembleModel score
        └─────────────────────────────────────────────────────────

GraphQL (new):
  Query:    modelPerformance(model: "A"|"B") → ModelPerformance
  Query:    modelAssignment(symbol: String!)  → "A_WEIGHTED_COMPOSITE" | "B_ENSEMBLE"
  Mutation: recordSignalOutcome(...)          → ModelPerformance
```

---

## Feature Engineering

### MLFeatureVector (41 features)

```
┌─────────────────────────────────────────────────────────────────┐
│  Group         │ Count │ Key Features                           │
├─────────────────────────────────────────────────────────────────┤
│  Technical     │  14   │ rsi14, macdHistogram, bbPosition,      │
│                │       │ smaAlignment, volumeRatio, obvTrend,   │
│                │       │ atrNormalized, sma20/50/200Distance    │
├─────────────────────────────────────────────────────────────────┤
│  Fundamental   │   8   │ peRatioNorm, pbRatio, roe,             │
│                │       │ debtToEquity, revenueGrowth,           │
│                │       │ epsGrowth, dividendYield, fundScore    │
├─────────────────────────────────────────────────────────────────┤
│  Sentiment     │   7   │ sentimentRaw (-1..+1), bullishPct,     │
│                │       │ newsCountLog, mentionsLog,             │
│                │       │ sentimentMomentum, normalizedSentiment │
├─────────────────────────────────────────────────────────────────┤
│  Macro         │   7   │ vixLevel, marketRegime (0/0.5/1),     │
│                │       │ gdpGrowth, inflationRate, bankRate,    │
│                │       │ fiiFlowNorm, macroScore                │
├─────────────────────────────────────────────────────────────────┤
│  Price         │   5   │ percentFrom52High, percentFrom52Low,  │
│                │       │ dayChangePct, volumeSpike, marketCap  │
└─────────────────────────────────────────────────────────────────┘
```

### EnsembleModel Sub-models

```
MomentumScore (0–100)
  RSI < 30  → +15  |  RSI > 70  → –12
  MACD hist > 0 → +8  |  < 0 → –6
  volumeRatio > 1.5 → +6
  OBV uptrend → +7  |  downtrend → –7
  dayChangePct contributes ×3

MeanReversionScore (0–100)
  bbPosition < 0.15 (near lower band) → +18
  bbPosition > 0.85 (near upper band) → –15
  Near 52-week low (< 5%) → +10
  Near 52-week high (within 5%) → –8
  Wide BB (> 8%) → –5 (reversion unreliable in volatile markets)

TrendScore (0–100)
  SMA bull alignment (price > ema9 > sma20 > sma50 > sma200) → +20
  Above 200-day MA → +8  |  below → –10
  macroScore contribution: (score – 50) × 0.4
  fundamentalScore contribution: (score – 50) × 0.3
  FII flow: norm × 8

Regime blending:
  Bull (VIX < 15)  → [0.50, 0.15, 0.35]   momentum-led
  Neutral          → [0.33, 0.33, 0.34]   balanced
  Bear  (VIX > 25) → [0.20, 0.40, 0.40]   reversion + trend

Bonuses/Penalties applied on top:
  Confirmation bonus (max +8): TA+Sentiment align, FA+TA align, volume+OBV confirm
  Conflict penalty  (max –12): RSI overbought + euphoria, macro headwind, distribution
```

---

## How to Run

### 1. Default dev (10% routing to Model B)

```bash
gradle bootRun --args='--spring.profiles.active=dev'
```

Startup log will confirm Processor 4:
```
[KafkaStreams] Processor 4 (ML Re-scorer) registered → signals-ml-scored
[KafkaStreams] Topology registered: QuoteKTable + CandleBuilder + SignalEnricher + MLRescorer
```

### 2. Route all traffic to Model B (testing/validation)

```bash
gradle bootRun --args='--spring.profiles.active=dev --hft.ml.model-router.model-b-fraction=1.0'
```

### 3. Disable Model B (Stage 4 behavior)

```bash
gradle bootRun --args='--spring.profiles.active=dev --hft.ml.model-router.model-b-fraction=0.0'
```

### 4. Check which model a symbol routes to

```graphql
# GraphiQL → http://localhost:8080/graphiql
query {
    modelAssignment(symbol: "AAPL")
}
```

Expected response:
```json
{
    "data": {
        "modelAssignment": "B_ENSEMBLE"
    }
}
```

### 5. View live model performance

```graphql
query {
    modelPerformance(model: "B") {
        model
        totalPredictions
        correctPredictions
        hitRatePct
        avgReturnPct
    }
}
```

Expected response:
```json
{
    "data": {
        "modelPerformance": {
            "model": "B",
            "totalPredictions": 47,
            "correctPredictions": 31,
            "hitRatePct": 65.96,
            "avgReturnPct": 3.82
        }
    }
}
```

### 6. Record a signal outcome (feedback loop)

```graphql
mutation {
    recordSignalOutcome(
        symbol: "RELIANCE"
        market: INDIA_NSE
        model: "B"
        actualReturnPercent: 8.5
        wasBullishCall: true
    ) {
        model
        totalPredictions
        hitRatePct
        avgReturnPct
    }
}
```

### 7. Prometheus metrics (ML model accuracy)

```bash
curl http://localhost:8080/actuator/prometheus | grep hft_ml
```

Expected output:
```
# HELP hft_ml_hit_rate Prediction hit rate — correct / total predictions
# TYPE hft_ml_hit_rate gauge
hft_ml_hit_rate{model="A",} 0.0
hft_ml_hit_rate{model="B",} 0.0

# HELP hft_ml_avg_return_pct Average actual return % for bullish predictions
# TYPE hft_ml_avg_return_pct gauge
hft_ml_avg_return_pct{model="A",} 0.0
hft_ml_avg_return_pct{model="B",} 0.0

# HELP hft_ml_predictions_total
# TYPE hft_ml_predictions_total counter
hft_ml_predictions_total{model="A",signal="STRONG_BUY",} 12.0
hft_ml_predictions_total{model="B",signal="BUY",} 5.0
```

### 8. Run all tests

```bash
gradle test
# Expected: BUILD SUCCESSFUL — all tests pass
```

### 9. Inspect ML-scored signals from Kafka

After Kafka is running (see Stage 3 Docker Compose):
```bash
# Consume from the new signals-ml-scored topic
kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic signals-ml-scored \
    --from-beginning \
    --property print.key=true

# Publish a test signal to trigger Processor 4
kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic signals-enriched \
    --property parse.key=true \
    --property key.separator=:
# Then type: AAPL:{"symbol":"AAPL","market":"US_NASDAQ","compositeScore":72.5,"confidencePercent":68.0,"technicalScore":75.0,"fundamentalScore":68.0,"sentimentScore":55.0,"macroScore":60.0}
```

---

## Expected Outputs

### Application startup (Stage 5 features active)

```
[ModelPerformanceTracker] Registered Gauge: hft.ml.hit.rate{model=A}
[ModelPerformanceTracker] Registered Gauge: hft.ml.hit.rate{model=B}
[ModelPerformanceTracker] Registered Gauge: hft.ml.avg.return.pct{model=A}
[ModelPerformanceTracker] Registered Gauge: hft.ml.avg.return.pct{model=B}
[KafkaStreams] Processor 4 (ML Re-scorer) registered → signals-ml-scored
[KafkaStreams] Topology registered: QuoteKTable + CandleBuilder + SignalEnricher + MLRescorer
```

### A/B routing log (DEBUG level)

```
[A/B] AAPL → ModelA: score=72.5 conf=68.0%
[A/B] MSFT → ModelA: score=79.1 conf=74.2%
[A/B] NVDA → ModelB: score=81.3 conf=76.5%
[A/B] RELIANCE → ModelA: score=65.4 conf=63.1%
```

### ML re-scored signal (Kafka topic: signals-ml-scored)

```json
{
    "symbol": "AAPL",
    "market": "US_NASDAQ",
    "signal": "BUY",
    "compositeScore": 74.8,
    "confidencePercent": 70.1,
    "mlScore": 79.3,
    "technicalScore": 75.0,
    "fundamentalScore": 68.0,
    "sentimentScore": 55.0,
    "macroScore": 60.0,
    "currentPrice": 189.25,
    "targetPrice": 213.45,
    "stopLossPrice": 181.30
}
```

Note: `compositeScore` blended = `72.5 × 0.60 + EnsembleScore × 0.40`, `mlScore` updated to Ensemble output.

---

## Enhancements vs Stage 4

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Capability                │  Before (Stage 4)        │  After (Stage 5)   │
├─────────────────────────────────────────────────────────────────────────────┤
│  ML scoring model          │  Single model (Model A)  │  A/B tested        │
│  Market regime awareness   │  Static VIX weight adj.  │  Dynamic regime    │
│                            │                          │  blend (3 regimes) │
│  Feature engineering       │  5 score inputs          │  41-feature vector │
│  Kafka pipeline stages     │  3 (quote/candle/signal) │  4 + ML rescore    │
│  Output topics             │  3 (Stage 3)             │  4 incl. ml-scored │
│  Model accuracy tracking   │  None                    │  hit rate + avg    │
│                            │                          │  return / Micrometer│
│  GraphQL schema            │  Query + Subscription    │  + Mutation type   │
│  Observable routing        │  None                    │  modelAssignment   │
│                            │                          │  query per symbol  │
│  Feedback loop             │  None                    │  recordSignalOutcome│
│                            │                          │  mutation          │
│  Redis model storage       │  Pub/Sub events          │  + prediction meta │
│                            │                          │  (TTL 90 days)     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## New Classes Reference

```
com.hft.ml/
├── MLFeatureVector.java         — 41-feature record (Lombok @Data @Builder)
├── MLFeatureExtractor.java      — maps domain objects → MLFeatureVector
│                                  null-safe; neutral defaults on missing data
├── EnsembleModel.java           — Model B (3 sub-models, regime-aware blending)
│                                  computeScore() + computeConfidence()
├── ModelABRouter.java           — consistent-hash routing between A and B
│                                  hft.ml.model-router.model-b-fraction
└── ModelPerformanceTracker.java — Micrometer counters/gauges + Redis TTL-90d

com.hft.graphql/
└── MLResolver.java              — @QueryMapping(modelPerformance, modelAssignment)
                                   @MutationMapping(recordSignalOutcome)

com.hft.streams/KafkaStreamsTopology.java  — Processor 4: mlRescore() added
com.hft.service/signal/RecommendationEngine.java — mlRouter injected (optional)
com.hft.config/KafkaConfig.java            — TOPIC_SIGNALS_ML_SCORED + @Bean
```

---

## A/B Model Routing Logic

```
symbol: "AAPL"
  hash: Math.abs("AAPL".hashCode()) % 100 → 65
  modelBFraction: 0.10 → threshold 10
  65 >= 10 → Model A ✓

symbol: "NVDA"
  hash: Math.abs("NVDA".hashCode()) % 100 → 8
  8 < 10 → Model B ✓
```

The hash is stable: the same symbol always maps to the same model as long as
`model-b-fraction` is unchanged. This ensures:
- Model B sees consistent symbols across time (valid comparison baseline)
- No leakage between cohorts (AAPL never accidentally switches models)

To change the split gradually in production:
```yaml
# application-prod.yml
hft:
  ml:
    model-router:
      model-b-fraction: 0.25   # ramp up from 10% to 25%
```

---

## Production Env Vars (Stage 5 adds none — all configurable via YAML)

Stage 5 introduces no new secrets. All ML config is property-based:

| Property                              | Default | Description                        |
|---------------------------------------|--------:|------------------------------------|
| `hft.ml.model-router.model-b-fraction`| 0.10   | Fraction of symbols to Model B     |
| `hft.ml.ensemble.momentum-weight`     | 0.33   | Default MomentumModel weight       |
| `hft.ml.ensemble.reversion-weight`    | 0.33   | Default MeanReversion weight       |
| `hft.ml.ensemble.trend-weight`        | 0.34   | Default TrendModel weight          |

The regime weights override these three defaults dynamically based on VIX.
The YAML weights only apply in neutral (VIX 15–25) regime.

---

## Port and Topic Map (cumulative through Stage 5)

```
HTTP  :8080   /graphql          — GraphQL (queries + mutations + subscriptions)
              /graphiql          — Dev UI
              /actuator/prometheus — Micrometer metrics incl. hft_ml_*
gRPC  :9090                    — MarketData, Signal, Portfolio services

Kafka Topics:
  market-data-raw      → [Processor 1] → quotes-aggregated
                       → [Processor 2] → candles-1m
  trading-signals      → [Processor 3] → signals-enriched
  signals-enriched     → [Processor 4] → signals-ml-scored   ← NEW Stage 5
```
