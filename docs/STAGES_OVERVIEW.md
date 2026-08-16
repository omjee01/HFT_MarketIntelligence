# STAGES OVERVIEW — Evolution of HFT Market Intelligence Platform

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

---

## 1. THE BUILD JOURNEY

This document maps the evolution of the HFT Market Intelligence Platform from its REST API
foundation through three successive stages, each adding a new communication layer and
data pipeline capability.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    PLATFORM EVOLUTION — 6 STAGES                                 │
│                                                                                  │
│  FOUNDATION                                                                      │
│  (Phase 1-4, REST)                                                               │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  Spring Boot + JPA + Kafka + Redis + JWT + Swagger                        │   │
│  │  Domain model, service layer, REST controllers, repositories              │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 1: GRAPHQL API LAYER                                                       │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + Spring GraphQL (spring-boot-starter-graphql)                           │   │
│  │  + graphql-java-extended-scalars (DateTime, BigDecimal, Long, Date)       │   │
│  │  + GraphiQL browser IDE at /graphiql                                      │   │
│  │  + WebSocket subscriptions at /graphql-ws                                 │   │
│  │  + 5 resolver classes (Dashboard, MarketData, Analysis, Rec, Subscription)│   │
│  │  + schema.graphqls SDL with all types, queries, mutations, subscriptions  │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 2: gRPC INTERNAL PIPELINE                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + io.grpc:grpc-netty-shaded / grpc-protobuf / grpc-stub 1.65.0          │   │
│  │  + com.google.protobuf:protoc 3.25.5 + protoc-gen-grpc-java              │   │
│  │  + 3 proto files: common.proto, market_data.proto, analysis.proto,        │   │
│  │    signal.proto                                                           │   │
│  │  + ProtoMapper (null-safe domain ↔ proto conversion)                     │   │
│  │  + 3 gRPC service impls (MarketData, Analysis, Signal)                   │   │
│  │  + GrpcServerConfig (SmartLifecycle, port 9090)                          │   │
│  │  + CVE-2024-7254 patch (forced protobuf-java:3.25.5)                     │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 3: KAFKA STREAMS REAL-TIME PIPELINE                                        │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + @EnableKafkaStreams, kafka-streams dependency                          │   │
│  │  + 3 output topics: quotes-aggregated, candles-1m, signals-enriched      │   │
│  │  + KafkaStreamsTopology (@PostConstruct, StreamsBuilder injected)         │   │
│  │    Processor 1: QuoteKTable (latest quote per symbol via reduce)          │   │
│  │    Processor 2: CandleBuilder (1-min tumbling window OHLCV)               │   │
│  │    Processor 3: SignalEnricher (leftJoin signal ⊕ quote KTable)           │   │
│  │  + StreamSinkBridge (Reactor Sinks multicast → GraphQL + gRPC)           │   │
│  │  + SignalSubscriptionResolver rewritten (no @KafkaListener)              │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 4: MULTI-NODE PRODUCTION HARDENING                                         │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + RedisPubSubBridge: Redis Pub/Sub fan-out (hft:quotes/signals/candles)  │   │
│  │    KafkaStreamsTopology routes emitXxx via Redis when bridge is active    │   │
│  │    @ConditionalOnProperty(hft.redis-pubsub.enabled=true)                  │   │
│  │  + GrpcAuthInterceptor: JWT ServerInterceptor on all gRPC services        │   │
│  │    (same JWT secret as HTTP; toggle grpc.server.auth.enabled)             │   │
│  │  + GrpcServerConfig: TLS via NettyServerBuilder.useTransportSecurity()   │   │
│  │    (grpc.server.tls.enabled + cert-file + key-file)                       │   │
│  │  + GraphQLInstrumentationConfig: MaxQueryDepth(10)+MaxQueryComplexity(200)│   │
│  │    via GraphQlSourceBuilderCustomizer (Spring Boot autoconfigure)         │   │
│  │  + KafkaStreamsMetricsRegistrar: binds KafkaStreams → Micrometer on       │   │
│  │    ApplicationReadyEvent; gracefully skips when auto-startup=false        │   │
│  │  + application-prod.yml: EOS (exactly_once_v2), Redis SSL, Kafka SSL,    │   │
│  │    all secrets via env vars, Prometheus scrape enabled                    │   │
│  │  + micrometer-registry-prometheus dependency added                        │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 5: ML MODEL INTEGRATION & A/B TESTING                                      │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + MLFeatureVector: 41-feature record (TA/FA/sentiment/macro/price)      │   │
│  │  + MLFeatureExtractor: domain objects → MLFeatureVector (null-safe)      │   │
│  │  + EnsembleModel (Model B): Momentum + MeanReversion + Trend             │   │
│  │    regime-aware blending: bull→[0.50,0.15,0.35], bear→[0.20,0.40,0.40]  │   │
│  │    confirmation bonus (+8 max) + conflict penalty (–12 max)              │   │
│  │  + ModelABRouter: consistent-hash routing (symbol-stable, fraction-based)│   │
│  │    hft.ml.model-router.model-b-fraction=0.10 (10% → Model B)            │   │
│  │  + ModelPerformanceTracker: hit-rate + avg-return gauges, Redis TTL-90d  │   │
│  │    Prometheus: hft_ml_hit_rate{model}, hft_ml_avg_return_pct{model}      │   │
│  │  + KafkaStreams Processor 4: signals-enriched → mlRescore() →            │   │
│  │    signals-ml-scored  (60% original + 40% ensemble blend)                │   │
│  │  + GraphQL Mutation type: recordSignalOutcome() → feedback loop          │   │
│  │  + GraphQL Queries: modelPerformance(), modelAssignment()                │   │
│  │  + MLResolver: @QueryMapping + @MutationMapping                          │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                │                                                                  │
│                ▼                                                                  │
│  STAGE 6: BACKTESTING & STRATEGY VALIDATION ENGINE                                │
│  ┌───────────────────────────────────────────────────────────────────────────┐   │
│  │  + BacktestRunner (@Async): fetch OHLCV → inline TA → signal → trade sim  │   │
│  │    inline TA: RSI-14, SMA20/50/200, EMA9, ATR-14, Bollinger Bands         │   │
│  │    exitReason: TARGET_HIT | STOP_HIT | TIME_EXPIRY                        │   │
│  │    20-bar warmup; one trade at a time per symbol; no external API calls   │   │
│  │  + StrategyMetricsEngine: 12 metrics — Sharpe (annualised √252), CAGR,    │   │
│  │    max drawdown, win rate, profit factor, expectancy, avgHoldingDays       │   │
│  │  + WalkForwardValidator: N rolling windows, 80/20 warmup/test split        │   │
│  │    prevents look-ahead bias; each window runs BacktestRunner.runSync()     │   │
│  │  + BacktestRun (@Entity): PENDING→RUNNING→COMPLETE→FAILED lifecycle        │   │
│  │  + BacktestTrade (@Entity): per-trade audit record with full OHLCV refs    │   │
│  │  + OHLCVDataRepository: was missing; added for historical bar queries      │   │
│  │  + GraphQL: runBacktest mutation (async, returns PENDING immediately)       │   │
│  │    backtestProgress subscription (auto-completes on COMPLETE|FAILED)       │   │
│  │    backtestRun, listBacktestRuns, walkForwardValidation queries            │   │
│  │  + Kafka Processor 5: signals-ml-scored → backtest-results (conditional)  │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. PORT AND PROTOCOL MAP (All Stages Combined)

