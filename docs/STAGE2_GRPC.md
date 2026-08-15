# STAGE 2 — gRPC Internal Pipeline

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0 | Built on: io.grpc 1.65.0 + Protobuf 3.25.5*

---

## 1. STAGE OVERVIEW

Stage 2 exposes the HFT platform's internal services over a **gRPC binary API** alongside
the existing REST and GraphQL layers. gRPC is ideal for:

- Low-latency, high-throughput inter-service calls (binary Protobuf vs JSON)
- Type-safe, version-controlled contracts via `.proto` schemas
- Server-streaming for live quote and signal feeds (Kafka-backed in Stage 3)
- External algo-trading clients and downstream microservices

```
┌────────────────────────────────────────────────────────────────────────┐
│                    STAGE 2 ARCHITECTURE                                │
│                                                                        │
│  HTTP CLIENT        GRPC CLIENT           BROWSER                      │
│  (REST/JSON)        (Proto/Binary)        (GraphiQL)                   │
│       │                   │                   │                        │
│       ▼                   ▼                   ▼                        │
│  ┌──────────┐    ┌─────────────────┐   ┌──────────────┐               │
│  │  :8080   │    │     :9090       │   │   :8080      │               │
│  │  REST    │    │  gRPC (Netty)   │   │  GraphQL     │               │
│  └──────────┘    └─────────────────┘   └──────────────┘               │
│       │                   │                   │                        │
│       └───────────────────┴───────────────────┘                        │
│                           │                                            │
│                ┌──────────▼──────────┐                                 │
│                │   SERVICE LAYER     │                                 │
│                │                     │                                 │
│                │  MarketDataAgg.Svc  │                                 │
│                │  TechnicalAnalysis  │                                 │
│                │  SentimentAnalysis  │                                 │
│                │  FundamentalAnalysis│                                 │
│                │  MacroGeopolitical  │                                 │
│                │  RecommendationEng. │                                 │
│                └─────────────────────┘                                 │
│                           │                                            │
│                ┌──────────▼──────────┐                                 │
│                │  H2 / PostgreSQL    │                                 │
│                │  Redis Cache        │                                 │
│                │  Kafka (async)      │                                 │
│                └─────────────────────┘                                 │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. TECHNOLOGY STACK

| Component | Technology | Version |
|---|---|---|
| gRPC Runtime | io.grpc:grpc-netty-shaded | 1.65.0 |
| Protobuf Runtime | io.grpc:grpc-protobuf | 1.65.0 |
| Stub Generation | io.grpc:grpc-stub | 1.65.0 |
| Protobuf Compiler | com.google.protobuf:protoc | 3.25.5 |
| Code-gen plugin | io.grpc:protoc-gen-grpc-java | 1.65.0 |
| Gradle plugin | com.google.protobuf | 0.9.4 |
| Security patch | protobuf-java forced to 3.25.5 | CVE-2024-7254 |
| Server lifecycle | Spring SmartLifecycle | 6.1.x |
| Port | gRPC Netty | 9090 |

---

## 3. PROTO CONTRACT DESIGN

Proto files location: `src/main/proto/hft/`
Generated Java output: `build/generated/source/proto/main/`

### 3.1 File Layout

```
src/main/proto/hft/
├── common.proto       ← shared enums (Market, AssetType, SignalType, etc.)
├── market_data.proto  ← MarketDataService (GetQuote, BatchQuotes, StreamQuotes)
├── analysis.proto     ← AnalysisService (Technical, Sentiment, Fundamentals, Macro)
└── signal.proto       ← SignalService (Recommendation, Screener, StreamSignals)
```

### 3.2 common.proto — Shared Enums

```protobuf
syntax = "proto3";
package hft;
option java_package = "com.hft.grpc.proto";
option java_multiple_files = true;

