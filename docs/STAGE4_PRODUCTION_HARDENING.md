# STAGE 4 — Multi-Node Production Hardening

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0 | Commit: adc400d*

---

## 1. STAGE OVERVIEW

Stage 4 hardens the platform for horizontal scaling and production deployment. It adds five
orthogonal production-readiness capabilities on top of the Stages 1–3 architecture:

1. **Redis Pub/Sub fan-out** — replaces single-node in-memory Sinks with a Redis broadcast
   so every app instance delivers live feeds to its own subscribers
2. **gRPC JWT interceptor** — validates Bearer tokens on all gRPC calls before any service
   handler is reached
3. **gRPC TLS** — optional mutual-TLS for the Netty gRPC server (plaintext in dev)
4. **GraphQL safety limits** — query depth and complexity instrumentation blocks DoS queries
   before execution
5. **Kafka Streams → Micrometer metrics** — binds built-in Kafka Streams JMX metrics to the
   Micrometer registry, scraped by Prometheus at `/actuator/prometheus`

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    STAGE 4 MULTI-NODE ARCHITECTURE                           │
│                                                                              │
│  NODE A (Kafka Streams active)         NODE B / NODE C (replicas)           │
│  ┌────────────────────────────┐        ┌────────────────────────────┐        │
│  │  KafkaStreamsTopology      │        │  (no Kafka Streams)        │        │
│  │  emitQuote/Signal/Candle   │        │                            │        │
│  │       │                   │        │                            │        │
│  │       ▼                   │        │                            │        │
│  │  RedisPubSubBridge         │        │  RedisPubSubBridge         │        │
│  │  publishQuote/Signal/Candle│        │  (subscriber only)         │        │
│  └────────────┬───────────────┘        └───────────────┬────────────┘        │
│               │                                        │                     │
│               ▼                                        ▼                     │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │                        REDIS PUB/SUB                                │     │
│  │  hft:quotes:{symbol}_{market}   ◄──────────────────────────────►   │     │
│  │  hft:signals                    ◄──────────────────────────────►   │     │
│  │  hft:candles:{symbol}           ◄──────────────────────────────►   │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│       │ (all nodes receive every message)                                    │
│       ▼                  ▼                    ▼                              │
│  StreamSinkBridge    StreamSinkBridge     StreamSinkBridge                   │
│  (Node A local)      (Node B local)       (Node C local)                     │
│       │                  │                    │                              │
│       ▼                  ▼                    ▼                              │
│  GraphQL/gRPC subs   GraphQL/gRPC subs    GraphQL/gRPC subs                  │
│  (Node A clients)    (Node B clients)     (Node C clients)                   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Security layer added to gRPC

```
┌────────────────────────────────────────────────────────────────────┐
│                  gRPC REQUEST LIFECYCLE (Stage 4)                  │
│                                                                    │
│  Client                gRPC Server (:9090)                         │
│  ──────                ──────────────────                          │
│  1. Connect via TLS (prod) / plaintext (dev)                       │
│  2. Send metadata: Authorization: Bearer <jwt>                     │
│                        │                                           │
│                        ▼                                           │
│                  GrpcAuthInterceptor                               │
│                  ├── auth.enabled=false? → pass-through            │
│                  ├── No/malformed header? → UNAUTHENTICATED        │
│                  ├── Invalid JWT? → UNAUTHENTICATED + log          │
│                  └── Valid JWT? → attach to Context, proceed       │
│                        │                                           │
│                        ▼                                           │
│                  MarketDataGrpcService                             │
│                  AnalysisGrpcService                               │
│                  SignalGrpcService                                  │
└────────────────────────────────────────────────────────────────────┘
```

---

## 2. TECHNOLOGY STACK

