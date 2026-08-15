# SESSION CONTEXT — HFT Market Intelligence Platform

**Resume file — created 2026-08-16 before IDE restart**
*Author: PTD2315 | Branch: DS_coding | Base: main*

---

## 1. PROJECT IDENTITY

```
Project:   HFT Market Intelligence Platform (HMIP)
Directory: /Users/avinash/Downloads/Documents/VSJava/HFT_MarketIntelligence
Branch:    DS_coding
Main:      main
GitHub:    https://github.com/omjee01/HFT_MarketIntelligence

Tech stack:
  Java 21
  Spring Boot 3.2.5
  Gradle 9 (build.gradle.kts)
  H2 (dev DB, in-memory)
  PostgreSQL (prod)
  Apache Kafka + Kafka Streams
  Redis (pub/sub fan-out + ML prediction TTL cache)
  gRPC (Netty, port 9090)
  Spring GraphQL (port 8080)
  Micrometer + Prometheus
  Lombok + Jackson
  Apache Commons Math 3 (SimpleRegression)
  JWT (JJWT)
```

---

## 2. GIT STATE

```
Last committed stages: Stages 1–4 are committed. Stages 5–6 are implemented but NOT YET committed.

Commits on DS_coding:
  bcca74c  docs: Stage 4 documentation + STAGES_OVERVIEW update
  adc400d  Stage 4: multi-node production hardening — all 24 tests pass
  dfdff9a  Stage 3: Kafka Streams stateful processing pipeline
  294b8b4  Stage 2: gRPC internal pipeline — Protobuf schema + service implementations
  cbdf4d2  Stage 1: GraphQL layer — queries, subscriptions, scalar config
  8e8b11c  v0.0.01 — HFT Market Intelligence Platform initial release

ACTION NEEDED after IDE restart:
  git add -p   (stage Stages 5 and 6 files selectively)
  git commit -m "Stage 5: ML A/B routing — EnsembleModel, 41-feature vector, Processor 4, Mutation type"
  git commit -m "Stage 6: Backtesting engine — BacktestRunner, StrategyMetrics, WalkForward, Processor 5"
```

---

## 3. WHAT HAS BEEN BUILT — STAGE SUMMARY

### Foundation (committed in v0.0.01)
Spring Boot + JPA + Kafka + Redis + JWT + Swagger. Domain model, REST controllers,
repositories, service layer skeleton.

### Stage 1 — GraphQL API (commit cbdf4d2)
- `spring-boot-starter-graphql` + `graphql-java-extended-scalars:22.0`
- `src/main/resources/graphql/schema.graphqls` — full SDL
- 5 resolvers: StockDashboard, MarketData, Analysis, Recommendation, SignalSubscription
- GraphiQL at `/graphiql`, WebSocket subscriptions at `/graphql-ws`
- Bug fix: `sentimentService.analyzeSentiment()` (not `.analyze()`); duplicate `spring:` key in yml

### Stage 2 — gRPC Internal Pipeline (commit 294b8b4)
- grpc-netty-shaded + grpc-protobuf + grpc-stub 1.65.0; protoc 3.25.5
- 4 proto files: `common.proto`, `market_data.proto`, `analysis.proto`, `signal.proto`
- `ProtoMapper.java` — null-safe domain ↔ proto
- 3 gRPC service impls: `MarketDataGrpcService`, `AnalysisGrpcService`, `SignalGrpcService`
- `GrpcServerConfig.java` (SmartLifecycle, port 9090)
- CVE-2024-7254: forced `protobuf-java:3.25.5` via resolutionStrategy
- Bug fix: AssetType enum — removed EQUITY/INDEX/FUTURES; added STOCK/OPTION/COMMODITY/IPO/ETF

### Stage 3 — Kafka Streams (commit dfdff9a)
- `@EnableKafkaStreams` + kafka-streams dependency
- `KafkaStreamsTopology.java` — 3 processors at `@PostConstruct`:
  - P1 QuoteKTable: `market-data-raw` → `reduce(latest)` → `quotes-aggregated`
  - P2 CandleBuilder: 1-min tumbling window OHLCV → `candles-1m`
  - P3 SignalEnricher: `trading-signals` leftJoin quoteKTable → `signals-enriched`
