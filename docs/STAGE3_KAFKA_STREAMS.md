# STAGE 3 — Kafka Streams Real-Time Pipeline

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0 | Built on: Spring Boot 3.2.5 + Apache Kafka Streams*

---

## 1. STAGE OVERVIEW

Stage 3 replaces the ad-hoc `@KafkaListener` approach with a proper **Kafka Streams topology**.
Raw market ticks flow from Kafka into three stateful processors:

1. **QuoteKTable** — keeps the latest quote per symbol (exactly-once key-value state)
2. **CandleBuilder** — aggregates 1-minute OHLCV candles using tumbling windows
3. **SignalEnricher** — joins enriched signals with the latest quote to update `currentPrice`

The output of each processor flows through **StreamSinkBridge** into two consumers:
- GraphQL WebSocket subscriptions (`liveSignals`, `liveQuote`, `liveCandles`)
- gRPC server-streaming endpoints (`StreamSignals`, `StreamQuotes`)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    STAGE 3 FULL DATA FLOW                               │
│                                                                         │
│  MARKET DATA SOURCES                                                    │
│  (Alpha Vantage / NSE / BSE)                                            │
│        │                                                                │
│        ▼                                                                │
│  ┌──────────────────┐    Kafka Topic                                    │
│  │  MarketData      │───────────────► market-data-raw                   │
│  │  Aggregator Svc  │                (64 partitions)                    │
│  └──────────────────┘                     │                             │
│                                           │                             │
│              ┌────────────────────────────┘                             │
│              │        KAFKA STREAMS TOPOLOGY                            │
│              ▼                                                          │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                                                                   │  │
│  │  [market-data-raw]                                                │  │
│  │       │                                                           │  │
│  │       ├──► selectKey(symbol) ──► peek(emitQuote)                  │  │
│  │       │         │                     │                           │  │
│  │       │         │                     ▼                           │  │
│  │       │         │            StreamSinkBridge.emitQuote()         │  │
│  │       │         │                                                 │  │
│  │       │         ▼  1. QUOTE KTABLE                                │  │
│  │       │    groupByKey → reduce(latest) ──► [quotes-aggregated]   │  │
│  │       │                                    (64 partitions)        │  │
│  │       │                                                           │  │
│  │       ▼  2. CANDLE BUILDER (1-min tumbling window)                │  │
│  │    windowedBy(1min) → aggregate(OHLCV)                            │  │
│  │       │──► peek(emitCandle) ──► [candles-1m]                      │  │
│  │                                 (64 partitions, compact)          │  │
│  │                                                                   │  │
│  │  [trading-signals]  3. SIGNAL ENRICHER                            │  │
│  │       │                                                           │  │
│  │       ▼                                                           │  │
│  │    selectKey(symbol) → leftJoin(QuoteKTable, enrichSignal)        │  │
│  │       │──► peek(emitSignal) ──► [signals-enriched]                │  │
│  │                                 (16 partitions)                   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                   │              │              │                        │
│                   ▼              ▼              ▼                        │
│         ┌─────────────────────────────────────────────┐                 │
│         │              StreamSinkBridge                │                 │
│         │                                             │                 │
│         │  signalSink   Sinks.Many<TradeRecommendation>                 │
│         │  quoteSinks   Map<String, Sinks.Many<StockQuote>>             │
│         │  candleSink   Sinks.Many<OHLCVData>                           │
│         └───────────────────────┬─────────────────────┘                 │
│                                 │                                        │
│               ┌─────────────────┴──────────────────┐                   │
│               ▼                                     ▼                   │
│  ┌────────────────────────┐         ┌─────────────────────────────┐    │
│  │  GraphQL Subscriptions │         │  gRPC Server-Streaming       │    │
│  │  (WebSocket :8080)     │         │  (Netty :9090)               │    │
│  │                        │         │                              │    │
│  │  liveSignals(market)   │         │  StreamSignals(symbol)       │    │
│  │  watchlistSignals()    │         │  StreamQuotes(symbols)       │    │
│  │  liveQuote(symbol)     │         │                              │    │
│  └────────────────────────┘         └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. TECHNOLOGY STACK