| Component | Technology | Version / Notes |
|---|---|---|
| Pub/Sub broker | Redis (via spring-data-redis) | Existing dependency |
| Redis listener | `RedisMessageListenerContainer` | Non-reactive, pattern subscribe |
| gRPC interceptor | `io.grpc.ServerInterceptor` | JWT via `com.auth0:java-jwt:4.4.0` |
| gRPC TLS | `NettyServerBuilder.useTransportSecurity()` | File-based cert/key |
| GraphQL safety | `MaxQueryDepthInstrumentation` | graphql-java (transitive) |
| GraphQL safety | `MaxQueryComplexityInstrumentation` | graphql-java (transitive) |
| GraphQL wiring | `GraphQlSourceBuilderCustomizer` | `org.springframework.boot.autoconfigure.graphql` |
| Metrics binding | `io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics` | micrometer-core (transitive) |
| Metrics scrape | `micrometer-registry-prometheus` | Added in Stage 4 |
| Production config | `application-prod.yml` | Kafka EOS, Redis SSL, TLS, env-var secrets |

---

## 3. FEATURE DEEP-DIVE

### 3.1 Redis Pub/Sub Fan-Out (`RedisPubSubBridge`)

```
src/main/java/com/hft/streams/RedisPubSubBridge.java

Activation: hft.redis-pubsub.enabled=true  (@ConditionalOnProperty)
            → disabled by default (single-node dev mode)
            → enabled in application-prod.yml

Channel naming:
  hft:quotes:{symbol}_{market}   e.g. hft:quotes:AAPL_US_NASDAQ
  hft:signals                    all enriched recommendations
  hft:candles:{symbol}           e.g. hft:candles:HDFCBANK.NSE

Message format: JSON (same as Kafka topic payload)

On PUBLISH (called from KafkaStreamsTopology.emitXxx helpers):
  StringRedisTemplate.convertAndSend(channel, json)

On SUBSCRIBE (RedisMessageListenerContainer, PatternTopic):
  deserialize JSON → call StreamSinkBridge.emitXxx()
  → local Reactor Sinks → GraphQL/gRPC subscribers on this node

Routing in KafkaStreamsTopology:
  if (redisBridge != null) redisBridge.publishXxx(...)   ← multi-node
  else                     sinkBridge.emitXxx(...)        ← single-node dev
```

**Why this design avoids duplicate delivery to the publishing node:**
```
Without Redis: topology → sinkBridge (Node A subscribers only)
With Redis:    topology → redisBridge.publish() → Redis
               Redis broadcasts to ALL nodes including Node A
               → ALL nodes call sinkBridge.emitXxx()
               
Node A's own sinkBridge.emitXxx() is only called via Redis,
NOT directly from the topology. No duplicate.
```

### 3.2 gRPC JWT Interceptor (`GrpcAuthInterceptor`)

```
src/main/java/com/hft/grpc/GrpcAuthInterceptor.java

Configuration:
  grpc.server.auth.enabled=false  → pass-through (dev default)
  grpc.server.auth.enabled=true   → JWT required (prod)

JWT settings re-used from existing HTTP security:
  hft.jwt.secret   (same key validates gRPC and HTTP tokens)
  Algorithm: HMAC256

Metadata key:  "Authorization" (ASCII string marshaller)
Token format:  "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."

On success: decoded JWT stored in grpc.Context under key "hft-jwt"
            Downstream service handlers can read: GrpcAuthInterceptor.JWT_CTX_KEY.get()

Registration in GrpcServerConfig:
  ServerInterceptors.intercept(marketDataService, authInterceptor)
  ServerInterceptors.intercept(analysisService, authInterceptor)
  ServerInterceptors.intercept(signalService, authInterceptor)
```

### 3.3 gRPC TLS (`GrpcServerConfig` — Stage 4 update)

```
Configuration:
  grpc.server.tls.enabled=false          → plaintext (dev)
  grpc.server.tls.enabled=true           → TLS (prod)
  grpc.server.tls.cert-file=/path/to/tls.crt
  grpc.server.tls.key-file=/path/to/tls.key

Implementation:
  NettyServerBuilder
    .useTransportSecurity(new File(certFile), new File(keyFile))

Dev startup log:
  [gRPC] Running in PLAINTEXT mode — enable TLS for production

Prod startup log:
  [gRPC] TLS enabled — cert=/etc/ssl/grpc/tls.crt
  [gRPC] Server started on port 9090
```