enum MarketProto {
  MARKET_UNSPECIFIED = 0;
  US_NYSE = 1;  US_NASDAQ = 2;  US_AMEX = 3;
  US_CBOE = 4;  US_COMEX = 5;
  INDIA_NSE = 6;  INDIA_BSE = 7;  INDIA_MCX = 8;
  ALL = 9;
}
enum AssetTypeProto {
  ASSET_TYPE_UNSPECIFIED = 0;
  STOCK = 1;  OPTION = 2;  FUTURE = 3;  COMMODITY = 4;
  IPO = 5;  ETF = 6;  MUTUAL_FUND = 7;  BOND = 8;
  CURRENCY = 9;  CRYPTO = 10;
}
enum SignalTypeProto { SIGNAL_UNSPECIFIED=0; STRONG_BUY=1; BUY=2; HOLD=3; SELL=4; STRONG_SELL=5; WATCH=6; }
enum RiskLevelProto  { RISK_UNSPECIFIED=0; VERY_LOW=1; LOW=2; MEDIUM=3; HIGH=4; VERY_HIGH=5; }
enum TimeHorizonProto{ HORIZON_UNSPECIFIED=0; INTRADAY=1; SHORT_TERM=2; MEDIUM_TERM=3; LONG_TERM=4; }
```

### 3.3 market_data.proto — Service Contract

```protobuf
service MarketDataService {
  rpc GetQuote      (QuoteRequest)       returns (StockQuoteProto);        // Unary
  rpc GetBatchQuotes(BatchQuoteRequest)  returns (BatchQuoteResponse);     // Unary
  rpc StreamQuotes  (BatchQuoteRequest)  returns (stream StockQuoteProto); // Server-streaming
}

message QuoteRequest {
  string symbol     = 1;
  MarketProto market = 2;
}
message BatchQuoteRequest {
  repeated string symbols = 1;
  MarketProto market       = 2;
}
message StockQuoteProto {
  string symbol            = 1;
  string company_name      = 2;
  MarketProto market       = 3;
  AssetTypeProto asset_type= 4;
  double current_price     = 5;
  double open_price        = 6;
  double high_price        = 7;
  double low_price         = 8;
  double previous_close    = 9;
  double change_amount     = 10;
  double change_percent    = 11;
  int64  volume            = 12;
  int64  avg_volume_10d    = 13;
  double market_cap        = 14;
  double pe_ratio          = 15;
  double high_52_week      = 16;
  double low_52_week       = 17;
  // ... 27 fields total
  int64  last_updated_epoch_ms = 27;
}
```

### 3.4 analysis.proto — Service Contract

```protobuf
service AnalysisService {
  rpc GetTechnical  (AnalysisRequest) returns (TechnicalIndicatorsProto);
  rpc GetSentiment  (AnalysisRequest) returns (SentimentDataProto);
  rpc GetFundamentals(AnalysisRequest) returns (FundamentalDataProto);
  rpc GetMacro      (MarketRequest)   returns (MacroDataProto);
}

message AnalysisRequest {
  string symbol      = 1;
  MarketProto market = 2;
}
message MarketRequest {
  MarketProto market = 1;
}
// TechnicalIndicatorsProto: 43 fields (RSI, MACD, BB, ATR, SMA, EMA, OBV, VWAP, etc.)
// SentimentDataProto: 21 fields (score, mentions, news count, fear/greed, etc.)
// FundamentalDataProto: 25 fields (P/E, P/B, EPS, revenue growth, etc.)
// MacroDataProto: 33 fields (GDP, inflation, rates, FII/DII flows, VIX, etc.)
```

### 3.5 signal.proto — Service Contract

```protobuf
service SignalService {
  rpc GetRecommendation(RecommendationRequest) returns (TradeRecommendationProto);
  rpc ScreenStocks     (ScreenerRequest)       returns (ScreenerResponse);
  rpc StreamSignals    (RecommendationRequest) returns (stream TradeRecommendationProto);
}

