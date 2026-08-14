package com.hft.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hft.model.domain.TradeRecommendation;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response DTO for the daily recommendations endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationResponse {

    private LocalDateTime generatedAt;
    private String marketSummary;          // "Markets are BULLISH today"

    private int totalAnalyzed;             // how many stocks were screened
    private int totalRecommendations;

    // ─── Categorized Recommendations ─────────────────────────────────────────
    private List<TradeRecommendation> topBuys;
    private List<TradeRecommendation> topSells;
    private List<TradeRecommendation> watchList;
    private List<TradeRecommendation> ipoRecommendations;
    private List<TradeRecommendation> optionStrategies;
    private List<TradeRecommendation> commodityTrades;

    // ─── Market Context ───────────────────────────────────────────────────────
    private Map<String, Object> usMacroSnapshot;
    private Map<String, Object> indiaMacroSnapshot;

    // ─── Sector Leaders ───────────────────────────────────────────────────────
    private List<String> bullishSectors;
    private List<String> bearishSectors;

    // ─── Risk Warning ─────────────────────────────────────────────────────────
    private String disclaimer;
    @Builder.Default
    private String legalNote = "For informational purposes only. " +
                               "Not investment advice. Consult a SEBI/SEC-registered advisor.";
}