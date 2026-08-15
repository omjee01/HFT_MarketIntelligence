package com.hft.graphql;

import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import com.hft.model.enums.Market;
import com.hft.streams.StreamSinkBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

/**
 * GraphQL Subscription resolver — exposes Kafka Streams output as reactive WebSocket streams.
 *
 * Live data flow (Stage 3):
 *   market-data-raw → Kafka Streams QuoteKTable → StreamSinkBridge → liveQuote subscription
 *   trading-signals → Kafka Streams SignalEnricher → StreamSinkBridge → liveSignals subscription
 *
 * Clients connect via WebSocket (graphql-ws protocol):
 *   subscription { liveSignals(market: US_NASDAQ) { symbol signal targetPrice } }
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SignalSubscriptionResolver {

    private final StreamSinkBridge sinkBridge;

    // ─── Subscriptions ────────────────────────────────────────────────────────

    @SubscriptionMapping
    public Flux<TradeRecommendation> liveSignals(@Argument Market market) {
        log.debug("[GraphQL] liveSignals subscription: market={}", market);
        return sinkBridge.signalFlux(market);
    }

    @SubscriptionMapping
    public Flux<TradeRecommendation> watchlistSignals(
            @Argument MarketDataResolver.WatchlistInput input) {
        log.debug("[GraphQL] watchlistSignals: {} symbols on {}", input.symbols().size(), input.market());
        return sinkBridge.signalFlux(input.symbols(), input.market());
    }

    @SubscriptionMapping
    public Flux<StockQuote> liveQuote(@Argument String symbol, @Argument Market market) {
        log.debug("[GraphQL] liveQuote subscription: {} on {}", symbol, market);
        return sinkBridge.quoteFlux(symbol, market);
    }
}