message ScreenerRequest {
  MarketProto market          = 1;
  double min_confidence       = 2;
  double min_technical_score  = 3;
  double min_fundamental_score= 4;
  repeated string sectors     = 5;
  int32  limit                = 6;
}
message TradeRecommendationProto {
  string symbol               = 1;
  string company_name         = 2;
  SignalTypeProto signal       = 3;
  MarketProto market          = 4;
  AssetTypeProto asset_type   = 5;
  TimeHorizonProto time_horizon= 6;
  RiskLevelProto risk_level   = 7;
  double composite_score      = 8;
  double confidence_percent   = 9;
  double entry_price          = 10;
  double target_price         = 11;
  double stop_loss_price      = 12;
  double expected_profit_percent = 13;
  double risk_reward_ratio    = 14;
  int32  holding_period_days  = 15;
  repeated string key_reasons = 16;
  repeated string key_risks   = 17;
  // ... 34 fields total
}
```

---

## 4. gRPC SERVICE IMPLEMENTATIONS

```
┌──────────────────────────────────────────────────────────────────┐
│                   gRPC SERVICE LAYER                             │
│                                                                  │
│  MarketDataGrpcService extends MarketDataServiceGrpc.ImplBase    │
│  ├── getQuote()        → delegates to MarketDataAggregatorService│
│  ├── getBatchQuotes()  → parallel map over symbol list           │
│  └── streamQuotes()    → snapshot then live via StreamSinkBridge │
│                                                                  │
│  AnalysisGrpcService extends AnalysisServiceGrpc.ImplBase        │
│  ├── getTechnical()    → TechnicalAnalysisService.analyze()      │
│  ├── getSentiment()    → SentimentAnalysisService.analyzeSentiment│
│  ├── getFundamentals() → FundamentalAnalysisService.analyze()    │
│  └── getMacro()        → MacroGeopoliticalService.getMacroData() │
│                                                                  │
│  SignalGrpcService extends SignalServiceGrpc.ImplBase             │
│  ├── getRecommendation()→ RecommendationEngine.generateRec.()    │
│  ├── screenStocks()   → generateTopRecommendations + filter      │
│  └── streamSignals()  → snapshot + live via StreamSinkBridge     │
└──────────────────────────────────────────────────────────────────┘
```

### ProtoMapper — Domain ↔ Protobuf Conversion

`src/main/java/com/hft/grpc/ProtoMapper.java`

```
Static utility class providing null-safe conversions:

  Primitive helpers:
    d(Double) / d(BigDecimal) → double (0.0 if null)
    i(Integer)                → int   (0 if null)
    l(Long)                   → long  (0L if null)
    b(Boolean)                → bool  (false if null)
    s(String)                 → String ("" if null)
    ls(List<String>)          → List<String> (empty if null)

  Enum converters (bidirectional):
    toProto(Market)     / fromProto(MarketProto)
    toProto(AssetType)
    toProto(SignalType)
    toProto(RiskLevel)
    toProto(TimeHorizon)

  Domain → Proto:
    toProto(StockQuote)           → StockQuoteProto
    toProto(TechnicalIndicators)  → TechnicalIndicatorsProto
    toProto(SentimentData)        → SentimentDataProto
    toProto(FundamentalData)      → FundamentalDataProto
    toProto(MacroData)            → MacroDataProto
    toProto(TradeRecommendation)  → TradeRecommendationProto
```

### GrpcServerConfig — Spring SmartLifecycle

`src/main/java/com/hft/grpc/GrpcServerConfig.java`

```
Implements SmartLifecycle so the gRPC Netty server:
  - Starts AFTER all Spring beans are ready (phase = MAX_VALUE - 100)
  - Stops gracefully during context shutdown (awaitTermination 5s)
  - Port: ${grpc.server.port:9090} (dev: 9090 via application-dev.yml)
  - Max inbound message size: 64 MB
  - Services registered: MarketDataGrpcService, AnalysisGrpcService, SignalGrpcService