```
┌──────────────────────────────────────────────────────────────────┐
│                    RUNTIME PORT LAYOUT                           │
│                                                                  │
│  :8080  — Spring Boot (HTTP/1.1 + HTTP/2)                       │
│  ├── REST API (from Foundation)                                  │
│  │   ├── GET  /market/quote/{symbol}                             │
│  │   ├── GET  /analysis/technical/{symbol}                       │
│  │   ├── GET  /recommendations/daily                             │
│  │   └── GET  /swagger-ui.html                                   │
│  │                                                               │
│  ├── GraphQL (from Stage 1)                                      │
│  │   ├── POST /graphql         Queries + Mutations               │
│  │   ├── WS   /graphql-ws      Subscriptions                     │
│  │   └── GET  /graphiql        Browser IDE                       │
│  │                                                               │
│  └── Spring Actuator                                             │
│      └── GET  /actuator/health                                   │
│                                                                  │
│  :9090  — gRPC Netty (from Stage 2)                             │
│  ├── hft.MarketDataService (GetQuote, BatchQuotes, StreamQuotes) │
│  ├── hft.AnalysisService   (Technical, Sentiment, Fund., Macro) │
│  └── hft.SignalService     (Recommendation, Screener, Stream)   │
│                                                                  │
│  Kafka (broker: localhost:9092) — from Stage 3                  │
│  ├── Input:  market-data-raw  (64 partitions)                   │
│  ├── Input:  trading-signals  (16 partitions)                   │
│  ├── Output: quotes-aggregated  (64 partitions)                 │
│  ├── Output: candles-1m         (64 partitions, compact)        │
│  ├── Output: signals-enriched   (16 partitions)                 │
│  ├── Output: signals-ml-scored  (16 partitions) ← Stage 5      │
│  └── Output: backtest-results   (8 partitions)  ← Stage 6      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. WHAT EACH STAGE ADDS

### Stage 1 — GraphQL API Layer

| Before | After |
|---|---|
| 4 REST calls for stock dashboard | 1 GraphQL query |
| Fixed response shape (over-fetching) | Client selects exact fields |
| No real-time push (except custom WebSocket) | Native GraphQL Subscriptions |
| Schema via Swagger YAML | SDL introspection + GraphiQL |
| N REST endpoints to document | Single /graphql entry point |

**New files added:**
```
src/main/resources/graphql/schema.graphqls
src/main/java/com/hft/graphql/
  StockDashboardResolver.java
  MarketDataResolver.java
  AnalysisResolver.java
  RecommendationResolver.java
  SignalSubscriptionResolver.java
  GraphQLConfig.java
```

**Bug fixes applied:**
- `StockDashboardResolver` line 38: `sentimentService.analyze()` → `sentimentService.analyzeSentiment()`
- `application.yml`: merged duplicate `spring:` root key (DuplicateKeyException fix)

---

### Stage 2 — gRPC Internal Pipeline

| Before | After |
|---|---|
| REST/JSON only (text, HTTP/1.1) | Binary Protobuf over HTTP/2 |
| Schema documented in Swagger | .proto files (compile-time contract) |
| No machine-to-machine streaming | gRPC server-streaming |
| API shape changed at runtime | Breaking changes caught at compile time |
| No type-safe cross-language clients | Auto-generated stubs for any language |

**New files added:**
```
src/main/proto/hft/
  common.proto
  market_data.proto
  analysis.proto
  signal.proto