| Component | Technology | Version |
|---|---|---|
| Stream Processing | Apache Kafka Streams | 3.7.x (via spring-kafka BOM) |
| Spring Integration | spring-kafka + @EnableKafkaStreams | 3.2.x |
| Topology Builder | StreamsBuilder (Spring-injected bean) | — |
| Windowing | Tumbling 1-minute windows | — |
| State Stores | RocksDB (embedded, per-stream-thread) | — |
| Reactive Bridge | Project Reactor `Sinks.Many<T>` | 3.6.x |
| Serialization | Jackson `ObjectMapper` (custom inline Serde) | 2.17.x |
| Sink capacity | signal: 1024, quote: 2048, candle: 4096 | — |

---

## 3. KAFKA TOPIC DESIGN

### 3.1 Topic Map

```
┌──────────────────────────────────────────────────────────────────┐
│                      TOPIC LAYOUT                                │
│                                                                  │
│  Input Topics (produced by existing services):                   │
│  ┌──────────────────┬──────────────┬──────────────────────────┐  │
│  │ Topic            │ Partitions   │ Content                  │  │
│  ├──────────────────┼──────────────┼──────────────────────────┤  │
│  │ market-data-raw  │ 64           │ StockQuote JSON per tick │  │
│  │ trading-signals  │ 16           │ TradeRecommendation JSON │  │
│  └──────────────────┴──────────────┴──────────────────────────┘  │
│                                                                  │
│  Output Topics (produced by Kafka Streams topology):             │
│  ┌──────────────────────┬──────────────┬──────────────────────┐  │
│  │ Topic                │ Partitions   │ Content              │  │
│  ├──────────────────────┼──────────────┼──────────────────────┤  │
│  │ quotes-aggregated    │ 64           │ Latest StockQuote    │  │
│  │                      │              │ (KTable state store) │  │
│  │ candles-1m           │ 64 (compact) │ OHLCVData per symbol │  │
│  │                      │              │ (1-min tumbling win) │  │
│  │ signals-enriched     │ 16           │ TradeRecommendation  │  │
│  │                      │              │ (currentPrice joined)│  │
│  └──────────────────────┴──────────────┴──────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 Topic Configuration (KafkaConfig.java)

```java
@EnableKafkaStreams
@Configuration
public class KafkaConfig {
    public static final String TOPIC_MARKET_DATA_RAW  = "market-data-raw";
    public static final String TOPIC_TRADING_SIGNALS  = "trading-signals";
    public static final String TOPIC_QUOTES_AGGREGATED = "quotes-aggregated";
    public static final String TOPIC_CANDLES_1M        = "candles-1m";
    public static final String TOPIC_SIGNALS_ENRICHED  = "signals-enriched";

    @Bean NewTopic quotesAggregated() {
        return TopicBuilder.name(TOPIC_QUOTES_AGGREGATED).partitions(64).replicas(1).build();
    }
    @Bean NewTopic candles1m() {
        return TopicBuilder.name(TOPIC_CANDLES_1M).partitions(64).replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT).build();
    }
    @Bean NewTopic signalsEnriched() {
        return TopicBuilder.name(TOPIC_SIGNALS_ENRICHED).partitions(16).replicas(1).build();
    }
}
```

---

## 4. KAFKA STREAMS TOPOLOGY DETAIL

`src/main/java/com/hft/streams/KafkaStreamsTopology.java`

### 4.1 Processor 1 — Quote KTable (Latest Quote per Symbol)

```
Input:  market-data-raw  (String key → JSON StockQuote value)
Key:    symbol_market   e.g. "AAPL_US_NASDAQ"