- `StreamSinkBridge.java` — Reactor Sinks.Many multicast; GraphQL + gRPC consumers

### Stage 4 — Production Hardening (commit adc400d)
- `RedisPubSubBridge.java` — `@ConditionalOnProperty(hft.redis-pubsub.enabled=true)`
  KafkaStreamsTopology routes emitXxx() via Redis when bridge is active
- `GrpcAuthInterceptor.java` — JWT ServerInterceptor; toggle `grpc.server.auth.enabled`
- `GrpcServerConfig.java` — TLS via `NettyServerBuilder.useTransportSecurity()` (cert/key via env vars)
- `GraphQLInstrumentationConfig.java` — MaxQueryDepth(10) + MaxQueryComplexity(200)
  CRITICAL: import is `org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer`
  NOT `org.springframework.graphql.execution.GraphQlSourceBuilderCustomizer`
- `KafkaStreamsMetricsRegistrar.java` — binds KafkaStreams → Micrometer on ApplicationReadyEvent
- `application-prod.yml` — EOS (exactly_once_v2), Redis SSL, Kafka SSL, all secrets via env vars
- `micrometer-registry-prometheus` added to build.gradle.kts

### Stage 5 — ML Model Integration (NOT YET committed)
New files:
- `com/hft/ml/MLFeatureVector.java` — 41-feature @Data @Builder record
  Groups: Technical(14), Fundamental(8), Sentiment(7), Macro(7), Price(5)
- `com/hft/ml/MLFeatureExtractor.java` — domain objects → MLFeatureVector, null-safe
  Field name gotchas: `MacroData.interestRate` (not centralBankRate),
  `MacroData.gdpGrowthRateYoY` (not gdpGrowthRate), `MacroData.cpiInflationRate`,
  `MacroData.fiiNetFlowCrores` (BigDecimal), `SentimentData.overallSentimentScore`
- `com/hft/ml/EnsembleModel.java` — Model B: Momentum + MeanReversion + Trend sub-models
  Regime weights: bull(VIX<15)=[0.50,0.15,0.35], neutral=[0.33,0.33,0.34], bear(VIX>25)=[0.20,0.40,0.40]
  Confirmation bonus max +8; conflict penalty max -12; confidence clamped 25–93
- `com/hft/ml/ModelABRouter.java` — consistent-hash routing: `Math.abs(symbol.hashCode()) % 100`
  Default 10% to Model B (`hft.ml.model-router.model-b-fraction=0.10`)
  Same symbol always hits the same model (stable hash)
- `com/hft/ml/ModelPerformanceTracker.java` — explicit constructor (not @RequiredArgsConstructor)
  Registers Micrometer Gauges in constructor
  `hft_ml_hit_rate{model}` + `hft_ml_avg_return_pct{model}`
  Redis TTL-90d prediction metadata
- `com/hft/graphql/MLResolver.java` — @QueryMapping(modelPerformance, modelAssignment)
  @MutationMapping(recordSignalOutcome)

Modified:
- `RecommendationEngine.java` — `@Autowired(required=false) private ModelABRouter mlRouter`
  (non-final, compatible with @RequiredArgsConstructor which only generates for final fields)
  Step 6: `mlRouter != null ? mlRouter.route(...) : mlService.predict(...)`
- `KafkaStreamsTopology.java` — Processor 4: reads `signals-enriched`, calls `mlRescore()`,
  writes `signals-ml-scored`. 60% original + 40% ensemble blend (no external calls in Streams thread)
- `KafkaConfig.java` — `TOPIC_SIGNALS_ML_SCORED` (16 partitions) + `@Bean signalsMlScoredTopic()`
  Also added `TOPIC_BACKTEST_RESULTS` (8 partitions) for Stage 6
- `schema.graphqls` — ModelPerformance type, Mutation type (first time), modelPerformance +
  modelAssignment queries, recordSignalOutcome mutation
- `application.yml` — `hft.ml.model-router.model-b-fraction: 0.10`; ensemble weights