src/main/java/com/hft/grpc/
  ProtoMapper.java
  MarketDataGrpcService.java
  AnalysisGrpcService.java
  SignalGrpcService.java
  GrpcServerConfig.java
```

**Bug fix applied:**
- `AssetType` enum mismatch: removed `EQUITY`, `INDEX`, `FUTURES`, `OPTIONS` (don't exist);
  added correct values `STOCK`, `OPTION`, `FUTURE`, `COMMODITY`, `IPO`, `ETF`,
  `MUTUAL_FUND`, `BOND`, `CURRENCY`, `CRYPTO`
- CVE-2024-7254: forced `protobuf-java:3.25.5` via `resolutionStrategy`

---

### Stage 3 — Kafka Streams Real-Time Pipeline (also modified in Stage 4)

| Before | After |
|---|---|
| @KafkaListener (stateless) | Kafka Streams topology (stateful) |
| No OHLCV candle data | 1-min tumbling window CandleBuilder |
| Signals with stale/null currentPrice | SignalEnricher joins with latest quote |
| Sinks.Many defined in resolver class | Centralized StreamSinkBridge |
| GraphQL subscriptions not Kafka-backed | Full pipeline: Kafka → Bridge → Reactor |
| gRPC streaming was snapshot-only | gRPC + Kafka Streams live feeds wired |

**New files added:**
```
src/main/java/com/hft/streams/
  StreamSinkBridge.java
  KafkaStreamsTopology.java
```

**Modified files:**
```
src/main/java/com/hft/config/KafkaConfig.java   (@EnableKafkaStreams + 3 new topics)
src/main/java/com/hft/graphql/SignalSubscriptionResolver.java  (delegates to bridge)
src/main/resources/application.yml               (kafka.streams config block)
src/main/resources/application-dev.yml           (streams.auto-startup=false)
```

---

### Stage 4 — Multi-Node Production Hardening

| Before | After |
|---|---|
| Single-node live subscriptions | Redis Pub/Sub fan-out across all nodes |
| No gRPC authentication | JWT ServerInterceptor (same token as HTTP) |
| gRPC plaintext only | Optional TLS (cert/key via env vars) |
| No GraphQL query limits | Depth(10) + Complexity(200) enforced |
| No Kafka Streams metrics | Full Kafka Streams → Micrometer → Prometheus |
| No production config | application-prod.yml with EOS, SSL, all env vars |

**New files added:**
```
src/main/java/com/hft/streams/RedisPubSubBridge.java
src/main/java/com/hft/grpc/GrpcAuthInterceptor.java
src/main/java/com/hft/graphql/GraphQLInstrumentationConfig.java
src/main/java/com/hft/metrics/KafkaStreamsMetricsRegistrar.java
src/main/resources/application-prod.yml
```

**Modified files:**
```
src/main/java/com/hft/grpc/GrpcServerConfig.java         (TLS + auth interceptor wiring)
src/main/java/com/hft/streams/KafkaStreamsTopology.java   (optional Redis routing)
src/main/resources/application.yml                        (Stage 4 config defaults)
src/main/resources/application-dev.yml                    (Stage 4 dev overrides)
build.gradle.kts                                          (micrometer-registry-prometheus)
```

---

### Stage 5 — ML Model Integration & A/B Testing

| Before | After |
|---|---|
| Single ML model (weighted composite) | A/B tested: Model A vs Model B |
| Static VIX weight adjustment | Dynamic 3-regime blend (bull/neutral/bear) |
| 5 score inputs to ML | 41-feature MLFeatureVector |
| 3 Kafka Streams processors | 4 + ML re-scoring pipeline |
| 3 output topics | 4 incl. `signals-ml-scored` |
| No model accuracy tracking | Hit rate + avg return via Micrometer |
| Query + Subscription only | + GraphQL Mutation type added |
| No routing observability | `modelAssignment(symbol)` query per symbol |
| No feedback loop | `recordSignalOutcome` mutation |

**New files added:**
```
src/main/java/com/hft/ml/
  MLFeatureVector.java          (41-feature @Data @Builder record)
  MLFeatureExtractor.java       (domain objects → feature vector, null-safe)
  EnsembleModel.java            (Model B: Momentum + MeanReversion + Trend)
  ModelABRouter.java            (consistent-hash A/B routing)
  ModelPerformanceTracker.java  (Micrometer gauges + Redis TTL-90d)
src/main/java/com/hft/graphql/
  MLResolver.java               (@QueryMapping + @MutationMapping)