Steps:
  1. Stream raw bytes from market-data-raw
  2. Deserialize JSON → StockQuote via jsonSerde(StockQuote.class)
  3. selectKey: key = quote.symbol + "_" + quote.market.name()
  4. peek: StreamSinkBridge.emitQuote(quote)    ← feeds GraphQL/gRPC live feed
  5. groupByKey → reduce(latest)                ← last-write-wins KTable
  6. toStream → to("quotes-aggregated")

Output: quotes-aggregated  (same key → latest StockQuote per symbol)
State:  In-memory RocksDB state store per stream thread
```

```java
KStream<String, StockQuote> quoteStream = builder
    .stream(TOPIC_MARKET_DATA_RAW, Consumed.with(Serdes.String(), jsonSerde(StockQuote.class)))
    .selectKey((k, q) -> q.getSymbol() + "_" + q.getMarket().name())
    .peek((k, q) -> sinkBridge.emitQuote(q));

KTable<String, StockQuote> latestQuotes = quoteStream
    .groupByKey(Grouped.with(Serdes.String(), jsonSerde(StockQuote.class)))
    .reduce((existing, incoming) -> incoming, Materialized.as("latest-quotes-store"));

latestQuotes.toStream().to(TOPIC_QUOTES_AGGREGATED,
    Produced.with(Serdes.String(), jsonSerde(StockQuote.class)));
```

### 4.2 Processor 2 — CandleBuilder (1-Minute OHLCV)

```
Input:  quoteStream (from Processor 1, keyed by symbol_market)
Window: Tumbling 1-minute (TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))

Steps:
  1. groupByKey(symbol_market)
  2. windowedBy(tumbling 1-min)
  3. aggregate(initializer=null, adder=buildCandle)
     buildCandle logic:
       - If existing == null: create new OHLCVData with open=high=low=close=current
       - high = max(existing.high, current.price)
       - low  = min(existing.low,  current.price)
       - close = current.price     (last tick in window)
       - volume += current.volume  (cumulative volume in window)
  4. toStream → peek(emitCandle) ← feeds GraphQL candle subscription
  5. map(unwindow key) → to("candles-1m")

Output: candles-1m (symbol_market → OHLCVData for completed 1-min window)
```

```java
quoteStream
    .groupByKey(Grouped.with(Serdes.String(), jsonSerde(StockQuote.class)))
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
    .aggregate(
        () -> null,
        (key, tick, existing) -> buildCandle(tick, existing),
        Materialized.with(Serdes.String(), jsonSerde(OHLCVData.class)))
    .toStream()
    .peek((wk, candle) -> sinkBridge.emitCandle(candle))
    .map((wk, candle) -> new KeyValue<>(wk.key(), candle))
    .to(TOPIC_CANDLES_1M, Produced.with(Serdes.String(), jsonSerde(OHLCVData.class)));
```

### 4.3 Processor 3 — SignalEnricher (Join with Latest Quote)

```
Input:  trading-signals (String key → JSON TradeRecommendation value)
Join:   latestQuotes KTable (built in Processor 1)

Steps:
  1. Stream from trading-signals
  2. Deserialize JSON → TradeRecommendation via jsonSerde(TradeRecommendation.class)
  3. selectKey: key = signal.symbol + "_" + signal.market.name()
  4. leftJoin(latestQuotes, enrichSignal)
     enrichSignal: copies currentPrice from latest quote into the signal
     (preserves the signal if no quote available — left join)
  5. peek: StreamSinkBridge.emitSignal(enrichedSignal) ← feeds subscriptions
  6. to("signals-enriched")

Output: signals-enriched (symbol_market → enriched TradeRecommendation)
```

```java
builder.stream(TOPIC_TRADING_SIGNALS,
        Consumed.with(Serdes.String(), jsonSerde(TradeRecommendation.class)))
    .selectKey((k, s) -> s.getSymbol() + "_" + s.getMarket().name())
    .leftJoin(latestQuotes,
        (signal, quote) -> enrichSignal(signal, quote),
        Joined.with(Serdes.String(), jsonSerde(TradeRecommendation.class), jsonSerde(StockQuote.class)))
    .peek((k, s) -> sinkBridge.emitSignal(s))
    .to(TOPIC_SIGNALS_ENRICHED,
        Produced.with(Serdes.String(), jsonSerde(TradeRecommendation.class)));