### Stage 6 — Backtesting Engine (NOT YET committed)
New files:
- `com/hft/backtest/BacktestConfig.java` — record, compact constructor with validation
  Defaults: modelVariant="A", initialCapital=100000, minConfidence=60.0, maxHoldingDays=45
- `com/hft/backtest/BacktestRun.java` — @Entity, table=backtest_runs
  @ElementCollection symbols in backtest_run_symbols
  @Embedded BacktestMetrics; @OneToMany(cascade=ALL) trades (lazy)
  status: PENDING/RUNNING/COMPLETE/FAILED; @PrePersist sets UUID
- `com/hft/backtest/BacktestTrade.java` — @Entity, table=backtest_trades
  exitReason: TARGET_HIT | STOP_HIT | TIME_EXPIRY
- `com/hft/backtest/BacktestMetrics.java` — @Embeddable, 12 metrics
  totalReturnPct, annualizedReturnPct, sharpeRatio, maxDrawdownPct, winRatePct,
  avgWinPct, avgLossPct, profitFactor, expectancy, totalTrades, winningTrades, losingTrades, avgHoldingDays
- `com/hft/backtest/BacktestRunRepository.java` + `BacktestTradeRepository.java`
- `com/hft/backtest/StrategyMetricsEngine.java` — RISK_FREE_RATE=0.05
  Sharpe: `(mean - riskFreePerTrade) / stdDev × √252`
  Max drawdown: peak-to-trough on running equity starting at 100.0
  CAGR: `(1 + totalReturn/100)^(1/years) - 1`
- `com/hft/backtest/WalkForwardResult.java` — record(windowIndex, fromDate, toDate, metrics)
- `com/hft/backtest/WalkForwardValidator.java` — N rolling windows, 80/20 warmup/test split
  Calls `runner.runSync(windowConfig, "WF-" + i)` per window
- `com/hft/backtest/BacktestRunner.java` — @Service @RequiredArgsConstructor
  `@Autowired(required=false) EnsembleModel ensembleModel`
  WARMUP_BARS=20; fetches 200 extra days before fromDate for TA warmup
  `runAsync()` → @Async("analysisExecutor"); `runSync()` → @Transactional
  Inline TA: RSI-14, SMA20/50/200, EMA9, ATR14, BB upper/lower (pure math, no service calls)
  Entry at next-bar open; exit: TARGET_HIT / STOP_HIT / TIME_EXPIRY
- `com/hft/graphql/BacktestResolver.java` — @MutationMapping runBacktest (async, PENDING immediately)
  @QueryMapping backtestRun, listBacktestRuns, walkForwardValidation
  @SubscriptionMapping backtestProgress — takeUntil(COMPLETE|FAILED)
- `com/hft/repository/OHLCVDataRepository.java` — WAS MISSING despite OHLCVData being a @Entity
  `findBySymbolAndMarketAndIntervalTypeAndBarDateBetweenOrderByBarDate`
  `findDistinctSymbolsByMarketAndInterval` + `findEarliestDate` + `findLatestDate`

Modified:
- `StreamSinkBridge.java` — added `emitBacktestProgress(BacktestRun)` + `backtestFlux(runId)`
- `KafkaStreamsTopology.java` — Processor 5 (backtest-capture, conditional on flag)
- `schema.graphqls` — BacktestInput, BacktestRun, BacktestTrade, BacktestMetrics, WalkForwardResult types;
  runBacktest mutation; backtestRun/listBacktestRuns/walkForwardValidation queries;
  backtestProgress subscription
- `application.yml` — `hft.backtest.interval-type: 1D` + `hft.backtest.kafka-capture.enabled: false`

---

## 4. COMPLETE JAVA FILE INVENTORY