```

---

## 5. HOW TO RUN — STAGE 2

### Prerequisites

| Requirement | Notes |
|---|---|
| Java 21 | `java -version` |
| Gradle 9 | `gradle -version` |
| grpcurl | `brew install grpcurl` (optional, for testing) |
| BloomRPC / Kreya | GUI gRPC client (optional) |

### Step 1 — Generate Proto Java Sources

```bash
cd HFT_MarketIntelligence
gradle generateProto

# Generated files appear in:
# build/generated/source/proto/main/java/com/hft/grpc/proto/
#   MarketDataServiceGrpc.java
#   AnalysisServiceGrpc.java
#   SignalServiceGrpc.java
#   StockQuoteProto.java, TechnicalIndicatorsProto.java, ... (all message classes)
```

### Step 2 — Build and Test

```bash
gradle build
# Expected: 24 tests passed, BUILD SUCCESSFUL
```

### Step 3 — Start the Application

```bash
gradle bootRun --args='--spring.profiles.active=dev'

# Startup log shows:
# [GrpcServerConfig] gRPC server started on port 9090
# [TomcatWebServer] started on port 8080
```

Both ports are now live:
- **http://localhost:8080** — REST + GraphQL
- **grpc://localhost:9090** — gRPC (Protobuf binary)

---

## 6. INPUTS — gRPC REQUESTS

### 6.1 Testing with grpcurl (CLI)

Install: `brew install grpcurl`

#### Service Discovery (list all services)

```bash
grpcurl -plaintext localhost:9090 list
# hft.MarketDataService
# hft.AnalysisService
# hft.SignalService
```

#### List Methods for a Service

```bash
grpcurl -plaintext localhost:9090 list hft.MarketDataService
# hft.MarketDataService.GetBatchQuotes
# hft.MarketDataService.GetQuote
# hft.MarketDataService.StreamQuotes
```

#### Describe a Message Type

```bash
grpcurl -plaintext localhost:9090 describe hft.StockQuoteProto
```

### 6.2 MarketDataService — GetQuote

```bash
grpcurl -plaintext \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  localhost:9090 \
  hft.MarketDataService/GetQuote
```

### 6.3 MarketDataService — GetBatchQuotes

```bash
grpcurl -plaintext \
  -d '{"symbols":["AAPL","MSFT","GOOGL"],"market":"US_NASDAQ"}' \
  localhost:9090 \
  hft.MarketDataService/GetBatchQuotes
```

### 6.4 MarketDataService — StreamQuotes (server-streaming)

```bash
grpcurl -plaintext \
  -d '{"symbols":["HDFCBANK.NSE","RELIANCE.NSE"],"market":"INDIA_NSE"}' \
  localhost:9090 \
  hft.MarketDataService/StreamQuotes
# Streams until client disconnects (Ctrl+C)
```

### 6.5 AnalysisService — GetTechnical

```bash
grpcurl -plaintext \
  -d '{"symbol":"RELIANCE.NSE","market":"INDIA_NSE"}' \
  localhost:9090 \
  hft.AnalysisService/GetTechnical
```

### 6.6 AnalysisService — GetSentiment

```bash
grpcurl -plaintext \
  -d '{"symbol":"TSLA","market":"US_NASDAQ"}' \
  localhost:9090 \
  hft.AnalysisService/GetSentiment
```

### 6.7 AnalysisService — GetMacro

```bash
grpcurl -plaintext \
  -d '{"market":"INDIA_NSE"}' \
  localhost:9090 \
  hft.AnalysisService/GetMacro
```

### 6.8 SignalService — GetRecommendation

```bash
grpcurl -plaintext \
  -d '{"symbol":"INFY.NSE","market":"INDIA_NSE"}' \
  localhost:9090 \
  hft.SignalService/GetRecommendation