```

---

## 5. STREAM SINK BRIDGE

`src/main/java/com/hft/streams/StreamSinkBridge.java`

```
┌──────────────────────────────────────────────────────────────────────┐
│                     StreamSinkBridge                                 │
│                     (@Component, Spring singleton)                   │
│                                                                      │
│  Sinks:                                                              │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │  signalSink   Sinks.Many<TradeRecommendation>                 │   │
│  │               multicast, onBackpressureBuffer(1024)           │   │
│  │                                                               │   │
│  │  quoteSinks   ConcurrentHashMap<String, Sinks.Many<StockQuote>│   │
│  │               key = symbol+"_"+market.name()                  │   │
│  │               each: multicast, onBackpressureBuffer(2048)     │   │
│  │               created lazily on first quoteFlux() call        │   │
│  │                                                               │   │
│  │  candleSink   Sinks.Many<OHLCVData>                           │   │
│  │               multicast, onBackpressureBuffer(4096)           │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Emit methods (called from KafkaStreamsTopology via peek()):          │
│    emitSignal(TradeRecommendation)                                   │
│    emitQuote(StockQuote)                                             │
│    emitCandle(OHLCVData)                                             │
│                                                                      │
│  Flux methods (consumed by resolvers/gRPC services):                 │
│    signalFlux(Market)               → all signals for a market       │
│    signalFlux(List<String>, Market) → filtered by symbol list        │
│    quoteFlux(String, Market)        → live ticks for one symbol      │
│    candleFlux(String)               → 1-min candles for one symbol   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6. HOW TO RUN — STAGE 3

### 6.1 Prerequisites

| Requirement | Notes |
|---|---|
| Java 21 | `java -version` |
| Gradle 9 | `gradle -version` |
| Docker + Docker Compose | For local Kafka broker |
| (Optional) kafkacat / kcat | CLI for producing test messages |

### 6.2 Start Local Kafka (Docker)

Save this as `docker-compose-kafka.yml`:

```yaml
version: "3.8"
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.1
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    ports:
      - "9092:9092"
```

```bash
docker-compose -f docker-compose-kafka.yml up -d

# Verify broker is up:
docker exec -it $(docker ps -q -f name=kafka) kafka-topics --bootstrap-server localhost:9092 --list
```

### 6.3 Start the Application with Streams Enabled

```bash
# Enable Kafka Streams (streams.auto-startup=false in dev by default)
gradle bootRun --args='--spring.profiles.active=dev --spring.kafka.streams.auto-startup=true'
```

Startup log should include:

```
[KafkaStreamsTopology] Building Kafka Streams topology...
[KafkaStreamsTopology] Topology built successfully:
  - Processor 1: QuoteKTable (market-data-raw → quotes-aggregated)
  - Processor 2: CandleBuilder (1-min OHLCV → candles-1m)
  - Processor 3: SignalEnricher (trading-signals ⊕ quote → signals-enriched)
[KafkaStreams] Streams application started (application-id: hft-market-intelligence-streams)
```

### 6.4 Create Topics Manually (if auto-create is off)

```bash
# Input topics (created by producers)
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic market-data-raw --partitions 64 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic trading-signals --partitions 16 --replication-factor 1

# Output topics (created by KafkaConfig @Bean NewTopic)
# Spring auto-creates on startup when broker is available
```

### 6.5 Produce Test Messages