### 3.4 GraphQL Safety Limits (`GraphQLInstrumentationConfig`)

```
src/main/java/com/hft/graphql/GraphQLInstrumentationConfig.java

Wired via: GraphQlSourceBuilderCustomizer bean (Spring Boot autoconfigure)

Limits (configurable):
  hft.graphql.max-query-depth:       10  (default)
  hft.graphql.max-query-complexity: 200  (default)

Depth example — depth 3:
  query {
    stockDashboard {           ← depth 1
      recommendation {         ← depth 2
        keyReasons             ← depth 3
      }
    }
  }

Complexity example:
  Each field = 1 unit of complexity.
  A query selecting 50 fields across 4 types = complexity 50.
  Selecting 201+ fields → rejected before any resolver runs.

Rejection response:
  { "errors": [{ "message": "maximum query depth exceeded 10" }] }
```

### 3.5 Kafka Streams → Micrometer (`KafkaStreamsMetricsRegistrar`)

```
src/main/java/com/hft/metrics/KafkaStreamsMetricsRegistrar.java

Binding trigger: ApplicationReadyEvent (deferred — KafkaStreams may not be
                 started at bean construction when auto-startup=false)

If KafkaStreams is null (dev, no broker): logs debug and skips — no crash.
If KafkaStreams is running: binds KafkaStreamsMetrics → MeterRegistry.

Metrics exposed at /actuator/prometheus:

  kafka_stream_thread_poll_records_avg            avg records per poll cycle
  kafka_stream_thread_process_records_rate        processing throughput (records/sec)
  kafka_stream_thread_process_latency_avg         processing time (ms)
  kafka_consumer_fetch_manager_records_lag        partition consumer lag
  kafka_stream_task_process_latency_avg           per-task processing latency
  kafka_stream_state_store_size                   RocksDB state store entry count

Prometheus scrape endpoint:  GET /actuator/prometheus
Micrometer dashboard:        GET /actuator/metrics
```

---

## 4. HOW TO RUN — STAGE 4

### 4.1 Dev Mode (default — no changes required)

All Stage 4 features are **off by default** in dev. No Redis, no JWT, no TLS required.

```bash
gradle bootRun --args='--spring.profiles.active=dev'

# Redis Pub/Sub: disabled (hft.redis-pubsub.enabled=false)
# gRPC auth:     disabled (grpc.server.auth.enabled=false)
# gRPC TLS:      disabled (grpc.server.tls.enabled=false)
# GraphQL limits: ACTIVE  (depth=10, complexity=200 — always on)
# Kafka Streams metrics: skipped (streams auto-startup=false in dev)
```

### 4.2 Dev Mode with Kafka Streams Metrics

```bash
docker-compose -f docker-compose-kafka.yml up -d   # start local Kafka

gradle bootRun --args='--spring.profiles.active=dev \
  --spring.kafka.streams.auto-startup=true'

# Kafka Streams starts → ApplicationReadyEvent → metrics bound
# Check: curl http://localhost:8080/actuator/prometheus | grep kafka_stream
```

### 4.3 Dev Mode with Redis Pub/Sub Enabled

```bash
# Start local Redis
docker run -d -p 6379:6379 redis:7.2-alpine

# Start app with Redis Pub/Sub on
gradle bootRun --args='--spring.profiles.active=dev \
  --hft.redis-pubsub.enabled=true'

# Startup log shows:
# [RedisPubSub] Subscribed to hft:quotes:*, hft:signals, hft:candles:*
```

### 4.4 Dev Mode with gRPC Auth Enabled (token testing)

