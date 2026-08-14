package com.hft.controller;

import com.hft.model.domain.TradeRecommendation;
import com.hft.model.dto.ApiResponse;
import com.hft.model.dto.RecommendationRequest;
import com.hft.model.dto.RecommendationResponse;
import com.hft.model.enums.Market;
import com.hft.repository.TradeRecommendationRepository;
import com.hft.service.analysis.MacroGeopoliticalService;
import com.hft.service.signal.RecommendationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for trade recommendations.
 *
 * Key endpoints:
 *  GET  /api/v1/recommendations/daily          → Top daily recommendations (US + India)
 *  GET  /api/v1/recommendations/us             → Top 5 US stock picks today
 *  GET  /api/v1/recommendations/india          → Top 5 Indian stock picks today
 *  GET  /api/v1/recommendations/stock/{symbol} → Deep-dive for specific symbol
 *  POST /api/v1/recommendations/watchlist      → Custom watchlist analysis
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "AI-powered BUY/SELL/HOLD trade recommendations")
public class RecommendationController {

    private final RecommendationEngine      engine;
    private final TradeRecommendationRepository recoRepo;
    private final MacroGeopoliticalService  macroService;

    // ─── Daily Recommendations ────────────────────────────────────────────────

    @GetMapping("/daily")
    @Operation(summary = "Get today's top recommendations across US and Indian markets")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getDailyRecommendations(
            @RequestParam(defaultValue = "5") int topN) {

        log.info("[API] Daily recommendations requested (topN={})", topN);

        List<TradeRecommendation> usTop   = engine.generateTopRecommendations(Market.US_NASDAQ, topN);
        List<TradeRecommendation> indiaTop = engine.generateTopRecommendations(Market.INDIA_NSE, topN);

        RecommendationResponse response = RecommendationResponse.builder()
                .generatedAt(LocalDateTime.now())
                .marketSummary(buildMarketSummary(usTop, indiaTop))
                .totalAnalyzed(25 + 23)  // US + India watchlist sizes
                .totalRecommendations(usTop.size() + indiaTop.size())
                .topBuys(mergeAndSort(usTop, indiaTop, topN))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response,
                String.format("Generated %d recommendations", response.getTotalRecommendations())));
    }

    @GetMapping("/us")
    @Operation(summary = "Top US stock picks for today (NYSE/NASDAQ)")
    public ResponseEntity<ApiResponse<List<TradeRecommendation>>> getUSRecommendations(
            @RequestParam(defaultValue = "5") int topN) {

        List<TradeRecommendation> recommendations = engine.generateTopRecommendations(Market.US_NASDAQ, topN);
        return ResponseEntity.ok(ApiResponse.success(recommendations,
                String.format("Top %d US recommendations", recommendations.size())));
    }

    @GetMapping("/india")
    @Operation(summary = "Top Indian stock picks for today (NSE/BSE)")
    public ResponseEntity<ApiResponse<List<TradeRecommendation>>> getIndiaRecommendations(
            @RequestParam(defaultValue = "5") int topN) {

        List<TradeRecommendation> recommendations = engine.generateTopRecommendations(Market.INDIA_NSE, topN);
        return ResponseEntity.ok(ApiResponse.success(recommendations,
                String.format("Top %d India recommendations", recommendations.size())));
    }

    // ─── Single Symbol ────────────────────────────────────────────────────────

    @GetMapping("/stock/{symbol}")
    @Operation(summary = "Deep-dive recommendation for a specific stock symbol")
    public ResponseEntity<ApiResponse<TradeRecommendation>> getStockRecommendation(
            @PathVariable String symbol,
            @Parameter(description = "Market: US_NYSE, US_NASDAQ, INDIA_NSE, INDIA_BSE")
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        log.info("[API] Recommendation requested for {} on {}", symbol, market);
        Optional<TradeRecommendation> reco = engine.generateRecommendation(symbol.toUpperCase(), market);

        return reco.map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                   .orElse(ResponseEntity.ok(ApiResponse.error(
                           "No recommendation available for " + symbol,
                           "NO_DATA")));
    }

    // ─── Custom Watchlist ──────────────────────────────────────────────────────

    @PostMapping("/watchlist")
    @Operation(summary = "Analyze a custom list of symbols and return recommendations")
    public ResponseEntity<ApiResponse<List<TradeRecommendation>>> analyzeWatchlist(
            @Valid @RequestBody RecommendationRequest request) {

        if (request.getWatchlistSymbols() == null || request.getWatchlistSymbols().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("watchlistSymbols cannot be empty", "INVALID_REQUEST"));
        }

        Market market = request.getMarket() != null ? request.getMarket() : Market.US_NASDAQ;

        List<TradeRecommendation> results = request.getWatchlistSymbols().parallelStream()
                .map(sym -> engine.generateRecommendation(sym.toUpperCase(), market))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(r -> request.getOnlyBuySignals() == null
                          || !request.getOnlyBuySignals()
                          || r.getSignal().isBullish())
                .filter(r -> request.getMinConfidence() == null
                          || r.getConfidencePercent() >= request.getMinConfidence())
                .sorted(java.util.Comparator.comparingDouble(
                        TradeRecommendation::getCompositeScore).reversed())
                .limit(request.getTopN() != null ? request.getTopN() : 10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(results,
                String.format("Analyzed %d symbols, found %d recommendations",
                              request.getWatchlistSymbols().size(), results.size())));
    }

    // ─── History ──────────────────────────────────────────────────────────────

    @GetMapping("/history/{symbol}")
    @Operation(summary = "Get historical recommendations for a symbol")
    public ResponseEntity<ApiResponse<List<TradeRecommendation>>> getHistory(
            @PathVariable String symbol) {

        List<TradeRecommendation> history = recoRepo.findHistoryForSymbol(symbol.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/active")
    @Operation(summary = "All currently active BUY recommendations")
    public ResponseEntity<ApiResponse<List<TradeRecommendation>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(recoRepo.findActiveTopBuys()));
    }

    // ─── Market Summary Builder ───────────────────────────────────────────────

    private String buildMarketSummary(List<TradeRecommendation> us, List<TradeRecommendation> india) {
        long usBuys   = us.stream().filter(r -> r.getSignal().isBullish()).count();
        long indiaBuys = india.stream().filter(r -> r.getSignal().isBullish()).count();
        return String.format("US Markets: %d BUY signals | Indian Markets: %d BUY signals",
                              usBuys, indiaBuys);
    }

    private List<TradeRecommendation> mergeAndSort(List<TradeRecommendation> us,
                                                    List<TradeRecommendation> india,
                                                    int limit) {
        return java.util.stream.Stream.concat(us.stream(), india.stream())
                .sorted(java.util.Comparator.comparingDouble(
                        TradeRecommendation::getCompositeScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}