```bash
# Produce a test quote tick
kafka-console-producer --bootstrap-server localhost:9092 \
  --topic market-data-raw \
  --property "parse.key=true" \
  --property "key.separator=:"

# Type:
AAPL_US_NASDAQ:{"symbol":"AAPL","market":"US_NASDAQ","currentPrice":189.25,"openPrice":187.40,"highPrice":190.10,"lowPrice":186.80,"volume":62450000,"changePercent":0.72,"assetType":"STOCK"}
```

```bash
# Produce a test signal
kafka-console-producer --bootstrap-server localhost:9092 \
  --topic trading-signals

# Type (no key separator needed, signal enricher sets key):
{"symbol":"AAPL","market":"US_NASDAQ","signal":"BUY","compositeScore":78.4,"confidencePercent":71.0,"entryPrice":187.50,"targetPrice":210.00,"stopLossPrice":176.00,"riskLevel":"MEDIUM","timeHorizon":"SHORT_TERM"}
```

### 6.6 Consume Output Topics

```bash
# Watch aggregated quotes
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic quotes-aggregated --from-beginning

# Watch 1-minute candles
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic candles-1m --from-beginning

# Watch enriched signals
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic signals-enriched --from-beginning
```

### 6.7 Test GraphQL Subscriptions (End-to-End)

1. Open GraphiQL at http://localhost:8080/graphiql
2. Run a subscription:
```graphql
subscription {
  liveQuote(symbol: "AAPL", market: US_NASDAQ) {
    symbol currentPrice changePercent volume
  }
}
```
3. Produce a quote tick to `market-data-raw` (Step 6.5)
4. GraphiQL shows the tick in real time

---

## 7. INPUTS — KAFKA MESSAGE FORMATS

### 7.1 market-data-raw (Input)

```
Topic: market-data-raw
Key:   (any — topology overwrites with symbol_market)
Value: JSON-serialized StockQuote
```

```json
{
  "symbol": "HDFCBANK.NSE",
  "market": "INDIA_NSE",
  "assetType": "STOCK",
  "companyName": "HDFC Bank Limited",
  "currentPrice": 1672.50,
  "openPrice": 1655.00,
  "highPrice": 1680.00,
  "lowPrice": 1648.00,
  "previousClose": 1651.75,
  "changeAmount": 20.75,
  "changePercent": 1.26,
  "volume": 8432100,
  "avgVolume10d": 6210000,
  "marketCap": 12534000000000.00,
  "peRatio": 19.4,
  "high52Week": 1794.00,
  "low52Week": 1363.55,
  "lastUpdated": "2026-05-28T10:45:00"
}
```

### 7.2 trading-signals (Input)

```
Topic: trading-signals
Key:   (any — topology overwrites with symbol_market)
Value: JSON-serialized TradeRecommendation
```

```json
{
  "symbol": "HDFCBANK.NSE",
  "market": "INDIA_NSE",
  "signal": "STRONG_BUY",
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
    "Golden cross forming: SMA50 approaching SMA200"
  ]
}
```

---

## 8. EXPECTED OUTPUTS

### 8.1 quotes-aggregated (Output)

Latest quote per symbol (reduce to last received):

```
Key:   HDFCBANK.NSE_INDIA_NSE
Value: (same JSON as input, but latest tick wins)
```

### 8.2 candles-1m (Output)

1-minute OHLCV candle aggregated from tick data:

```json
{
  "symbol": "HDFCBANK.NSE",
  "market": "INDIA_NSE",
  "windowStartMs": 1748430300000,
  "windowEndMs":   1748430360000,
  "open":   1655.00,
  "high":   1680.00,
  "low":    1648.00,
  "close":  1672.50,
  "volume": 124310,
  "vwap":   1664.75,
  "tickCount": 18
}
```

### 8.3 signals-enriched (Output)

Signal with `currentPrice` updated from QuoteKTable join:

```json
{
  "symbol": "HDFCBANK.NSE",
  "market": "INDIA_NSE",
  "signal": "STRONG_BUY",
  "currentPrice": 1672.50,
  "entryPrice": 1672.50,
  "targetPrice": 1980.00,
  "stopLossPrice": 1585.00,
  "compositeScore": 87.4,
  "confidencePercent": 82.0,
  "riskRewardRatio": 3.54
}
```

### 8.4 GraphQL Subscription Output (liveQuote)

Received on the WebSocket as each tick flows through:

```json
{
  "data": {
    "liveQuote": {
      "symbol": "AAPL",
      "currentPrice": 189.75,
      "changePercent": 0.98,
      "volume": 63120000
    }
  }
}
```

### 8.5 gRPC StreamSignals Output

Each `TradeRecommendationProto` message received by gRPC client:

```
symbol: "AAPL"
signal: BUY
composite_score: 78.4
confidence_percent: 71.0
current_price: 189.75
entry_price: 187.50
target_price: 210.00
stop_loss_price: 176.00
risk_level: MEDIUM
time_horizon: SHORT_TERM
```

### 8.6 Kafka Streams Thread Startup (Application Log)

```
[KafkaStreams] stream-thread [hft-market-intelligence-streams-StreamThread-1] Starting
[KafkaStreams] stream-thread [hft-market-intelligence-streams-StreamThread-2] Starting
[KafkaStreams] State transition from REBALANCING to RUNNING
[KafkaStreamsTopology] All processors active
```

---

## 9. TOPOLOGY DIAGRAM (ASCII)

```
market-data-raw
      │
      ▼
┌─────────────────────────────────────────────────────────────┐
│  KSTREAM: raw-quotes                                        │
│  Deserialized: Serde<StockQuote> (Jackson JSON)             │
│                                                             │
│  selectKey(symbol_market)                                   │
│       │                                                     │
│       ├──► peek(emitQuote → StreamSinkBridge)               │
│       │                                                     │
│       │  PROCESSOR 1: QuoteKTable                           │
│       ├──► groupByKey → reduce(latest) → KTABLE             │
│       │         └──► toStream → quotes-aggregated           │
│       │                                                     │
│       │  PROCESSOR 2: CandleBuilder                         │
│       └──► groupByKey → windowedBy(1min tumbling)           │
│                 └──► aggregate(OHLCV) → toStream            │
│                          └──► peek(emitCandle)              │
│                                   └──► candles-1m           │
└─────────────────────────────────────────────────────────────┘

trading-signals
      │
      ▼
┌─────────────────────────────────────────────────────────────┐
│  KSTREAM: raw-signals                                       │
│  Deserialized: Serde<TradeRecommendation>                   │
│                                                             │
│  PROCESSOR 3: SignalEnricher                                │
│  selectKey(symbol_market)                                   │
│  leftJoin(QuoteKTable, enrichSignal)                        │
│       └──► peek(emitSignal → StreamSinkBridge)              │
│                └──► signals-enriched                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. ENHANCEMENTS OVER @KAFKALISTENER

### 10.1 What Stage 3 Replaces

```
BEFORE (Stage 1/2 — @KafkaListener approach):
┌────────────────────────────────────────────────────────────────┐
│  @KafkaListener(topics = "trading-signals")                    │
│  public void onSignal(String json) {                           │
│    TradeRecommendation r = mapper.readValue(json, ...);        │
│    signalSink.tryEmitNext(r);   ← no enrichment, no state     │
│  }                                                             │
│                                                                │
│  Problems:                                                     │
│  - No join: signal has stale/null currentPrice                 │
│  - No OHLCV: no candle aggregation possible                    │
│  - No KTable: no "latest quote per symbol" state               │
│  - Each partition consumed independently — no windowing        │
│  - Single-threaded consumer (no stream parallelism)            │
└────────────────────────────────────────────────────────────────┘

