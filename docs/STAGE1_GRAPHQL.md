# STAGE 1 — GraphQL API Layer

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0 | Built on: Spring Boot 3.2.5 + Spring GraphQL*

---

## 1. STAGE OVERVIEW

Stage 1 adds a **GraphQL API layer** on top of the existing REST endpoints. All market data,
analysis, recommendations, and real-time signal subscriptions are now accessible via a
single `/graphql` endpoint, replacing the need to stitch multiple REST calls client-side.

```
┌─────────────────────────────────────────────────────────────────┐
│                    STAGE 1 ARCHITECTURE                         │
│                                                                 │
│  CLIENT (Browser / App / CLI)                                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────┐                        │
│  │  Spring Security (JWT filter)       │  :8080                 │
│  └─────────────────────────────────────┘                        │
│       │                                                         │
│       ├─► POST /graphql      ─── Query / Mutation               │
│       ├─► GET  /graphiql     ─── GraphiQL Browser IDE           │
│       ├─► WS   /graphql-ws   ─── Subscriptions (graphql-ws)     │
│       └─► GET  /api-docs     ─── Swagger (REST still live)      │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                  GRAPHQL RESOLVER LAYER                 │    │
│  │                                                         │    │
│  │  StockDashboardResolver   ── aggregates 4 services      │    │
│  │  MarketDataResolver       ── quotes, batch, history     │    │
│  │  AnalysisResolver         ── TA / FA / Sentiment / Macro│    │
│  │  RecommendationResolver   ── signals, screener          │    │
│  │  SignalSubscriptionResolver── liveSignals, liveQuote    │    │
│  └─────────────────────────────────────────────────────────┘    │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   SERVICE LAYER (unchanged)             │    │
│  │  MarketDataAggregatorService  TechnicalAnalysisService  │    │
│  │  SentimentAnalysisService     FundamentalAnalysisService│    │
│  │  MacroGeopoliticalService     RecommendationEngine      │    │
│  └─────────────────────────────────────────────────────────┘    │
│       │                                                         │
│       ▼                                                         │
│  ┌───────────────────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │  H2 / PostgreSQL (JPA)│  │  Redis   │  │  Kafka (events)  │  │
│  └───────────────────────┘  └──────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. TECHNOLOGY STACK

| Component | Technology | Version |
|---|---|---|
| Framework | Spring Boot | 3.2.5 |
| GraphQL Runtime | Spring GraphQL (graphql-java) | Built-in 3.2.5 |
| Scalar Extensions | graphql-java-extended-scalars | 22.0 |
| Schema Protocol | SDL (Schema Definition Language) | — |
| Subscriptions | graphql-ws over WebSocket | — |
| Security | Spring Security + JWT | Stateless |
| Build | Gradle Kotlin DSL | 9.x |
| Java | OpenJDK | 21 |

---

## 3. GRAPHQL SCHEMA OVERVIEW

Schema location: `src/main/resources/graphql/schema.graphqls`

### 3.1 Custom Scalars

```graphql
scalar DateTime      # maps to java.time.LocalDateTime (ISO-8601)
scalar Date          # maps to java.time.LocalDate
scalar BigDecimal    # maps to java.math.BigDecimal (precision-safe)
scalar Long          # maps to java.lang.Long
```

### 3.2 Enums

```graphql
enum Market {
  US_NYSE | US_NASDAQ | US_AMEX | US_CBOE | US_COMEX
  INDIA_NSE | INDIA_BSE | INDIA_MCX | ALL
}
enum AssetType  { STOCK | OPTION | FUTURE | COMMODITY | IPO | ETF
                  MUTUAL_FUND | BOND | CURRENCY | CRYPTO }
