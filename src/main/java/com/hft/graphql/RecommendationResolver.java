package com.hft.graphql;

import com.hft.model.domain.*;
import com.hft.model.enums.Market;
import com.hft.model.enums.RiskLevel;
import com.hft.model.enums.SignalType;
import com.hft.repository.TradeRecommendationRepository;
import com.hft.service.signal.RecommendationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RecommendationResolver {

    private final RecommendationEngine             engine;
    private final TradeRecommendationRepository    recoRepo;

    @QueryMapping
    public TradeRecommendation recommendation(@Argument String symbol,
                                              @Argument Market market) {
        log.debug("[GraphQL] recommendation: {} on {}", symbol, market);
        return engine.generateRecommendation(symbol, market).orElse(null);
    }

    @QueryMapping
    public List<TradeRecommendation> screenStocks(@Argument ScreenerInput input) {
        log.debug("[GraphQL] screenStocks: market={}, limit={}", input.market(), input.limit());
        int limit = input.limit() != null ? input.limit() : 10;
        List<TradeRecommendation> results = engine.generateTopRecommendations(input.market(), limit);

        // Apply optional filters from the input
        return results.stream()
                .filter(r -> input.minConfidence() == null
                        || r.getConfidencePercent() >= input.minConfidence())
                .filter(r -> input.signals() == null || input.signals().isEmpty()
                        || input.signals().contains(r.getSignal()))
                .filter(r -> input.maxRiskLevel() == null
                        || r.getRiskLevel().getLevel() <= input.maxRiskLevel().getLevel())
                .filter(r -> input.minTechnicalScore() == null
                        || r.getTechnicalScore() >= input.minTechnicalScore())
                .filter(r -> input.sectors() == null || input.sectors().isEmpty()
                        || input.sectors().contains(r.getSector()))
                .collect(Collectors.toList());
    }

    @QueryMapping
    public List<TradeRecommendation> activeRecommendations(@Argument Market market) {
        log.debug("[GraphQL] activeRecommendations: market={}", market);
        if (market != null) {
            return recoRepo.findRecentByMarket(market,
                    LocalDateTime.now().minusHours(24));
        }
        return recoRepo.findActiveTopBuys();
    }

    // ─── Composite dashboard query ─────────────────────────────────────────────
    @QueryMapping
    public StockDashboard stockDashboard(@Argument String symbol,
                                         @Argument Market market) {
        log.debug("[GraphQL] stockDashboard: {} on {}", symbol, market);
        return new StockDashboard(symbol, market);
    }

    // ─── Input + Output records ────────────────────────────────────────────────
    public record ScreenerInput(
            Market market,
            Double minConfidence,
            List<SignalType> signals,
            RiskLevel maxRiskLevel,
            Double minTechnicalScore,
            Double minFundamentalScore,
            List<String> sectors,
            Integer limit
    ) {}

    public record StockDashboard(String symbol, Market market) {}
}
