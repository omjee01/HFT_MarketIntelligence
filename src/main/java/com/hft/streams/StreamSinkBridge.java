package com.hft.streams;

import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import com.hft.model.enums.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reactive bridge between Kafka Streams processors, GraphQL subscriptions,
 * and gRPC server-streaming endpoints.
 *
 * Kafka Streams topology nodes push into these sinks via emitSignal/emitQuote/emitCandle.
 * GraphQL resolvers and gRPC services subscribe via the Flux accessors.
 *
 * This replaces the in-class sinks that were previously embedded in SignalSubscriptionResolver.
 */
@Slf4j
@Component
public class StreamSinkBridge {

    // ─── Signal sink — all enriched trade recommendations ────────────────────
    private final Sinks.Many<TradeRecommendation> signalSink =
            Sinks.many().multicast().onBackpressureBuffer(1024);

    // ─── Quote sinks — per symbol+market (created on first subscribe) ─────────
    private final Map<String, Sinks.Many<StockQuote>> quoteSinks = new ConcurrentHashMap<>();

    // ─── Candle sink — 1-minute OHLCV bars ───────────────────────────────────
    private final Sinks.Many<OHLCVData> candleSink =
            Sinks.many().multicast().onBackpressureBuffer(4096);

    // ─── Emitters (called by Kafka Streams topology nodes) ───────────────────

    public void emitSignal(TradeRecommendation signal) {
        if (signal == null) return;
        Sinks.EmitResult result = signalSink.tryEmitNext(signal);
        if (result.isFailure()) {
            log.warn("[StreamSink] Signal emit failed for {}: {}", signal.getSymbol(), result);
        }
    }

    public void emitQuote(StockQuote quote) {
        if (quote == null || quote.getSymbol() == null || quote.getMarket() == null) return;
        String key = sinkKey(quote.getSymbol(), quote.getMarket());
        Sinks.Many<StockQuote> sink = quoteSinks.get(key);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(quote);
            if (result.isFailure()) {
                log.warn("[StreamSink] Quote emit failed for {}: {}", key, result);
            }
        }
    }

    public void emitCandle(OHLCVData candle) {
        if (candle == null) return;
        Sinks.EmitResult result = candleSink.tryEmitNext(candle);
        if (result.isFailure()) {
            log.warn("[StreamSink] Candle emit failed for {}: {}", candle.getSymbol(), result);
        }
    }

    // ─── Flux accessors (consumed by GraphQL resolvers and gRPC services) ────

    /** Filtered signal stream — null market returns all signals. */
    public Flux<TradeRecommendation> signalFlux(Market market) {
        Flux<TradeRecommendation> flux = signalSink.asFlux();
        return market != null ? flux.filter(r -> r.getMarket() == market) : flux;
    }

    /** Filtered signal stream for a watchlist. */
    public Flux<TradeRecommendation> signalFlux(java.util.List<String> symbols, Market market) {
        return signalSink.asFlux()
                .filter(r -> market == null || r.getMarket() == market)
                .filter(r -> symbols == null || symbols.isEmpty() || symbols.contains(r.getSymbol()));
    }

    /** Per-symbol quote stream (creates sink on first call). */
    public Flux<StockQuote> quoteFlux(String symbol, Market market) {
        String key = sinkKey(symbol, market);
        return quoteSinks.computeIfAbsent(key,
                k -> Sinks.many().multicast().onBackpressureBuffer(64)).asFlux();
    }

    /** All 1-minute OHLCV candles, optionally filtered by symbol. */
    public Flux<OHLCVData> candleFlux(String symbol) {
        Flux<OHLCVData> flux = candleSink.asFlux();
        return symbol != null ? flux.filter(c -> symbol.equals(c.getSymbol())) : flux;
    }

    private static String sinkKey(String symbol, Market market) {
        return symbol + "_" + (market != null ? market.name() : "ALL");
    }
}