```
com.hft (root)
  HFTApplication.java

com.hft.config
  AppConfig.java              (@Async executor beans: analysisExecutor)
  CacheConfig.java            (Spring Cache, simple/redis)
  KafkaConfig.java            (@EnableKafkaStreams, all topic @Beans)
  SecurityConfig.java         (JWT filter chain, permitAll for GraphQL/gRPC)
  SwaggerConfig.java          (SpringDoc OpenAPI)
  WebSocketConfig.java        (STOMP WebSocket for legacy WS endpoint)

com.hft.controller
  MarketDataController.java   (GET /market/quote/{symbol}, /quotes)
  AnalysisController.java     (GET /analysis/technical, /sentiment, /fundamental)
  RecommendationController.java (GET /recommendations/daily, /screen)
  GlobalExceptionHandler.java (@ControllerAdvice)

com.hft.graphql
  GraphQLConfig.java               (scalar registrations: DateTime, BigDecimal, Long, Date)
  GraphQLInstrumentationConfig.java (MaxQueryDepth + MaxQueryComplexity)
  StockDashboardResolver.java      (@QueryMapping stockDashboard)
  MarketDataResolver.java          (@QueryMapping quote, quotes)
  AnalysisResolver.java            (@QueryMapping technical, sentiment, fundamental, macro)
  RecommendationResolver.java      (@QueryMapping recommendation, screenStocks, activeRecommendations)
  SignalSubscriptionResolver.java  (@SubscriptionMapping liveQuote, liveSignals, watchlistSignals)
  MLResolver.java                  (@QueryMapping modelPerformance, modelAssignment)
                                   (@MutationMapping recordSignalOutcome)
  BacktestResolver.java            (@MutationMapping runBacktest)
                                   (@QueryMapping backtestRun, listBacktestRuns, walkForwardValidation)
                                   (@SubscriptionMapping backtestProgress)

com.hft.grpc
  GrpcServerConfig.java       (SmartLifecycle, NettyServerBuilder, port 9090, TLS)
  GrpcAuthInterceptor.java    (JWT ServerInterceptor, @ConditionalOnProperty)
  ProtoMapper.java            (null-safe domain ↔ proto conversion)
  MarketDataGrpcService.java  (GetQuote, BatchQuotes, StreamQuotes)
  AnalysisGrpcService.java    (GetTechnical, GetSentiment, GetFundamental, GetMacro)
  SignalGrpcService.java      (GetRecommendation, ScreenStocks, StreamSignals)

com.hft.metrics
  KafkaStreamsMetricsRegistrar.java (Micrometer binding on ApplicationReadyEvent)

com.hft.ml
  MLFeatureVector.java        (41-feature record)
  MLFeatureExtractor.java     (domain → feature vector, null-safe)
  EnsembleModel.java          (Model B: 3 sub-models, regime weighting)
  ModelABRouter.java          (consistent-hash A/B routing, 10% to Model B)
  ModelPerformanceTracker.java (Micrometer gauges + Redis TTL-90d)

com.hft.model.domain
  StockQuote.java, TechnicalIndicators.java, SentimentData.java,
  FundamentalData.java, MacroData.java, TradeRecommendation.java,
  OHLCVData.java, PortfolioPosition.java, IPOData.java

com.hft.model.enums
  Market.java (US_NYSE, US_NASDAQ, US_AMEX, INDIA_NSE, INDIA_BSE, INDIA_MCX)
  AssetType.java, SignalType.java, RiskLevel.java, TrendDirection.java,
  SentimentLabel.java, TimeHorizon.java

com.hft.model.dto
  ApiResponse.java, QuoteResponse.java, RecommendationRequest.java,
  RecommendationResponse.java, ScreenerRequest.java

com.hft.repository
  StockQuoteRepository.java, TechnicalIndicatorsRepository.java,
  FundamentalDataRepository.java, OHLCVDataRepository.java (added Stage 6),
  TradeRecommendationRepository.java, PortfolioPositionRepository.java,
  IPODataRepository.java

com.hft.backtest (Stage 6)
  BacktestConfig.java, BacktestRun.java, BacktestTrade.java,
  BacktestMetrics.java, BacktestRunRepository.java, BacktestTradeRepository.java,
  StrategyMetricsEngine.java, WalkForwardResult.java,
  WalkForwardValidator.java, BacktestRunner.java

com.hft.service.analysis
  TechnicalAnalysisService.java, SentimentAnalysisService.java,
  FundamentalAnalysisService.java, MacroGeopoliticalService.java

com.hft.service.data
  MarketDataAggregatorService.java, MarketDataService.java,
  AlphaVantageService.java, NSEIndiaService.java

com.hft.service.ml
  MLPredictionService.java    (Model A: weighted composite + SimpleRegression)

com.hft.service.risk
  RiskManagementService.java

com.hft.service.signal
  RecommendationEngine.java   (orchestrates all services → TradeRecommendation)

com.hft.streams
  StreamSinkBridge.java       (Reactor Sinks.Many multicast, backtestFlux)
  KafkaStreamsTopology.java    (5 processors, Redis routing, ML rescore)
  RedisPubSubBridge.java      (@ConditionalOnProperty hft.redis-pubsub.enabled)

com.hft.util
  DateUtil.java, MarketHoursUtil.java, SentimentUtil.java, TechnicalIndicatorUtil.java
```