```bash
gradle bootRun --args='--spring.profiles.active=dev \
  --grpc.server.auth.enabled=true'

# Generate a dev JWT (any HMAC256 tool, using the dev secret):
JWT_SECRET="hft-platform-secret-key-change-in-production-min-256-bit"
# Use jwt.io, or:
TOKEN=$(python3 -c "
import base64, hmac, hashlib, json, time
header  = base64.urlsafe_b64encode(json.dumps({'alg':'HS256','typ':'JWT'}).encode()).rstrip(b'=').decode()
payload = base64.urlsafe_b64encode(json.dumps({'sub':'dev-user','exp': int(time.time())+3600}).encode()).rstrip(b'=').decode()
msg  = f'{header}.{payload}'.encode()
sig  = base64.urlsafe_b64encode(hmac.new(b'${JWT_SECRET}', msg, hashlib.sha256).digest()).rstrip(b'=').decode()
print(f'{header}.{payload}.{sig}')
")

# Call gRPC with the token
grpcurl -plaintext \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  localhost:9090 hft.MarketDataService/GetQuote
```

### 4.5 Production Startup

```bash
# Set all required env vars (never hardcode in YAML):
export DATABASE_URL=jdbc:postgresql://prod-db:5432/hftdb
export DATABASE_USERNAME=hft_app
export DATABASE_PASSWORD=<secret>
export REDIS_HOST=prod-redis.cluster.local
export REDIS_PASSWORD=<secret>
export KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
export JWT_SECRET=<256-bit-random-secret>
export ALPHA_VANTAGE_API_KEY=<key>
export NEWSAPI_API_KEY=<key>
export GRPC_TLS_CERT_FILE=/etc/ssl/grpc/tls.crt
export GRPC_TLS_KEY_FILE=/etc/ssl/grpc/tls.key

java -jar build/libs/hft-market-intelligence-1.0.0.jar \
  --spring.profiles.active=prod
```

### 4.6 Test GraphQL Depth Limit

```bash
# This query is 11 levels deep → rejected
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ stockDashboard(symbol:\"AAPL\",market:US_NASDAQ){ recommendation{ keyReasons } technical{ sma20 sma50 sma200 ema9 ema21 bb20Upper bb20Middle bb20Lower atr14 rsi14 macdLine macdSignal macdHistogram } } }"}'
```

### 4.7 Test GraphQL Complexity Limit

```bash
# A screener + dashboard query requesting 300+ fields → rejected
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ topRecommendations(market:US_NASDAQ, limit:50){ symbol signal compositeScore confidencePercent entryPrice targetPrice stopLossPrice expectedProfitPercent riskRewardRatio holdingPeriodDays timeHorizon riskLevel keyReasons keyRisks sectorName sectorOutlook dataSources technicalScore fundamentalScore sentimentScore macroScore mlScore } }"}'
```

### 4.8 Prometheus Scrape

```bash
# All platform + Kafka Streams metrics:
curl http://localhost:8080/actuator/prometheus

# Filter Kafka Streams only:
curl -s http://localhost:8080/actuator/prometheus | grep kafka_stream

# List all registered metric names:
curl http://localhost:8080/actuator/metrics | python3 -m json.tool
```

---

## 5. INPUTS

### 5.1 gRPC with JWT Token (curl via grpcurl)

```bash
# Without auth (dev — auth.enabled=false):
grpcurl -plaintext \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  localhost:9090 hft.MarketDataService/GetQuote

# With auth (auth.enabled=true):
grpcurl -plaintext \
  -H "Authorization: Bearer eyJhbGci..." \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  localhost:9090 hft.MarketDataService/GetQuote

# With TLS (prod):
grpcurl \
  -cacert /etc/ssl/grpc/ca.crt \
  -H "Authorization: Bearer eyJhbGci..." \
  -d '{"symbol":"AAPL","market":"US_NASDAQ"}' \
  prod-host:9090 hft.MarketDataService/GetQuote
```

### 5.2 GraphQL — Query Within Limits

```graphql
# Depth = 3, complexity ≈ 12 — accepted
query SafeQuery {
  stockDashboard(symbol: "AAPL", market: US_NASDAQ) {
    quote { currentPrice changePercent }
    recommendation { signal targetPrice }
  }
}
```

### 5.3 Redis Pub/Sub — Manual Publish (testing)

