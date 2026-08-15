package com.hft.graphql;

import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import com.hft.model.enums.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GraphQL Subscription resolver — bridges Kafka topics to reactive Flux streams.
 *
 * Clients subscribe via WebSocket:
 *   subscription { liveSignals(market: US_NASDAQ) { symbol signal targetPrice } }
 *
 * When a new TradeRecommendation arrives on the Kafka 'trading-signals' topic,
 * it is emitted to all active subscribers.
 */
@Slf4j
@Controller
public class SignalSubscriptionResolver {

    // Multicast sink: many subscribers, all get every signal
    private final Sinks.Many<TradeRecommendation> signalSink =
            Sinks.many().multicast().onBackpressureBuffer(1024);

    // Per-symbol quote sinks for liveQuote subscriptions
    private final Map<String, Sinks.Many<StockQuote>> quoteSinks =
            new ConcurrentHashMap<>();

    // ─── Subscriptions ────────────────────────────────────────────────────────

    @SubscriptionMapping
    public Flux<TradeRecommendation> liveSignals(@Argument Market market) {
        log.debug("[GraphQL] liveSignals subscription: market={}", market);
        Flux<TradeRecommendation> flux = signalSink.asFlux();
        if (market != null) {
            flux = flux.filter(r -> r.getMarket() == market);
        }
        return flux;
    }

    @SubscriptionMapping
    public Flux<TradeRecommendation> watchlistSignals(
            @Argument MarketDataResolver.WatchlistInput input) {
        log.debug("[GraphQL] watchlistSignals: {} symbols", input.symbols().size());
        return signalSink.asFlux()
                .filter(r -> r.getMarket() == input.market())
                .filter(r -> input.symbols().contains(r.getSymbol()));
    }

    @SubscriptionMapping
    public Flux<StockQuote> liveQuote(@Argument String symbol, @Argument Market market) {
        log.debug("[GraphQL] liveQuote subscription: {} on {}", symbol, market);
        String key = symbol + "_" + market.name();
        Sinks.Many<StockQuote> sink = quoteSinks.computeIfAbsent(key,
                k -> Sinks.many().multicast().onBackpressureBuffer(64));
        return sink.asFlux();
    }

    // ─── Kafka listeners (feed the sinks) ─────────────────────────────────────

    @KafkaListener(
            topics = "#{T(com.hft.config.KafkaConfig).TOPIC_SIGNALS}",
            groupId = "graphql-signal-subscriber",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSignal(TradeRecommendation recommendation) {
        log.debug("[GraphQL] Kafka signal received: {} → {}",
                recommendation.getSymbol(), recommendation.getSignal());
        Sinks.EmitResult result = signalSink.tryEmitNext(recommendation);
        if (result.isFailure()) {
            log.warn("[GraphQL] Signal sink emit failed: {}", result);
        }
    }

    @KafkaListener(
            topics = "#{T(com.hft.config.KafkaConfig).TOPIC_MARKET_DATA_RAW}",
            groupId = "graphql-quote-subscriber",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onQuote(StockQuote quote) {
        String key = quote.getSymbol() + "_" + quote.getMarket().name();
        Sinks.Many<StockQuote> sink = quoteSinks.get(key);
        if (sink != null) {
            sink.tryEmitNext(quote);
        }
    }
}