```

### 6.9 SignalService — ScreenStocks

```bash
grpcurl -plaintext \
  -d '{
    "market": "US_NASDAQ",
    "min_confidence": 65.0,
    "min_technical_score": 60.0,
    "min_fundamental_score": 55.0,
    "limit": 10
  }' \
  localhost:9090 \
  hft.SignalService/ScreenStocks
```

### 6.10 SignalService — StreamSignals (server-streaming)

```bash
grpcurl -plaintext \
  -d '{"symbol":"","market":"US_NASDAQ"}' \
  localhost:9090 \
  hft.SignalService/StreamSignals
# Emits one snapshot, then streams live Kafka-enriched signals
```

### 6.11 With Proto File Descriptor (no reflection)

If server reflection is not enabled (production), pass `.proto` files directly:

```bash
grpcurl -plaintext \
  -import-path src/main/proto \
  -proto hft/market_data.proto \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  localhost:9090 \
  hft.MarketDataService/GetQuote
```

### 6.12 Testing with BloomRPC / Kreya (GUI)

1. Open BloomRPC or Kreya
2. Add server: `localhost:9090` (plaintext, no TLS in dev)
3. Import proto files from `src/main/proto/hft/`
4. Select a service/method and fill in the JSON request body
5. Click Send — response displays as formatted JSON

---

## 7. EXPECTED OUTPUTS

### 7.1 GetQuote Response

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "market": "US_NASDAQ",
  "assetType": "STOCK",
  "currentPrice": 189.25,
  "openPrice": 187.40,
  "highPrice": 190.10,
  "lowPrice": 186.80,
  "previousClose": 187.90,
  "changeAmount": 1.35,
  "changePercent": 0.72,
  "volume": "62450000",
  "avgVolume10d": "55210000",
  "marketCap": 2940000000000.0,
  "peRatio": 31.2,
  "high52Week": 198.23,
  "low52Week": 124.17,
  "lastUpdatedEpochMs": "1748430600000"
}
```

### 7.2 GetRecommendation Response

```json
{
  "symbol": "HDFCBANK.NSE",
  "companyName": "HDFC Bank Limited",
  "signal": "STRONG_BUY",
  "market": "INDIA_NSE",
  "assetType": "STOCK",
  "timeHorizon": "MEDIUM_TERM",
  "riskLevel": "LOW",
  "compositeScore": 87.4,
  "confidencePercent": 82.0,
  "entryPrice": 1672.50,
  "targetPrice": 1980.00,
  "stopLossPrice": 1585.00,
  "expectedProfitPercent": 18.38,
  "riskRewardRatio": 3.54,
  "holdingPeriodDays": 63,
  "keyReasons": [
    "RSI(14) at 42 — emerging from oversold zone",
    "Golden cross forming: SMA50 approaching SMA200",
    "Q4 FY26 PAT beat by 14%, NII growth 18% YoY"
  ],
  "keyRisks": [
    "Global recession fears could trigger FII selling",
    "India VIX above 14"
  ]
}
```

### 7.3 ScreenStocks Response

```json
{
  "recommendations": [
    {
      "symbol": "INFY.NSE",
      "signal": "BUY",
      "compositeScore": 79.2,
      "confidencePercent": 71.0,
      "targetPrice": 1850.00
    },
    {
      "symbol": "TCS.NSE",
      "signal": "BUY",
      "compositeScore": 76.8,
      "confidencePercent": 68.5,
      "targetPrice": 4100.00
    }
  ],
  "totalCount": 2
}
```

### 7.4 Error Response (NOT_FOUND)

```
ERROR:
  Code: NotFound
  Message: No recommendation for UNKNOWN.SYM
```

### 7.5 Startup Log

```
[GrpcServerConfig] Starting gRPC server on port 9090...
[GrpcServerConfig] gRPC server started on port 9090
[TomcatWebServer] Tomcat started on port 8080 with context path '/'
[HFTApplication] Started HFTApplication in 4.823 seconds
```

---

## 8. PORT LAYOUT