enum SignalType { STRONG_BUY | BUY | HOLD | SELL | STRONG_SELL | WATCH }
enum RiskLevel  { VERY_LOW | LOW | MEDIUM | HIGH | VERY_HIGH }
enum TimeHorizon{ INTRADAY | SHORT_TERM | MEDIUM_TERM | LONG_TERM }
```

### 3.3 Root Types

```
Query
├── stockDashboard(symbol, market)    → StockDashboard
├── quote(symbol, market)             → StockQuote
├── batchQuotes(symbols, market)      → [StockQuote]
├── technical(symbol, market)         → TechnicalIndicators
├── sentiment(symbol, market)         → SentimentData
├── fundamentals(symbol, market)      → FundamentalData
├── macro(market)                     → MacroData
├── recommendation(symbol, market)   → TradeRecommendation
├── topRecommendations(market, limit) → [TradeRecommendation]
└── screenStocks(input)               → ScreenerResult

Mutation
└── generateFreshSignal(symbol, market) → TradeRecommendation

Subscription
├── liveSignals(market)               → TradeRecommendation (stream)
├── watchlistSignals(input)           → TradeRecommendation (stream)
└── liveQuote(symbol, market)         → StockQuote (stream)
```

---

## 4. RESOLVER ARCHITECTURE

```
┌──────────────────────────────────────────────────────────────────────┐
│                        RESOLVER MAP                                  │
│                                                                      │
│  @QueryMapping         @Controller class                             │
│  ─────────────────     ──────────────────────────────────────────    │
│  stockDashboard    ──► StockDashboardResolver                        │
│      (aggregates 4 service calls in one round trip)                 │
│                                                                      │
│  quote             ──► MarketDataResolver                            │
│  batchQuotes       ──► MarketDataResolver                            │
│                                                                      │
│  technical         ──► AnalysisResolver                              │
│  sentiment         ──► AnalysisResolver                              │
│  fundamentals      ──► AnalysisResolver                              │
│  macro             ──► AnalysisResolver                              │
│                                                                      │
│  recommendation    ──► RecommendationResolver                        │
│  topRecommendations──► RecommendationResolver                        │
│  screenStocks      ──► RecommendationResolver                        │
│                                                                      │
│  generateFreshSignal──► RecommendationResolver (@MutationMapping)    │
│                                                                      │
│  liveSignals       ──► SignalSubscriptionResolver (@SubscriptionMapping)│
│  watchlistSignals  ──► SignalSubscriptionResolver                    │
│  liveQuote         ──► SignalSubscriptionResolver                    │
└──────────────────────────────────────────────────────────────────────┘
```

### StockDashboard — single-call aggregation

```
Client sends: { stockDashboard(symbol: "AAPL", market: US_NASDAQ) { ... } }

StockDashboardResolver:
  1. marketDataService.getQuote(symbol, market)
  2. taService.analyze(symbol, market)
  3. sentimentService.analyzeSentiment(symbol, market)
  4. engine.generateRecommendation(symbol, market)
  → bundles into StockDashboard record

Before Stage 1 (REST): 4 HTTP calls, N+1 round trips
After Stage 1 (GraphQL): 1 HTTP call, zero over-fetching
```

---

## 5. HOW TO RUN — STAGE 1

### Prerequisites

| Requirement | Notes |
|---|---|
| Java 21 | `java -version` |
| Gradle 9 | `gradle -version` |
| Nothing else | H2 in-memory DB, Kafka disabled in dev profile |

### Step 1 — Build

```bash
cd HFT_MarketIntelligence
gradle compileJava
```

### Step 2 — Run Tests (24 tests)

```bash
gradle test
# Expected: 24 tests passed, 0 failed
```

### Step 3 — Start the Application

```bash
gradle bootRun --args='--spring.profiles.active=dev'

# OR with environment variables for external APIs:
ALPHA_VANTAGE_API_KEY=your_key gradle bootRun --args='--spring.profiles.active=dev'
```

Application starts on **http://localhost:8080**

### Step 4 — Open GraphiQL Browser IDE

Navigate to: **http://localhost:8080/graphiql**

GraphiQL provides:
- Schema explorer (Docs panel on the right)
- Query autocomplete and syntax highlighting
- Variable panel for parameterized queries
- Subscription support over WebSocket

### Step 5 — View Full Schema SDL

```bash
curl http://localhost:8080/graphql/schema
```

### Step 6 — H2 Database Console (dev only)

Navigate to: **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:hftdb`
- Username: `sa` | Password: (empty)