---

## 5. KAFKA TOPICS (all 5 stream outputs)

```
Input topics:
  market-data-raw    (64 partitions)  — live tick feed
  trading-signals    (16 partitions)  — raw signal events

Kafka Streams output topics:
  quotes-aggregated  (64 partitions)  ← Processor 1 (QuoteKTable reduce)
  candles-1m         (64 partitions)  ← Processor 2 (1-min OHLCV window), compacted
  signals-enriched   (16 partitions)  ← Processor 3 (signal + latest quote join)
  signals-ml-scored  (16 partitions)  ← Processor 4 (Stage 5, EnsembleModel rescore)
  backtest-results   (8  partitions)  ← Processor 5 (Stage 6, conditional capture)

Config flag:  hft.backtest.kafka-capture.enabled: false  (set true to activate P5)
```

---

## 6. GRAPHQL SCHEMA — FULL TYPE LIST

```
Scalars:    BigDecimal, DateTime, Date, Long
Enums:      Market, AssetType, SignalType, RiskLevel, TrendDirection, SentimentLabel, TimeHorizon
Types:      StockQuote, TechnicalIndicators, SentimentData, FundamentalData, MacroData,
            TradeRecommendation, StockDashboard,
            ModelPerformance (Stage 5),
            BacktestMetrics, BacktestTrade, BacktestRun, WalkForwardResult (Stage 6)
Inputs:     ScreenerInput, WatchlistInput,
            BacktestInput (Stage 6)
Query:      quote, quotes, technicalAnalysis, sentiment, fundamentals, macro,
            recommendation, screenStocks, activeRecommendations, stockDashboard,
            modelPerformance, modelAssignment (Stage 5),
            backtestRun, listBacktestRuns, walkForwardValidation (Stage 6)
Mutation:   recordSignalOutcome (Stage 5), runBacktest (Stage 6)
Subscription: liveQuote, liveSignals, watchlistSignals, backtestProgress (Stage 6)
```

---

## 7. CONFIGURATION MAP

```
src/main/resources/application.yml           — all defaults
src/main/resources/application-dev.yml       — H2 DB, kafka streams auto-startup=false
src/main/resources/application-prod.yml      — EOS, Redis SSL, Kafka SSL, all ${ENV_VAR}
src/main/resources/graphql/schema.graphqls   — SDL (all types through Stage 6)
src/main/proto/hft/
  common.proto, market_data.proto, analysis.proto, signal.proto

Key config values (application.yml):
  server.port: 8080
  hft.graphql.max-query-depth: 10
  hft.graphql.max-query-complexity: 200
  hft.redis-pubsub.enabled: false
  hft.ml.model-router.model-b-fraction: 0.10
  hft.backtest.interval-type: 1D
  hft.backtest.kafka-capture.enabled: false
  hft.recommendation.composite-weights:
    technical: 0.35, fundamental: 0.25, sentiment: 0.20, macro: 0.15, ml: 0.05
  hft.recommendation.min-confidence-for-buy: 65.0
  hft.recommendation.min-risk-reward-ratio: 2.0
  hft.recommendation.min-liquidity-avg-volume: 100000
```

---

## 8. KNOWN GOTCHAS & CRITICAL DECISIONS

### Import trap (Stage 4)
```java
// WRONG — causes NoSuchBeanDefinitionException at startup
import org.springframework.graphql.execution.GraphQlSourceBuilderCustomizer;

// CORRECT
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
```