```
┌──────────────────────────────────────────────────────────┐
│                    PORT ALLOCATION                       │
│                                                          │
│  :8080  HTTP/1.1 + HTTP/2 (Spring Boot Tomcat)          │
│  ├── POST /graphql          GraphQL queries/mutations    │
│  ├── WS   /graphql-ws       GraphQL subscriptions        │
│  ├── GET  /graphiql         GraphiQL browser IDE         │
│  ├── GET  /swagger-ui.html  Swagger REST docs            │
│  ├── GET  /actuator/health  Health check                 │
│  └── GET  /h2-console       H2 DB console (dev only)    │
│                                                          │
│  :9090  HTTP/2 (gRPC Netty, plaintext in dev)           │
│  ├── hft.MarketDataService                               │
│  │   ├── GetQuote                                        │
│  │   ├── GetBatchQuotes                                  │
│  │   └── StreamQuotes (server-streaming)                 │
│  ├── hft.AnalysisService                                 │
│  │   ├── GetTechnical                                    │
│  │   ├── GetSentiment                                    │
│  │   ├── GetFundamentals                                 │
│  │   └── GetMacro                                        │
│  └── hft.SignalService                                   │
│      ├── GetRecommendation                               │
│      ├── ScreenStocks                                    │
│      └── StreamSignals (server-streaming)                │
└──────────────────────────────────────────────────────────┘
```

---

## 9. ENHANCEMENTS OVER REST/GRAPHQL

### 9.1 Why gRPC?

| Dimension | REST/JSON | GraphQL | gRPC/Protobuf |
|---|---|---|---|
| Payload size | Large (text JSON) | Medium (selective fields) | Small (binary) |
| Parsing overhead | High (text decode) | Medium | Minimal |
| Schema contract | OpenAPI (runtime) | SDL (runtime) | .proto (compile-time) |
| Streaming | SSE / WebSocket | WebSocket subscriptions | Native (HTTP/2) |
| Code generation | Optional | Optional | Enforced |
| Cross-language | Universal | Universal | Language-agnostic stubs |
| Best for | Public API | UI/BFF layer | Internal services |

### 9.2 gRPC vs REST Latency (typical)

```
Same service call (GetQuote):
  REST/JSON    ~8-15ms   (text serialize + HTTP/1.1 overhead)
  gRPC/Proto   ~1-4ms    (binary encode + HTTP/2 multiplexing)

StreamSignals (live feed):
  WebSocket/JSON  ~5-12ms per message
  gRPC streaming  ~1-3ms per message
```

### 9.3 Security — CVE-2024-7254 Mitigation

gRPC 1.65.0 transitively pulls `protobuf-java:3.25.1` which has a known DoS vulnerability.
Patched in `build.gradle.kts`:

```kotlin
configurations.all {
    resolutionStrategy.force("com.google.protobuf:protobuf-java:3.25.5")
    resolutionStrategy.force("com.google.protobuf:protobuf-java-util:3.25.5")
}
```

Static scanners may still report 3.25.1 (they read the POM, not runtime resolution).
At runtime, `./gradlew dependencies | grep protobuf-java` confirms 3.25.5.

### 9.4 Production Hardening (not in Stage 2, planned)

| Item | How to enable |
|---|---|
| TLS on port 9090 | `NettyServerBuilder.useTransportSecurity(certFile, keyFile)` |
| JWT interceptor | `ServerInterceptor` checking `Authorization` metadata |
| Server reflection | `ProtoReflectionService.newInstance()` added to server |
| Load balancing | gRPC client-side lb (round-robin) or Envoy sidecar |

---

## 10. CONFIGURATION REFERENCE

### application-dev.yml (gRPC section)

```yaml
grpc:
  server:
    port: 9090    # gRPC Netty port (HTTP stays on 8080)
```

### build.gradle.kts (gRPC/Protobuf section)

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.65.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { create("grpc") }
        }
    }
}
```

---

*Stage 2 Complete | Commit: 294b8b4*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*