---

## 6. INPUTS — GRAPHQL OPERATIONS

### 6.1 Query: Stock Dashboard (aggregated)

```graphql
query StockDashboard {
  stockDashboard(symbol: "HDFCBANK.NSE", market: INDIA_NSE) {
    symbol
    market
    quote {
      currentPrice
      changePercent
      volume
      high52Week
      low52Week
    }
    technical {
      rsi14
      macdLine
      macdSignal
      bb20Upper
      bb20Lower
      technicalScore
    }
    sentiment {
      overallSentiment
      sentimentScore
      bullishPercent
      bearishPercent
    }
    recommendation {
      signal
      compositeScore
      confidencePercent
      entryPrice
      targetPrice
      stopLossPrice
      riskRewardRatio
      timeHorizon
      riskLevel
    }
  }
}
```

### 6.2 Query: Single Quote

```graphql
query GetQuote {
  quote(symbol: "AAPL", market: US_NASDAQ) {
    symbol
    companyName
    currentPrice
    changePercent
    volume
    marketCap
    peRatio
    high52Week
    low52Week
    lastUpdated
  }
}
```

### 6.3 Query: Batch Quotes

```graphql
query BatchQuotes {
  batchQuotes(symbols: ["AAPL", "MSFT", "GOOGL"], market: US_NASDAQ) {
    symbol
    currentPrice
    changePercent
  }
}
```

### 6.4 Query: Technical Analysis

```graphql
query TechnicalAnalysis {
  technical(symbol: "RELIANCE.NSE", market: INDIA_NSE) {
    rsi14
    macdLine
    macdSignal
    macdHistogram
    sma20
    sma50
    sma200
    ema9
    ema21
    bb20Upper
    bb20Middle
    bb20Lower
    atr14
    obvTrend
    vwap
    supertrend
    technicalScore
    goldenCross
    deathCross
    supportLevel
    resistanceLevel
  }
}
```

### 6.5 Query: Top Recommendations

```graphql
query TopRecommendations {
  topRecommendations(market: US_NASDAQ, limit: 5) {
    rank
    symbol
    companyName
    signal
    compositeScore
    confidencePercent
    entryPrice
    targetPrice
    stopLossPrice
    expectedProfitPercent
    riskRewardRatio
    holdingPeriodDays
    keyReasons
    riskLevel
    timeHorizon
  }
}
```

### 6.6 Query: Stock Screener

```graphql
query ScreenStocks {
  screenStocks(input: {
    market: INDIA_NSE
    minCompositeScore: 70.0
    minConfidence: 65.0
    assetTypes: [STOCK]
    signals: [STRONG_BUY, BUY]
    riskLevels: [LOW, MEDIUM]
    timeHorizons: [SHORT_TERM, MEDIUM_TERM]
    limit: 10
  }) {
    totalMatched
    recommendations {
      symbol
      signal
      compositeScore
      targetPrice
    }
  }
}
```

### 6.7 Query: Sentiment Analysis

```graphql
query Sentiment {
  sentiment(symbol: "TSLA", market: US_NASDAQ) {
    overallSentiment
    sentimentScore
    bullishPercent
    bearishPercent
    newsHeadlineCount
    twitterMentions
    redditMentions
    fearGreedIndex
    institutionalSentiment
    retailSentiment
    analystRating
    priceTargetConsensus
    dataFreshness
  }
}
```

### 6.8 Query: Macro Data

```graphql
query MacroData {
  macro(market: INDIA_NSE) {
    gdpGrowthRate
    inflationRate
    interestRate
    currencyStrength
    fiiNetFlowCrores
    diiNetFlowCrores
    vixIndia
    sectorRotation
    macroScore
    overallOutlook
  }
}
```

### 6.9 Mutation: Force Fresh Signal

