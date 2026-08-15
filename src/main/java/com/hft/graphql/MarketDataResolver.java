package com.hft.graphql;

import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;
import com.hft.service.data.MarketDataAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MarketDataResolver {

    private final MarketDataAggregatorService marketDataService;

    @QueryMapping
    public StockQuote quote(@Argument String symbol, @Argument Market market) {
        log.debug("[GraphQL] quote: {} on {}", symbol, market);
        return marketDataService.getQuote(symbol, market).orElse(null);
    }

    @QueryMapping
    public List<StockQuote> quotes(@Argument WatchlistInput input) {
        log.debug("[GraphQL] quotes: {} symbols on {}", input.symbols().size(), input.market());
        return marketDataService.getBatchQuotes(input.symbols(), input.market());
    }

    // ─── Input record ──────────────────────────────────────────────────────────
    public record WatchlistInput(List<String> symbols, Market market) {}
}