docs/STAGE5_ML_PIPELINE.md
```

**Modified files:**
```
src/main/java/com/hft/streams/KafkaStreamsTopology.java   (Processor 4 + mlRescore)
src/main/java/com/hft/service/signal/RecommendationEngine.java  (ModelABRouter injection)
src/main/java/com/hft/config/KafkaConfig.java             (TOPIC_SIGNALS_ML_SCORED + @Bean)
src/main/resources/graphql/schema.graphqls                (ModelPerformance + Mutation type)
src/main/resources/application.yml                        (hft.ml config defaults)
docs/STAGES_OVERVIEW.md                                   (this file)
```

---

### Stage 6 — Backtesting & Strategy Validation Engine

| Before | After |
|---|---|
| Signals validated only on live data | Historical simulation on OHLCV bars |
| No trade-level outcome records | @Entity BacktestTrade with full audit trail |
| Model A vs B compared live only | A/B on identical historical data |
| Overfitting risk unmeasured | Walk-forward validation (N windows, 80/20 split) |
| 5 performance metrics (hit rate, avg return) | +12 metrics incl. Sharpe, CAGR, drawdown |
| GraphQL Subscription (3 types) | +backtestProgress (auto-completes on done) |
| 4 Kafka Streams processors | 5 (backtest-capture, conditional) |
| 12 JPA entities | 14 (+backtest_runs, +backtest_trades) |
| OHLCVData entity, no repository | OHLCVDataRepository added |
| Inline TA only in live pipeline | Inline TA engine in BacktestRunner |

**New files added:**
```
src/main/java/com/hft/backtest/
  BacktestConfig.java              (record — symbols, dates, model, thresholds)
  BacktestRun.java                 (@Entity — lifecycle PENDING→RUNNING→COMPLETE→FAILED)
  BacktestTrade.java               (@Entity — per-trade outcome with exitReason)
  BacktestMetrics.java             (@Embeddable — 12 performance metrics)
  BacktestRunRepository.java       (findByMarket, findTop20ByOrderByStartedAtDesc)
  BacktestTradeRepository.java     (findByRunId, findByRunIdAndProfitable)
  StrategyMetricsEngine.java       (Sharpe×√252, CAGR, max drawdown, profit factor)
  WalkForwardValidator.java        (N windows, 80/20 split, runSync per window)
  BacktestRunner.java              (@Async orchestrator, inline TA, Model A/B dispatch)
src/main/java/com/hft/graphql/
  BacktestResolver.java            (@MutationMapping runBacktest, @SubscriptionMapping backtestProgress)
src/main/java/com/hft/repository/
  OHLCVDataRepository.java         (was missing — findBySymbolAndMarket…BarDateBetween)