```graphql
mutation FreshSignal {
  generateFreshSignal(symbol: "INFY.NSE", market: INDIA_NSE) {
    signal
    compositeScore
    confidencePercent
    entryPrice
    targetPrice
    stopLossPrice
    keyReasons
  }
}
```

### 6.10 Subscription: Live Signals (WebSocket)

Connect to `ws://localhost:8080/graphql-ws` using the `graphql-ws` protocol.

```graphql
subscription LiveSignals {
  liveSignals(market: US_NASDAQ) {
    symbol
    signal
    compositeScore
    targetPrice
    stopLossPrice
    confidencePercent
    timeHorizon
  }
}
```

### 6.11 Subscription: Watchlist Signals

```graphql
subscription WatchlistSignals {
  watchlistSignals(input: {
    symbols: ["AAPL", "MSFT", "GOOGL", "AMZN"]
    market: US_NASDAQ
  }) {
    symbol
    signal
    entryPrice
    targetPrice
    expectedProfitPercent
  }
}
```

### 6.12 Subscription: Live Quote Tick

```graphql
subscription LiveQuote {
  liveQuote(symbol: "HDFCBANK.NSE", market: INDIA_NSE) {
    symbol
    currentPrice
    changePercent
    volume
    lastUpdated
  }
}
```

---

## 7. EXPECTED OUTPUTS

### 7.1 Stock Dashboard Response

```json
{
  "data": {
    "stockDashboard": {
      "symbol": "HDFCBANK.NSE",
      "market": "INDIA_NSE",
      "quote": {
        "currentPrice": 1672.50,
        "changePercent": 1.24,
        "volume": 8432100,
        "high52Week": 1794.00,
        "low52Week": 1363.55
      },
      "technical": {
        "rsi14": 42.3,
        "macdLine": 12.4,
        "macdSignal": 8.7,
        "bb20Upper": 1712.80,
        "bb20Lower": 1598.20,
        "technicalScore": 74.0
      },
      "sentiment": {
        "overallSentiment": "BULLISH",
        "sentimentScore": 0.68,
        "bullishPercent": 72.0,
        "bearishPercent": 18.0
      },
      "recommendation": {
        "signal": "STRONG_BUY",
        "compositeScore": 87.4,
        "confidencePercent": 82.0,
        "entryPrice": 1672.50,
        "targetPrice": 1980.00,
        "stopLossPrice": 1585.00,
        "riskRewardRatio": 3.54,
        "timeHorizon": "MEDIUM_TERM",
        "riskLevel": "LOW"
      }
    }
  }
}
```

### 7.2 Subscription Message (liveSignals)

```json
{
  "type": "next",
  "payload": {
    "data": {
      "liveSignals": {
        "symbol": "NVDA",
        "signal": "BUY",
        "compositeScore": 78.6,
        "targetPrice": 980.00,
        "stopLossPrice": 821.00,
        "confidencePercent": 74.0,
        "timeHorizon": "SHORT_TERM"
      }
    }
  }
}
```

### 7.3 Error Response (invalid symbol)

```json
{
  "errors": [
    {
      "message": "No quote found for symbol: INVALID.SYM",
      "locations": [{"line": 2, "column": 3}],
      "path": ["quote"],
      "extensions": {"classification": "NOT_FOUND"}
    }
  ],
  "data": {
    "quote": null
  }
}
```

### 7.4 Schema SDL (GET /graphql/schema)

```
type Query {
  stockDashboard(symbol: String!, market: Market!): StockDashboard
  quote(symbol: String!, market: Market!): StockQuote
  ...
}
type Subscription {
  liveSignals(market: Market!): TradeRecommendation
  liveQuote(symbol: String!, market: Market!): StockQuote
  ...
}
```

---

## 8. TESTING WITH CURL

### 8.1 Health Check

```bash
curl http://localhost:8080/actuator/health
```