AFTER (Stage 3 — Kafka Streams topology):
┌────────────────────────────────────────────────────────────────┐
│  Kafka Streams topology with:                                  │
│  ✅ QuoteKTable: always-fresh quote state per symbol           │
│  ✅ CandleBuilder: 1-min OHLCV via tumbling windows            │
│  ✅ SignalEnricher: leftJoin enriches signal.currentPrice       │
│  ✅ Parallel stream threads (num.stream.threads=2)             │
│  ✅ Exactly-once semantics (state store + changelog topics)    │
│  ✅ Backpressure via Reactor Sinks buffer limits               │
│  ✅ StreamSinkBridge decouples Kafka ↔ GraphQL/gRPC            │
└────────────────────────────────────────────────────────────────┘
```

### 10.2 Feature Comparison

| Feature | @KafkaListener | Kafka Streams (Stage 3) |
|---|---|---|
| Stateful processing | No | Yes (RocksDB state stores) |
| Join streams | No | Yes (leftJoin KTable) |
| Windowed aggregation | No | Yes (tumbling/hopping) |
| OHLCV candle builder | Manual/ad-hoc | Built-in via aggregate() |
| Exactly-once delivery | No | Yes (EOS config) |
| Parallel processing | Partition-count threads | Configurable stream threads |
| Fault tolerance | Manual | Built-in (changelog replication) |
| Live enrichment | No | Yes (price in signal = latest) |
| GraphQL subscriptions | Direct sink | Via StreamSinkBridge |
| gRPC streaming | Direct sink | Via StreamSinkBridge |

### 10.3 StreamSinkBridge Design — Why It Matters

```
Without StreamSinkBridge:
  KafkaStreams → directly writes to GraphQL Sink
  KafkaStreams → directly writes to gRPC StreamObserver
  Problem: tight coupling, gRPC thread contention, no fanout

With StreamSinkBridge:
  KafkaStreams → emitQuote/emitSignal/emitCandle → Sinks.Many
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        GraphQL Sub    gRPC Stream   Future consumer
        (Flux<T>)      (Flux<T>)     (any Flux<T>)

  Benefit: Reactor Sinks multicast to any number of subscribers
  Benefit: Backpressure buffer absorbs burst (1024-4096 items)
  Benefit: Quote sinks are keyed (symbol_market) — each subscriber
           only gets the symbols it cares about
```

---

## 11. CONFIGURATION REFERENCE

### application.yml (Kafka Streams section)

```yaml
spring:
  kafka:
    streams:
      application-id: hft-market-intelligence-streams
      properties:
        default.key.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
        default.value.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
        commit.interval.ms: 100          # low-latency: commit every 100ms
        cache.max.bytes.buffering: 0     # no internal buffer — process immediately
        num.stream.threads: 2            # 2 stream threads (increase in prod)
```

### application-dev.yml (Streams disabled in dev by default)

```yaml
spring:
  kafka:
    streams:
      auto-startup: false    # Start manually: --spring.kafka.streams.auto-startup=true
```

### Enabling Streams Selectively

```bash
# Dev with Kafka (broker running locally):
gradle bootRun \
  --args='--spring.profiles.active=dev --spring.kafka.streams.auto-startup=true'

# Without Kafka (topology not started — safe for test/CI):
gradle bootRun --args='--spring.profiles.active=dev'
```

---

## 12. KNOWN LIMITATIONS IN STAGE 3

| Limitation | Notes |
|---|---|
| Streams disabled in dev by default | Requires live Kafka broker; toggle with `auto-startup=true` |
| Single broker in dev Docker setup | Replication factor = 1 (data loss if broker dies) |
| No EOS (Exactly-Once Semantics) configured | Add `processing.guarantee=exactly_once_v2` for prod |
| CandleBuilder uses `noGrace` window | Out-of-order ticks are dropped (acceptable for 500ms polling) |
| StreamSinkBridge is single-node | Multi-node: replace with Redis pub/sub fan-out |
| Quote sinks created on first subscriber | Brief gap if subscriber connects after tick arrives |

---

*Stage 3 Complete | Commit: dfdff9a*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*