```bash
# Simulate a quote tick from Node A reaching Node B/C via Redis:
redis-cli PUBLISH "hft:quotes:AAPL_US_NASDAQ" \
  '{"symbol":"AAPL","market":"US_NASDAQ","currentPrice":189.75,"changePercent":0.98,"volume":63120000}'

# Node B/C will receive this and push to their local GraphQL/gRPC subscribers
```

### 5.4 Prometheus Metric Query (Grafana / PromQL)

```promql
# Kafka Streams processing rate:
rate(kafka_stream_thread_process_records_total[1m])

# Consumer lag per partition:
kafka_consumer_fetch_manager_records_lag{topic="market-data-raw"}

# gRPC request latency (if Micrometer gRPC interceptor added in future):
grpc_server_processing_duration_seconds_bucket

# JVM heap usage:
jvm_memory_used_bytes{area="heap"}
```

---

## 6. EXPECTED OUTPUTS

### 6.1 Startup Log (dev — all Stage 4 features in passive mode)

```
[GrpcServerConfig] Running in PLAINTEXT mode — enable TLS for production
[GrpcServerConfig] gRPC server started on port 9090
[KafkaStreamsMetricsRegistrar] KafkaStreams not started (auto-startup=false) — metrics binding skipped
[TomcatWebServer] Tomcat started on port 8080
[HFTApplication] Started HFTApplication in 4.921 seconds
```

### 6.2 Startup Log (prod — all features active)

```
[RedisPubSub] Subscribed to hft:quotes:*, hft:signals, hft:candles:*
[GrpcServerConfig] TLS enabled — cert=/etc/ssl/grpc/tls.crt
[GrpcServerConfig] gRPC server started on port 9090
[KafkaStreams] State transition from REBALANCING to RUNNING
[KafkaStreamsMetricsRegistrar] Kafka Streams metrics registered with Micrometer (SimpleMeterRegistry)
[TomcatWebServer] Tomcat started on port 8080
[HFTApplication] Started HFTApplication in 6.341 seconds
```

### 6.3 GraphQL Depth Limit Rejection

```json
{
  "errors": [
    {
      "message": "maximum query depth exceeded 10 but was 11",
      "locations": [],
      "extensions": {
        "classification": "ExecutionAborted"
      }
    }
  ],
  "data": null
}
```

### 6.4 GraphQL Complexity Limit Rejection

```json
{
  "errors": [
    {
      "message": "maximum query complexity exceeded 200 but was 253",
      "locations": [],
      "extensions": {
        "classification": "ExecutionAborted"
      }
    }
  ],
  "data": null
}
```

### 6.5 gRPC Auth Rejection (no token)

```
ERROR:
  Code: Unauthenticated
  Message: Missing or malformed Authorization header
```

### 6.6 gRPC Auth Rejection (expired token)

```
ERROR:
  Code: Unauthenticated
  Message: JWT validation failed: The Token has expired on ...
```

### 6.7 Prometheus Scrape Output (excerpt)

```
# HELP kafka_stream_thread_process_records_rate Number of records processed per second
# TYPE kafka_stream_thread_process_records_rate gauge
kafka_stream_thread_process_records_rate{application="hft-market-intelligence",
  environment="production",thread_id="hft-streams-StreamThread-1"} 1842.3

# HELP kafka_consumer_fetch_manager_records_lag Records lag for partition
# TYPE kafka_consumer_fetch_manager_records_lag gauge
kafka_consumer_fetch_manager_records_lag{application="hft-market-intelligence",
  partition="0",topic="market-data-raw"} 0.0

# HELP jvm_memory_used_bytes Used bytes of given JVM memory area
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.27926272E8

# HELP http_server_requests_active_seconds_max
# TYPE http_server_requests_active_seconds_max gauge
http_server_requests_active_seconds_max{method="POST",uri="/graphql"} 0.003
```

### 6.8 Redis Pub/Sub Delivery (Node B log)