docs/STAGE6_BACKTESTING.md
```

**Modified files:**
```
src/main/java/com/hft/streams/StreamSinkBridge.java        (emitBacktestProgress + backtestFlux)
src/main/java/com/hft/streams/KafkaStreamsTopology.java    (Processor 5, conditional)
src/main/java/com/hft/config/KafkaConfig.java              (TOPIC_BACKTEST_RESULTS + @Bean)
src/main/resources/graphql/schema.graphqls                 (BacktestInput/Run/Trade/Metrics types)
src/main/resources/application.yml                         (hft.backtest config block)
docs/STAGES_OVERVIEW.md                                    (this file)
```

---

### Stage 7 — Real Intelligence Data Sourcing

A code audit (2026-08-16) found the platform's news/social/macro pipeline was partly
synthetic — see `docs/STAGE7_DATA_SOURCING.md` for the full source-by-source verdict. This
stage replaces every fake/hardcoded input that could be replaced with a real, no-cost,
ToS-compliant source, and reserves the Stage 7 slot originally planned for ONNX (which
becomes Stage 8 — see §11).

| Before | After |
|---|---|
| Social sentiment = `Math.random()` noise | Real Reddit (OAuth2) + StockTwits scoring |
| NewsAPI, FRED real but disabled by default | Both on by default, safe no-op without a key |
| No company-filing signal | SEC EDGAR full-text search (8-K/10-K/10-Q), US, verified live |
| Geopolitical risk = hardcoded 5.0 (US) / 4.0 (India) | GDELT tone-derived score (implemented, reachability unverified — see doc) |
| India FII/DII flow = hardcoded "BUYING", 5 days | Real NSE `fiidiiTradeReact` endpoint, verified live |
| India repo rate/CPI/GDP | Unchanged — no free RBI API found this pass |
| India fundamentals | Unchanged — Screener.in's only JSON endpoint is robots.txt-disallowed |

No new files, no new entities, no new GraphQL fields, no new Kafka topics/ports — this stage
only modified three existing services plus config. Full detail, including exactly which
sources are genuinely live versus honestly blocked and why: `docs/STAGE7_DATA_SOURCING.md`.

**Modified files:**
```
src/main/java/com/hft/service/analysis/SentimentAnalysisService.java   (+SEC EDGAR, Reddit, StockTwits)
src/main/java/com/hft/service/analysis/MacroGeopoliticalService.java   (+GDELT, +NSE FII/DII)
src/main/java/com/hft/service/analysis/FundamentalAnalysisService.java (comment only — Screener.in blocked)
src/main/resources/application.yml, application-dev.yml                (new source config, flags flipped on)
docs/HFT_ARCHITECTURE.md                                                (+§22 IPO, §23 UI, §24 ASRB, §25 Identity)
docs/ASRB_TECHNICAL_DISCLOSURE.md                                       (new — algorithm spec + novelty draft)
docs/STAGE7_DATA_SOURCING.md                                            (new — this stage's detail doc)
docs/STAGES_OVERVIEW.md                                                 (this file)
```

---

### Stage 8 — Identity, Admin Platform & Web UI

The platform had no login and no UI before this stage — every prior stage was verified via
curl/grpcurl only. This adds real JWT auth (access + refresh tokens, `com.auth0:java-jwt`),
an admin-managed platform-credential store (AES-256-GCM at rest), and a static, no-build-step
web UI with a 3-state Light/Dark/Auto theme.

| Before | After |
|---|---|
| No login, no user concept | `com.hft.identity` — User/Role, JwtService, JwtAuthFilter, `/api/v1/auth/register\|login\|refresh\|me` |
| `SecurityConfig`'s JWT filter was scaffolding — no filter class existed | Real `JwtAuthFilter` (`OncePerRequestFilter`), wired via `addFilterBefore` |
| API keys only settable via env vars | `com.hft.admin` — `AdminSettingsController`, write-only, encrypted at rest, never echoes raw values |
| No UI at all | `src/main/resources/static/` — vanilla HTML/CSS/JS, mobile/tablet/desktop responsive |

**Modified/new files:**
```
src/main/java/com/hft/identity/           (new — User, Role, JwtService, JwtAuthFilter, AuthService,
                                            AuthController, TestUserSeeder, exceptions)
src/main/java/com/hft/admin/              (new — PlatformApiCredential, CredentialCipher,
                                            PlatformSettingsService, AdminSettingsController)
src/main/java/com/hft/config/SecurityConfig.java   (JwtAuthFilter wired in; auth endpoints made public)
src/main/resources/static/                (new — index.html, css/, js/)
```

Verified end-to-end in a real Chrome browser, not just unit/compile-verified: login, a live
MSFT recommendation rendered from genuinely live Alpha Vantage/NewsAPI/SEC EDGAR data, the
admin credential save/clear round-trip, and theme cycling.

---

### Stage 9 — IPO Engine, ASRB & Real Infrastructure

Three sub-stages, one commit each (9a/9b/9c) — grouped under one stage number because they
landed in the same session and share a common thread: everything the platform does for
already-listed stocks now also exists for IPOs, a genuinely novel fusion algorithm exists
(if not yet wired live), and the infrastructure config files stop describing services that
don't actually run anywhere.

**9a — IPO Buy/Sell Decision Engine** (`docs/HFT_ARCHITECTURE.md` §22): Phase 1 pre-listing
composite scoring (Valuation/Demand/Quality/Sentiment → APPLY_STRONG/APPLY/RISKY/AVOID) and
Phase 2 post-listing lifecycle (Day 0 flip/hold/sell, Days 1–19 reduced scoring, Day 20+
graduates to the main `RecommendationEngine`). `GET /api/v1/ipo/recommendations[/{symbol}]`
plus GraphQL equivalents. Verified live against 3 seeded sample IPOs, each scoring correctly
and distinctly (APPLY / AVOID / RISKY). Found and fixed a real bug in the original §22.2 spec
during verification — the RISKY override's `valuationScore < 30` threshold was mathematically
unreachable (the formula's own floor is exactly 30); corrected to `RICH_VALUATION_THRESHOLD = 40`.

**9b — Adaptive Source Reliability Bandit** (`docs/ASRB_TECHNICAL_DISCLOSURE.md`): the
correlation-/misinformation-aware source-fusion algorithm, built as a standalone module
(`com.hft.intelligence`) with 13 passing tests. Deliberately **not wired into the live
pipeline yet** — that's a separate decision (calibration data needed first).

**9c — Real Infrastructure** (`docs/HFT_ARCHITECTURE.md` §26, `docs/STAGE9_INFRASTRUCTURE.md`):
MySQL, ClickHouse, Redis, and Kafka actually running via Docker Compose, replacing what had
been H2-in-memory + no-broker + simple-cache everywhere. New `docker` Spring profile layers
this on top of `dev` rather than replacing it — `gradle test` and plain `dev` still run on H2
with zero external dependencies. Surfaced and fixed a real bug along the way: `signal`/`rank`
columns that were silently fine on H2 turned out to be MySQL 8 reserved words.

| Before | After |
|---|---|
| No IPO support at all | Full pre-/post-listing engine, live-verified |
| ASRB existed only as a design doc | Built, tested, standalone (not yet wired live) |
| H2 in-memory, no broker, `cache.type: simple` | Real MySQL/ClickHouse/Redis/Kafka via `docker` profile |
| Alpha Vantage `demo` key | Real key — confirmed valid, free tier (25/day) far stricter than the app's polling assumed |

**Modified/new files:**
```
src/main/java/com/hft/ipo/                          (new — IPOAnalysisService, IPOLifecycleScorer, SampleIpoSeeder)
src/main/java/com/hft/controller/IPOController.java (new)
src/main/java/com/hft/graphql/IPOResolver.java      (new)
src/main/java/com/hft/intelligence/                 (new — ASRB, 7 classes + 13 tests, standalone)
src/main/java/com/hft/analytics/                    (new — ClickHouseSchemaInitializer, ClickHouseSignalSink)
src/main/java/com/hft/config/DatabaseConfig.java    (new — explicit @Primary + secondary ClickHouse datasource)
docker-compose.yml, .env.example                    (new)
src/main/resources/application-docker.yml           (new)
docs/STAGE9_INFRASTRUCTURE.md                        (new — this stage's detail doc)
docs/HFT_ARCHITECTURE.md                             (§22.2 threshold fix, §26 new)
```

---

## 4. DEPENDENCY EVOLUTION (build.gradle.kts)

```
Foundation:
  spring-boot-starter-web
  spring-boot-starter-data-jpa
  spring-boot-starter-security
  spring-boot-starter-cache
  spring-kafka
  h2, postgresql
  lombok, jackson

Stage 1 adds:
  spring-boot-starter-graphql
  graphql-java-extended-scalars:22.0

Stage 2 adds:
  io.grpc:grpc-netty-shaded:1.65.0
  io.grpc:grpc-protobuf:1.65.0
  io.grpc:grpc-stub:1.65.0
  org.apache.tomcat:annotations-api:6.0.53  (javax.annotation for generated stubs)
  Plugin: com.google.protobuf:0.9.4
  protoc artifact: com.google.protobuf:protoc:3.25.5
  protoc plugin: io.grpc:protoc-gen-grpc-java:1.65.0

Stage 3 adds:
  org.apache.kafka:kafka-streams
  (spring-kafka already present; @EnableKafkaStreams activates auto-config)

Stage 4 adds:
  io.micrometer:micrometer-registry-prometheus  (runtimeOnly — Prometheus scrape endpoint)
  (all other Stage 4 features use existing dependencies: redis, grpc, graphql-java)

Stage 5 adds:
  (no new dependencies — uses commons-math3, micrometer-core, and spring-data-redis
   already present from earlier stages)

Stage 6 adds:
  (no new dependencies — BacktestRunner computes TA inline from raw OHLCV using
   pure Java math; no DJL, ND4J, or external ML inference library required)

Stage 7 adds:
  (no new dependencies — new fetch methods reuse the existing OkHttp3/Jackson stack)

Stage 8 adds:
  com.auth0:java-jwt:4.4.0   (JWT issuance/verification — NOT jjwt, despite some older
                               internal notes; this is the dependency actually in build.gradle.kts)

Stage 9 adds:
  com.mysql:mysql-connector-j        (runtimeOnly — MySQL, "docker"/prod profiles)
  com.clickhouse:clickhouse-jdbc:0.6.3:all   (analytics datasource, plain JDBC)
```

---

## 5. PERFORMANCE COMPARISON

### 5.1 Latency by Protocol

```
GET /recommendations/stock/AAPL   (REST/JSON)
  ─── Round trip: ~15-25ms   (HTTP/1.1 + JSON parse)

POST /graphql { recommendation(symbol:"AAPL") { ... } }  (GraphQL)
  ─── Round trip: ~8-15ms   (smaller payload, field selection)
  ─── Stock dashboard (4 data types): ~15ms  (vs 60-100ms for 4 REST calls)

grpcurl GetRecommendation AAPL  (gRPC/Protobuf)
  ─── Round trip: ~2-5ms    (binary encode, HTTP/2 multiplexing)

Kafka Streams → StreamSinkBridge → GraphQL subscription
  ─── End-to-end tick latency: ~50-200ms  (Kafka poll 100ms + stream processing)

Kafka Streams → StreamSinkBridge → gRPC streaming
  ─── End-to-end tick latency: ~30-150ms  (similar path)
```

### 5.2 Throughput by Stage

| Stage | Protocol | Throughput | Best For |
|---|---|---|---|
| Foundation | REST/JSON | ~500 req/s (single node) | Public API, CRUD |
| Stage 1 | GraphQL | ~800 req/s (query batching) | UI/BFF, dashboards |
| Stage 2 | gRPC | ~5,000 req/s (binary, HTTP/2) | Internal services, algo clients |
| Stage 3 | Kafka Streams | 64 partitions × broker throughput | Real-time enrichment pipeline |
| Stage 5 | ML Pipeline  | +<1ms overhead per signal          | A/B model accuracy testing    |
| Stage 6 | Backtesting  | ~1-30s per symbol (OHLCV depth)    | Strategy validation, walk-forward |

### 5.3 Developer Experience by Stage

| Dimension | REST | GraphQL | gRPC |
|---|---|---|---|
| Schema contract | Swagger (optional) | SDL (enforced) | .proto (compile-time) |
| Client code gen | Optional | Optional | Required |
| Browser testable | Yes (curl/Postman) | Yes (GraphiQL) | Needs grpcurl/BloomRPC |
| Real-time | Custom WebSocket | Native Subscriptions | Native streaming |
| Documentation | Swagger UI | GraphiQL Docs panel | Proto files |

---

## 6. TEST COVERAGE

All 37 unit tests pass across all stages (24 baseline + 13 from Stage 9b's ASRB module):

```
gradle test

┌─────────────────────────────────────────────────────────┐
│  Test Results                                           │
│  Tests run: 37                                          │
│  Failures: 0                                            │
│  Errors: 0                                              │
│  Skipped: 0                                             │
│                                                         │
│  Coverage areas:                                        │
│  ├── Service layer (market data, analysis, signals)     │
│  ├── RecommendationEngine (composite scoring)           │
│  ├── JWT authentication filter                          │
│  ├── REST controllers                                   │
│  ├── ASRB (correlation, posterior, stability, policy —  │
│  │     7 classes, Stage 9b)                              │
│  └── Domain model constructors and enums               │
└─────────────────────────────────────────────────────────┘
```

Note: Kafka Streams topology, gRPC services, and GraphQL resolvers are excluded from unit
tests (they require live infrastructure) — Stage 9c's docker-compose stack now makes that
infrastructure available locally, but no integration-test suite runs against it yet. Still
planned, not built.

---

## 7. GIT COMMIT HISTORY

| Stage | Commit | Description |
|---|---|---|
| Stage 1 | `cbdf4d2` | GraphQL layer + 2 bug fixes — all 24 tests pass |
| Stage 2 | `294b8b4` | gRPC pipeline + proto schemas + ProtoMapper |
| Stage 3 | `dfdff9a` | Kafka Streams topology + StreamSinkBridge |
| Stage 4 | `adc400d` | Production hardening — Redis fan-out, gRPC TLS+auth, GraphQL limits, metrics |
| Stage 5 | `a40fc96` | ML A/B routing — EnsembleModel, 41-feature vector, Processor 4, Mutation type |
| Stage 6 | `dee1c5d` | Backtesting engine — BacktestRunner, StrategyMetrics, WalkForward, Processor 5 |
| Stage 7 | `35a6845` | Real intelligence data sourcing + admin-managed credential overrides |
| Stage 8 | `a1dc644` | Identity & Auth platform + lightweight Web UI |
| Stage 9a | `492ebd6` | IPO Buy/Sell Decision Engine — Phase 1 pre-listing scoring |
| Stage 9b | `44a6ba6` | Adaptive Source Reliability Bandit (ASRB) — standalone module |
| Stage 9c | `c8b435f` | Real infrastructure — MySQL, ClickHouse, Redis, Kafka |

Short hashes only — commit trailers no longer include AI-attribution as of Stage 9c.

---

## 8. HOW TO RUN — ALL STAGES TOGETHER

### Full Stack (real MySQL/ClickHouse/Redis/Kafka — Stage 9c)

```bash
docker compose up -d --wait                              # mysql, clickhouse, redis, kafka

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun  # "secrets" only if
                                                           # application-secrets.yml exists locally

open http://localhost:8080/graphiql
grpcurl -plaintext localhost:9090 list
```

See `docs/STAGE9_INFRASTRUCTURE.md` for the full setup, port map (note: MySQL 3307 and
Redis 6380, not the defaults — see that doc for why), and verification steps.

### Dev Mode (H2, no external services — default, what `gradle test` runs under)

```bash
gradle bootRun --args='--spring.profiles.active=dev'
# REST + GraphQL fully functional
# gRPC starts on :9090
# Kafka Streams NOT started (no broker needed) — cache.type: simple, no Redis
```

---

## 9. SECURITY NOTES (ALL STAGES)

```
NEVER committed to git:
  .env
  secrets.yml
  application-local.yml
  application-secrets.yml
  (all in .gitignore)

JWT secret:
  application.yml: hft.jwt.secret: ${JWT_SECRET:change-in-production}
  → MUST be overridden via env var in production

API Keys (Alpha Vantage, NewsAPI, Twitter, Reddit, FRED):
  → Set as environment variables: ALPHA_VANTAGE_API_KEY=...
  → Never hardcoded in yml files
  → Real Alpha Vantage key (Stage 9c) lives in application-secrets.yml, git-ignored

docker-compose.yml (Stage 9c) credentials:
  → Local-dev-only defaults, bound to localhost, not exposed beyond the machine
  → Override via a git-ignored .env — see .env.example for the variable names
  → Never reused for prod — application-prod.yml requires its own env vars with no
    defaults (DATABASE_URL/USERNAME/PASSWORD, CLICKHOUSE_*, REDIS_PASSWORD, etc.)

gRPC (Stage 2):
  → Plaintext in dev (port 9090, no TLS)
  → Production: add TLS cert + JWT ServerInterceptor
```

---

## 10. DOCUMENTATION MAP

| Document | Contents |
|---|---|
| `docs/STAGE1_GRAPHQL.md` | GraphQL layer — how to run, queries, subscriptions, enhancements |
| `docs/STAGE2_GRPC.md` | gRPC pipeline — proto contracts, grpcurl commands, port layout |
| `docs/STAGE3_KAFKA_STREAMS.md` | Kafka Streams topology — processors, topics, run guide, outputs |
| `docs/STAGE4_PRODUCTION_HARDENING.md` | Multi-node hardening — Redis fan-out, gRPC TLS/auth, GraphQL limits, metrics |
| `docs/STAGE5_ML_PIPELINE.md` | ML pipeline — EnsembleModel, A/B routing, Processor 4, GraphQL Mutation, Prometheus |
| `docs/STAGE6_BACKTESTING.md` | Backtesting engine — BacktestRunner algo, metrics reference, walk-forward, GraphQL guide |
| `docs/STAGE7_DATA_SOURCING.md` | Real data sourcing — source-by-source real/blocked/deferred verdict, config, verification |
| `docs/STAGE9_INFRASTRUCTURE.md` | Real MySQL/ClickHouse/Redis/Kafka — setup, two-datasource design, the MySQL reserved-word bug, verification |
| `docs/HFT_ARCHITECTURE.md` | Full platform architecture, 26 sections incl. §22 IPO engine, §23 Web UI, §24 ASRB, §25 Identity/Admin, §26 Data infra |
| `docs/ASRB_TECHNICAL_DISCLOSURE.md` | Adaptive Source Reliability Bandit — algorithm spec, prior art, novelty draft, eval methodology |
| `docs/STAGES_OVERVIEW.md` | This file — evolution summary, performance comparison |

---

## 11. WHAT COMES NEXT (Planned)

```
STAGE 5 (COMPLETE): ML Model Integration & A/B Testing
  ├── MLFeatureVector (41 features) + MLFeatureExtractor
  ├── EnsembleModel (Model B) — Momentum + MeanReversion + Trend
  ├── ModelABRouter — consistent-hash symbol routing
  ├── ModelPerformanceTracker — Micrometer gauges + Redis
  ├── Kafka Streams Processor 4 — signals-ml-scored topic
  ├── GraphQL Mutation type + recordSignalOutcome()
  └── modelPerformance() + modelAssignment() queries

STAGE 6 (COMPLETE): Backtesting & Strategy Validation Engine
  ├── BacktestRunner (@Async) — OHLCV replay with inline TA
  ├── StrategyMetricsEngine — 12 metrics (Sharpe, CAGR, drawdown)
  ├── WalkForwardValidator — N rolling windows, 80/20 split
  ├── BacktestRun + BacktestTrade (@Entity) — full audit trail
  ├── OHLCVDataRepository — added (was missing)
  ├── Kafka Streams Processor 5 — backtest-results topic (conditional)
  ├── GraphQL runBacktest mutation + backtestProgress subscription
  └── backtestRun, listBacktestRuns, walkForwardValidation queries

STAGE 7 (COMPLETE): Real Intelligence Data Sourcing
  ├── SEC EDGAR filings, real NSE FII/DII flow — verified live
  ├── NewsAPI + FRED — real integrations switched on by default
  ├── Reddit — real OAuth2 flow implemented, needs user-supplied API keys
  ├── StockTwits, GDELT — implemented, blocked/unverified respectively (see STAGE7 doc)
  └── India repo rate/CPI/GDP, India fundamentals — honestly left on fallback (no ToS-safe API found)

STAGE 8 (COMPLETE): Identity, Admin Platform & Web UI
  ├── com.hft.identity — User/Role, JwtService (com.auth0:java-jwt), JwtAuthFilter (SecurityConfig's
  │     JWT filter was pure scaffolding before this — no filter class existed anywhere)
  ├── /api/v1/auth/register|login|refresh|me — real JWT issuance, access+refresh tokens
  ├── TestUserSeeder — PTD2315/omanu01@gmail.com, USER+ADMIN, fresh SecureRandom password each boot
  ├── com.hft.admin — PlatformApiCredential (AES-256-GCM at rest), AdminSettingsController —
  │     admin-managed NewsAPI/FRED/Reddit keys, write-only, distinct from per-user BYOC (§24.3, not built)
  ├── Web UI at src/main/resources/static/ — static HTML/CSS/vanilla-JS, no build step, 3-state
  │     Light/Dark/Auto theme toggle, mobile/tablet/desktop responsive, REST-only (GraphQL/WS
  │     subscriptions deferred as a fast-follow)
  └── Verified end-to-end: full curl smoke test + live in a real Chrome browser (login, a real
        MSFT recommendation rendered from genuinely live Alpha Vantage/NewsAPI/SEC EDGAR data,
        admin credential save/clear round-trip, theme cycling) — not just unit/compile-verified

STAGE 9 (COMPLETE): IPO Engine, ASRB & Real Infrastructure
  ├── 9a — IPO Buy/Sell Decision Engine (§22) — pre-listing apply/avoid, post-listing
  │     hold/sell, live-verified against 3 seeded sample IPOs
  ├── 9b — ASRB — Adaptive Source Reliability Bandit (§24, ASRB_TECHNICAL_DISCLOSURE.md) —
  │     built + tested (13 tests), deliberately not wired into the live pipeline yet
  └── 9c — Real MySQL/ClickHouse/Redis/Kafka via a new "docker" Spring profile (§26,
        STAGE9_INFRASTRUCTURE.md), replacing H2-in-memory/no-broker/simple-cache; real
        Alpha Vantage key wired in (25 req/day free tier — stricter than assumed)

STAGE 10 (Planned, was "Stage 7", then "Stage 8", then "Stage 9"): Deep Learning & ONNX Serving
  ├── ONNX model serving via DJL (Deep Java Library)
  ├── Real-time feature vector construction in Kafka Streams
  ├── 45-feature vector → model inference → confidence score update
  └── Outcome reconciliation using backtest-results topic (Stage 6)

ALSO DESIGNED (docs/HFT_ARCHITECTURE.md §24.3), not yet built, no stage number assigned:
  ├── Wiring ASRB (9b) into the live recommendation pipeline — needs a calibration-data decision
  └── Per-user BYOC ConnectedAccount (§24.3, §25 identity foundation exists from Stage 8) —
        a user linking their own read-only Twitter/Reddit account, pooled into the shared signal
```

---

*Repository: https://github.com/omjee01/HFT_MarketIntelligence*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*