### 8.2 Simple Quote Query

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ quote(symbol:\"AAPL\", market:US_NASDAQ){ symbol currentPrice changePercent } }"}'
```

### 8.3 Introspection (schema exploration)

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ __schema { types { name } } }"}'
```

### 8.4 WebSocket Subscription Test

```bash
# Install wscat: npm install -g wscat
wscat -c "ws://localhost:8080/graphql-ws" \
  --subprotocol "graphql-ws"

# After connected, send:
{"type":"connection_init"}
{"type":"subscribe","id":"1","payload":{"query":"subscription { liveSignals(market: US_NASDAQ) { symbol signal } }"}}
```

---

## 9. ENHANCEMENTS OVER PURE REST

### 9.1 What Stage 1 Solves

```
BEFORE (REST-only):
┌────────────────────────────────────────────────────────┐
│  Client needs a stock dashboard:                       │
│  → GET /market/quote/AAPL?market=US_NASDAQ             │
│  → GET /analysis/technical/AAPL?market=US_NASDAQ       │
│  → GET /analysis/sentiment/AAPL?market=US_NASDAQ       │
│  → GET /recommendations/stock/AAPL?market=US_NASDAQ    │
│                                                        │
│  4 round trips, N fields over-fetched per call         │
│  Client must aggregate and join data                   │
└────────────────────────────────────────────────────────┘

AFTER (GraphQL):
┌────────────────────────────────────────────────────────┐
│  Client needs a stock dashboard:                       │
│  → POST /graphql { stockDashboard(symbol, market) {   │
│      quote { price volume }                            │
│      technical { rsi macd technicalScore }             │
│      recommendation { signal target stopLoss }         │
│    }}                                                  │
│                                                        │
│  1 round trip, exactly the fields requested            │
│  Server does the aggregation                           │
└────────────────────────────────────────────────────────┘
```

### 9.2 Feature Comparison

| Feature | REST (before) | GraphQL (Stage 1) |
|---|---|---|
| Aggregated dashboard | 4 calls | 1 call |
| Field selection | Fixed response shape | Client selects fields |
| Real-time data | WebSocket (custom) | Native Subscriptions |
| Schema discovery | Swagger/OpenAPI | Introspection |
| Batch queries | Multiple endpoints | Single query, multi-root |
| Type safety | OpenAPI schema | SDL + scalar extensions |
| N+1 problem | Present | Resolver batching ready |

### 9.3 Security — JWT on GraphQL

All GraphQL endpoints are protected by the same JWT filter as REST:

```
Authorization: Bearer <jwt_token>

Roles enforced at resolver level:
  ROLE_FREE    → topRecommendations limited to top 3
  ROLE_PREMIUM → full recommendations + IPO + options
  ROLE_ADMIN   → all data + raw fields
```

---

## 10. CONFIGURATION REFERENCE

### application.yml (GraphQL section)

```yaml
spring:
  graphql:
    graphiql:
      enabled: true
      path: /graphiql         # Browser IDE
    websocket:
      path: /graphql-ws       # Subscription WebSocket endpoint
    schema:
      printer:
        enabled: true         # GET /graphql/schema exposes SDL
    path: /graphql            # Main query/mutation endpoint
```

### Scalar Registration (AppConfig.java)

```java
@Bean
public RuntimeWiringConfigurer runtimeWiringConfigurer() {
    return builder -> builder
        .scalar(ExtendedScalars.DateTime)
        .scalar(ExtendedScalars.Date)
        .scalar(ExtendedScalars.GraphQLBigDecimal)
        .scalar(ExtendedScalars.GraphQLLong);
}
```

---

## 11. KNOWN LIMITATIONS IN STAGE 1

| Limitation | Resolved In |
|---|---|
| Subscriptions push synthetic/mock data | Stage 3 (Kafka Streams) |
| No DataLoader batching (N+1 on nested resolvers) | Future |
| No persisted queries | Future |
| No query depth/complexity limits | Production hardening |
| Subscriptions are single-node only | Production: Redis pub/sub |

---

*Stage 1 Complete | Commit: cbdf4d2*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*