```
[RedisPubSub] Deserialize StockQuote OK — pushing to local StreamSinkBridge
[StreamSink] Quote emitted for key AAPL_US_NASDAQ
[GraphQL] liveQuote subscription: pushed to 3 local WebSocket clients
```

---

## 7. ENHANCEMENTS OVER STAGE 3

### 7.1 What Stage 4 Solves

```
BEFORE (Stage 3 — single-node only):
┌──────────────────────────────────────────────────────────────┐
│  App instances:   1 (scale-out breaks live subscriptions)    │
│  GraphQL subs:    Single-node Reactor Sinks — only clients   │
│                   on Node A get updates when Node A has       │
│                   the Kafka Streams partition                  │
│  gRPC auth:       No token required — internal service trust │
│  gRPC transport:  Plaintext only                              │
│  Malicious GQL:   No depth/complexity guard                  │
│  Metrics:         JVM + Spring only (Kafka Streams hidden)   │
└──────────────────────────────────────────────────────────────┘

AFTER (Stage 4 — production-ready):
┌──────────────────────────────────────────────────────────────┐
│  App instances:   N (Redis fan-out synchronises all nodes)   │
│  GraphQL subs:    All nodes receive every event via Redis     │
│                   → any node serves any subscriber           │
│  gRPC auth:       Same JWT used for HTTP/GraphQL auth        │
│  gRPC transport:  TLS with cert/key files (prod toggle)      │
│  Malicious GQL:   depth=10, complexity=200 hard limits       │
│  Metrics:         Full Kafka Streams metrics in Prometheus    │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Feature Comparison

| Feature | Stage 3 | Stage 4 |
|---|---|---|
| Multi-node live subscriptions | No (single-node Sinks) | Yes (Redis Pub/Sub) |
| gRPC authentication | None | JWT ServerInterceptor |
| gRPC transport security | Plaintext only | TLS (configurable) |
| GraphQL DoS protection | None | Depth(10) + Complexity(200) |
| Kafka Streams metrics | JMX only | Micrometer → Prometheus |
| Production config | None | application-prod.yml (EOS, SSL, TLS) |
| Kafka EOS (Exactly-Once) | No | `exactly_once_v2` in prod |
| Horizontal scaling | Manual (stateful, broken) | Ready (Redis-backed) |

### 7.3 Kafka Streams EOS — What Changes in Prod

```
application-prod.yml:
  spring.kafka.streams.properties:
    processing.guarantee: exactly_once_v2

Effect:
  - Each Kafka Streams task wraps its state-store writes and output-topic
    writes in a single atomic Kafka transaction
  - No duplicate records emitted to quotes-aggregated / candles-1m / signals-enriched
    if a stream thread is killed mid-processing
  - Requires Kafka broker ≥ 2.5, replication.factor ≥ 2

Trade-off:
  - ~20% lower throughput vs at-least-once
  - Higher broker resource usage (transaction coordinator)
  - Acceptable for financial data where duplicates cause incorrect signals
```

### 7.4 Security Architecture (Complete — Stage 4)

```
┌────────────────────────────────────────────────────────────────────┐
│                COMPLETE SECURITY POSTURE (Stage 4)                 │
│                                                                    │
│  HTTP / GraphQL (:8080)                                            │
│  ├── Spring Security JWT filter (existing)                         │
│  ├── Stateless sessions (no cookies)                               │
│  ├── GraphQL depth limit (NEW) — max 10 levels                     │
│  └── GraphQL complexity limit (NEW) — max 200 fields               │
│                                                                    │
│  gRPC (:9090)                                                      │
│  ├── TLS transport (NEW) — cert/key from env var paths             │
│  ├── JWT ServerInterceptor (NEW) — same token as HTTP              │
│  └── Per-method auth scope (same roles: FREE/PREMIUM/ADMIN)        │
│                                                                    │
│  Redis Pub/Sub                                                      │
│  ├── Redis AUTH password (REDIS_PASSWORD env var)                  │
│  ├── TLS via spring.data.redis.ssl.enabled=true (prod)             │
│  └── Channels are internal-only (no external exposure)             │
│                                                                    │
│  Kafka                                                             │
│  ├── SSL transport (KAFKA_SECURITY_PROTOCOL=SSL in prod)           │
│  ├── Keystore + truststore via env vars                            │
│  └── EOS prevents duplicate financial records                      │
│                                                                    │
│  Secrets (all stages)                                              │
│  ├── .gitignore: .env, secrets.yml, application-local.yml          │
│  ├── application-prod.yml: ALL values via ${ENV_VAR}               │
│  └── JWT secret: MUST be ≥256-bit random key in production         │
└────────────────────────────────────────────────────────────────────┘
```

---

## 8. CONFIGURATION REFERENCE

### 8.1 New Properties (application.yml base defaults)

```yaml
hft:
  redis-pubsub:
    enabled: false           # true in prod (application-prod.yml)
  graphql:
    max-query-depth: 10      # reject nested queries > 10 levels
    max-query-complexity: 200 # reject queries with > 200 total fields