### Optional dependency injection pattern (Stages 4, 5, 6)
```java
// @RequiredArgsConstructor only generates constructor args for final fields.
// For optional wiring, use non-final + @Autowired(required=false):
@Autowired(required = false)
private ModelABRouter mlRouter;          // null when Stage 5 not active

@Autowired(required = false)
private EnsembleModel ensembleModel;     // null when Stage 5 not active

@Autowired(required = false)
private RedisPubSubBridge redisBridge;   // null in single-node dev mode
```

### MacroData / SentimentData field names (discovered during Stage 5)
Java field names differ from what the GraphQL schema names suggest:
```
MacroData.interestRate       (schema says centralBankRate)
MacroData.gdpGrowthRateYoY  (schema says gdpGrowthRate)
MacroData.cpiInflationRate   (schema says inflationRate)
MacroData.fiiNetFlowCrores   (BigDecimal, not Float)
SentimentData has no fearGreedIndex or analystConsensus fields
FundamentalData has no freeCashFlowYield field
```

### Kafka Streams — no external calls in processor thread
The `mlRescore()` method in `KafkaStreamsTopology` builds a proxy `MLFeatureVector` from
scores already embedded in the `TradeRecommendation`. It never calls `MLFeatureExtractor`
or any service — Kafka Streams threads are not Spring-managed and cannot call service beans safely.

### BacktestRunner dual API
- `runAsync(BacktestConfig)` → `@Async("analysisExecutor")` — used by GraphQL mutation
- `runSync(BacktestConfig, runId)` → `@Transactional` — used by WalkForwardValidator
  These cannot be the same method because @Async returns CompletableFuture and WalkForward
  needs the result inline.

### OHLCVDataRepository was missing
`OHLCVData` existed as a `@Entity` from Stage 3 but had no JPA repository.
`OHLCVDataRepository` was created in Stage 6. Any code that needs historical bars
must use this repository; `MarketDataAggregatorService.getRecentHistory()` should
delegate to it.

### ModelPerformanceTracker — explicit constructor required
Cannot use `@RequiredArgsConstructor` because Micrometer Gauges must be registered
in the constructor body. Uses an explicit constructor instead.

---

## 9. BUILD COMMANDS

```bash
# Compile only
gradle compileJava

# Run all tests
gradle test

# Run application (dev, no Kafka)
gradle bootRun --args='--spring.profiles.active=dev'

# Run with Kafka Streams active
gradle bootRun --args='--spring.profiles.active=dev --spring.kafka.streams.auto-startup=true'

# Build JAR
gradle bootJar

# Check dependencies
gradle dependencies --configuration compileClasspath

# Clean build
gradle clean build
```

---

## 10. DOCUMENTATION MAP

```
docs/HFT_ARCHITECTURE.md          — full platform architecture (foundation)
docs/STAGE1_GRAPHQL.md            — GraphQL layer
docs/STAGE2_GRPC.md               — gRPC pipeline
docs/STAGE3_KAFKA_STREAMS.md      — Kafka Streams topology
docs/STAGE4_PRODUCTION_HARDENING.md — Redis fan-out, gRPC TLS/auth, GraphQL limits
docs/STAGE5_ML_PIPELINE.md        — EnsembleModel, A/B routing, Processor 4
docs/STAGE6_BACKTESTING.md        — BacktestRunner, StrategyMetrics, WalkForward
docs/STAGES_OVERVIEW.md           — cumulative evolution summary
docs/SESSION_CONTEXT.md           — THIS FILE (resume point for IDE restart)
```

---

## 11. ANALYSIS PIPELINE — HOW IT WORKS (summary)

Every symbol goes through 8 steps in `RecommendationEngine.generateRecommendation()`:

```
1. getQuote()         → StockQuote (price, volume, 52w high/low)
2. taService.analyze()→ TechnicalIndicators (RSI, MACD, SMA, OBV, patterns)
3. sentimentService() → SentimentData (news NLP, social mentions, bullish%)
4. fundamentalService → FundamentalData (P/E, ROE, debt/equity, EPS growth)
5. macroService()     → MacroData (VIX, FII flows, GDP, interest rate)
6. ML prediction      → compositeScore (Model A or B via A/B router)
7. Liquidity filter   → drop if avgVolume20Day < 100,000
8. R:R filter         → drop if riskRewardRatio < 2.0

Model A (90% of symbols): weighted composite + linear regression on 30 bars
  weights: TA(35%) + FA(25%) + Sentiment(20%) + Macro(15%) + Regression(5%)
  VIX>25 → macro weight +5pp, TA weight -5pp (normalised to sum 1.0)

Model B (10% of symbols, EnsembleModel):
  3 sub-models: Momentum, MeanReversion, Trend
  Blend weights driven by VIX:
    Bull (VIX<15):  Momentum 50%, Trend 35%, Reversion 15%
    Neutral:        33% / 33% / 34%
    Bear (VIX>25):  Reversion 40%, Trend 40%, Momentum 20%
  + confirmation bonus (max +8) + conflict penalty (max -12)

Price target: score-based return (18%/12%/6%/2%/-8%) averaged with regression projection
Stop loss:    entry - 2×ATR(14);  fallback -7%
Signal:       ≥80 STRONG_BUY, ≥65 BUY, ≥52 WATCH, ≥38 HOLD, ≥25 SELL, <25 STRONG_SELL
Confidence:   base=compositeScore + bonuses for aligned signals, penalty for contradictions
Ranking:      sort all bullish signals by compositeScore desc, limit to topN
```

Stage 6 backtest validates these signals on historical OHLCV data:
```
Entry: next bar open after BUY signal
Exit:  first of → price hits target (TARGET_HIT) | price hits stop (STOP_HIT) | time expires (TIME_EXPIRY)
Inline TA: RSI-14, SMA20/50/200, EMA9, ATR14, BB — computed from raw bars, no service calls
Metrics:   Sharpe(×√252), CAGR, max drawdown, win rate, profit factor, expectancy
Walk-forward: N windows, each 80% warmup / 20% test — proves strategy isn't curve-fitted
```

---

## 12. NEXT STAGE — STAGE 7 (planned)

**Stage 7: Deep Learning & ONNX Model Serving**

Planned additions:
```
- DJL (Deep Java Library) — ONNX Runtime backend
  dependency: ai.djl:api + ai.djl.pytorch:pytorch-engine
- Train external LSTM/Transformer on historical OHLCV + feature vectors (Python)
- Export trained model to ONNX format
- Load ONNX model at Spring Boot startup via DJL ModelZoo
- Replace MLPredictionService.computeRegressionScore() with ONNX inference
- 45-feature input tensor (41 from MLFeatureVector + 4 OHLCV-derived momentum features)
- Kafka Streams: feed signal features into ONNX model inline (replace proxy FV approach)
- Outcome reconciliation: consume backtest-results topic (Stage 6) to score live predictions
- New Prometheus metrics: hft_onnx_inference_ms, hft_onnx_prediction_accuracy
- New GraphQL fields: modelType, onnxModelVersion, inferenceMs on TradeRecommendation
- New topic: onnx-inference-results (16 partitions)
```

Starting point for Stage 7:
```
1. Add DJL dependencies to build.gradle.kts
2. Create com/hft/ml/OnnxModelService.java — loads model, runs inference
3. Create com/hft/ml/OnnxFeatureBuilder.java — constructs 45-feature input tensor
4. Modify ModelABRouter to add a third path: Model C (ONNX)
5. Add Kafka Processor 6: signals-ml-scored → onnx-inference-results
6. Extend schema.graphqls with ONNX-specific fields
7. Extend BacktestRunner to support model="C"
```

---

## 13. SECURITY REMINDERS

```
NEVER commit:  .env, secrets.yml, application-local.yml, application-secrets.yml
JWT secret:    MUST be overridden via ${JWT_SECRET} env var in production
API keys:      ALPHA_VANTAGE_API_KEY, NEWSAPI_API_KEY, TWITTER_BEARER_TOKEN,
               REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET, FRED_API_KEY
application-prod.yml: ALL values via ${ENV_VAR} — zero hardcoded secrets
gRPC TLS:      cert and key via env vars grpc.server.tls.cert-file / key-file
```

---

*Created: 2026-08-16 | Branch: DS_coding | Last stages implemented: 1–6 | Next: Stage 7*
*All trading signals are for informational/educational purposes only — not investment advice.*
