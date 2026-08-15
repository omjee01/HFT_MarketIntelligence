package com.hft.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.config.KafkaConfig;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Kafka Streams topology for real-time market data processing.
 *
 * Three logical pipelines:
 *
 *  1. Quote KTable  — market-data-raw → groupByKey → reduce(latest) → quotes-aggregated
 *                     Side-effect: push each tick to sink bridge
 *
 *  2. Candle Builder — market-data-raw → groupByKey → windowedBy(1 min) → aggregate(OHLCV)
 *                      → candles-1m
 *                      Side-effect: push completed candles to sink bridge
 *
 *  3. Signal Enricher — trading-signals → selectKey(symbol) → leftJoin(quoteKTable)
 *                        → signals-enriched
 *                        Side-effect: push enriched signals to sink bridge
 *
 * Stage 4: when RedisPubSubBridge is active (multi-node), events are published to Redis
 * rather than written to the local StreamSinkBridge directly. The bridge's Redis subscriber
 * propagates them to ALL nodes (including this one) via sinkBridge.emitXxx().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStreamsTopology {

    private final StreamsBuilder   builder;
    private final StreamSinkBridge sinkBridge;
    private final ObjectMapper     mapper;

    /** Null when hft.redis-pubsub.enabled=false (dev/single-node). */
    @Autowired(required = false)
    private RedisPubSubBridge redisBridge;

    @PostConstruct
    public void buildTopology() {
        Serde<StockQuote>          quoteSerde  = jsonSerde(StockQuote.class);
        Serde<TradeRecommendation> signalSerde = jsonSerde(TradeRecommendation.class);
        Serde<OHLCVData>           candleSerde = jsonSerde(OHLCVData.class);

        // ─── 1. Raw quote stream (JSON strings from market-data-raw) ─────────────
        KStream<String, String> rawQuotes = builder.stream(
                KafkaConfig.TOPIC_MARKET_DATA_RAW,
                Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, StockQuote> quoteStream = rawQuotes
                .mapValues(json -> fromJson(json, StockQuote.class))
                .filter((k, v) -> v != null && v.getSymbol() != null)
                .selectKey((k, v) -> v.getSymbol())
                .peek((symbol, quote) -> emitQuote(quote));

        // ─── 2. KTable — latest quote per symbol (used for signal join) ──────────
        KTable<String, StockQuote> latestQuotes = quoteStream
                .groupByKey(Grouped.with(Serdes.String(), quoteSerde))
                .reduce(
                        (existing, update) -> update,   // always keep the newest tick
                        Materialized.<String, StockQuote, KeyValueStore<Bytes, byte[]>>as("quotes-latest-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(quoteSerde));

        // Publish latest quote per symbol to output topic
        quoteStream
                .mapValues(q -> toJson(q))
                .filter((k, v) -> v != null)
                .to(KafkaConfig.TOPIC_QUOTES_AGGREGATED, Produced.with(Serdes.String(), Serdes.String()));

        // ─── 3. 1-Minute OHLCV candle builder ───────────────────────────────────
        quoteStream
                .groupByKey(Grouped.with(Serdes.String(), quoteSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        () -> null,
                        (symbol, tick, candle) -> buildCandle(tick, candle),
                        Materialized.<String, OHLCVData, WindowStore<Bytes, byte[]>>as("candles-1m-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(candleSerde))
                .toStream()
                .peek((wk, candle) -> emitCandle(candle))
                .map((wk, candle) -> KeyValue.pair(wk.key(), toJson(candle)))
                .filter((k, v) -> v != null)
                .to(KafkaConfig.TOPIC_CANDLES_1M, Produced.with(Serdes.String(), Serdes.String()));

        // ─── 4. Signal enrichment — join signals with latest quotes ──────────────
        KStream<String, String> rawSignals = builder.stream(
                KafkaConfig.TOPIC_SIGNALS,
                Consumed.with(Serdes.String(), Serdes.String()));

        rawSignals
                .mapValues(json -> fromJson(json, TradeRecommendation.class))
                .filter((k, v) -> v != null && v.getSymbol() != null)
                .selectKey((k, v) -> v.getSymbol())
                .leftJoin(
                        latestQuotes,
                        this::enrichSignal,
                        Joined.with(Serdes.String(), signalSerde, quoteSerde))
                .peek((symbol, signal) -> emitSignal(signal))
                .mapValues(s -> toJson(s))
                .filter((k, v) -> v != null)
                .to(KafkaConfig.TOPIC_SIGNALS_ENRICHED, Produced.with(Serdes.String(), Serdes.String()));

        log.info("[KafkaStreams] Topology registered: QuoteKTable + CandleBuilder + SignalEnricher");
    }

    // ─── Emit helpers — routes via Redis when multi-node, local otherwise ─────

    private void emitQuote(StockQuote quote) {
        if (redisBridge != null) redisBridge.publishQuote(quote);
        else sinkBridge.emitQuote(quote);
    }

    private void emitSignal(TradeRecommendation signal) {
        if (redisBridge != null) redisBridge.publishSignal(signal);
        else sinkBridge.emitSignal(signal);
    }

    private void emitCandle(OHLCVData candle) {
        if (redisBridge != null) redisBridge.publishCandle(candle);
        else sinkBridge.emitCandle(candle);
    }

    // ─── OHLCV Candle Builder ─────────────────────────────────────────────────

    private OHLCVData buildCandle(StockQuote tick, OHLCVData existing) {
        BigDecimal price = tick.getCurrentPrice();
        if (price == null) return existing;

        if (existing == null) {
            return OHLCVData.builder()
                    .symbol(tick.getSymbol())
                    .market(tick.getMarket())
                    .intervalType("1m")
                    .open(price)
                    .high(price)
                    .low(price)
                    .close(price)
                    .volume(tick.getVolume() != null ? tick.getVolume() : 0L)
                    .fetchedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build();
        }

        BigDecimal newHigh = existing.getHigh() != null ? existing.getHigh().max(price) : price;
        BigDecimal newLow  = existing.getLow()  != null ? existing.getLow().min(price)  : price;
        long addedVol = tick.getVolume() != null ? tick.getVolume() : 0L;

        return OHLCVData.builder()
                .symbol(existing.getSymbol())
                .market(existing.getMarket())
                .intervalType("1m")
                .open(existing.getOpen())   // open is fixed at first tick
                .high(newHigh)
                .low(newLow)
                .close(price)              // close tracks latest tick
                .volume((existing.getVolume() != null ? existing.getVolume() : 0L) + addedVol)
                .fetchedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    // ─── Signal Enrichment ────────────────────────────────────────────────────

    private TradeRecommendation enrichSignal(TradeRecommendation signal, StockQuote quote) {
        if (signal == null) return null;
        if (quote == null || quote.getCurrentPrice() == null) return signal;
        // Update the live price on the recommendation with the latest quote
        signal.setCurrentPrice(quote.getCurrentPrice());
        return signal;
    }

    // ─── JSON Serde factory ───────────────────────────────────────────────────

    private <T> Serde<T> jsonSerde(Class<T> type) {
        Serializer<T> ser = (topic, data) -> {
            if (data == null) return null;
            try { return mapper.writeValueAsBytes(data); }
            catch (JsonProcessingException e) {
                log.warn("[KafkaStreams] Serialization failed for {}: {}", type.getSimpleName(), e.getMessage());
                return null;
            }
        };
        Deserializer<T> des = (topic, bytes) -> {
            if (bytes == null || bytes.length == 0) return null;
            try { return mapper.readValue(bytes, type); }
            catch (Exception e) {
                log.warn("[KafkaStreams] Deserialization failed for {}: {}", type.getSimpleName(), e.getMessage());
                return null;
            }
        };
        return Serdes.serdeFrom(ser, des);
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, type); }
        catch (Exception e) {
            log.warn("[KafkaStreams] JSON parse failed for {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return mapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) {
            log.warn("[KafkaStreams] JSON write failed: {}", e.getMessage());
            return null;
        }
    }
}