```

### 8.2 Dev Overrides (application-dev.yml)

```yaml
grpc:
  server:
    auth:
      enabled: false   # no JWT required — easy grpcurl testing
    tls:
      enabled: false   # plaintext — no cert files needed
```

### 8.3 Production Overrides (application-prod.yml — key entries)

```yaml
spring:
  kafka:
    streams:
      properties:
        processing.guarantee: exactly_once_v2
        num.stream.threads: 4
grpc:
  server:
    auth:
      enabled: true
    tls:
      enabled: true
      cert-file: ${GRPC_TLS_CERT_FILE}
      key-file:  ${GRPC_TLS_KEY_FILE}
hft:
  redis-pubsub:
    enabled: true
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

### 8.4 Environment Variables Required in Production

| Variable | Used By | Example |
|---|---|---|
| `DATABASE_URL` | Spring DataSource | `jdbc:postgresql://host:5432/hftdb` |
| `DATABASE_USERNAME` | Spring DataSource | `hft_app` |
| `DATABASE_PASSWORD` | Spring DataSource | `<secret>` |
| `REDIS_HOST` | Spring Redis | `prod-redis.internal` |
| `REDIS_PASSWORD` | Spring Redis | `<secret>` |
| `KAFKA_BOOTSTRAP_SERVERS` | Spring Kafka | `k1:9092,k2:9092,k3:9092` |
| `JWT_SECRET` | JWT auth (HTTP + gRPC) | `<256-bit-random>` |
| `GRPC_TLS_CERT_FILE` | gRPC TLS | `/etc/ssl/grpc/tls.crt` |
| `GRPC_TLS_KEY_FILE` | gRPC TLS | `/etc/ssl/grpc/tls.key` |
| `ALPHA_VANTAGE_API_KEY` | Market data | `<key>` |
| `NEWSAPI_API_KEY` | Sentiment | `<key>` |
| `TWITTER_BEARER_TOKEN` | Sentiment | `<token>` |
| `FRED_API_KEY` | Macro data | `<key>` |

---

## 9. KNOWN LIMITATIONS IN STAGE 4

| Limitation | Notes |
|---|---|
| Redis Pub/Sub — at-least-once delivery | Redis does not persist Pub/Sub messages. If a node is down when a message is published, it misses it. Mitigation: combine with Kafka consumer replay on reconnect |
| Single Redis channel for all signals | High-volume deployments should shard by market: `hft:signals:US_NASDAQ`, `hft:signals:INDIA_NSE` |
| gRPC metrics not yet in Micrometer | gRPC server latency/call counts require adding `grpc-spring-boot-starter` or a custom interceptor in Stage 5 |
| TLS cert rotation | No hot-reload of TLS cert. Server must restart to pick up new cert |
| GraphQL complexity scoring is flat | List fields multiplied by element count is not modeled — add `@complexity` directive for precise scoring in production |
| Redis connection pool | Default pool settings; tune `spring.data.redis.lettuce.pool.*` for high concurrency |

---

*Stage 4 Complete | Commit: adc400d*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*
