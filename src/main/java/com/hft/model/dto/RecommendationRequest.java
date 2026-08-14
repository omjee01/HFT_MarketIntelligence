package com.hft.model.dto;

import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import com.hft.model.enums.RiskLevel;
import com.hft.model.enums.TimeHorizon;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.List;

/** Request body for getting filtered trade recommendations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {

    private Market market;                    // null = ALL markets
    private AssetType assetType;              // null = ALL asset types
    private TimeHorizon timeHorizon;          // null = ALL horizons
    private RiskLevel maxRiskLevel;           // filter: max risk tolerance

    @Min(0) @Max(100)
    private Double minConfidence;             // e.g. 65.0 (%)

    @Min(1) @Max(50)
    @Builder.Default
    private Integer topN = 10;                // how many results to return

    private List<String> watchlistSymbols;    // optional: analyze specific symbols
    private List<String> sectors;            // optional: filter by sector

    private Boolean onlyBuySignals;          // true = only BUY/STRONG_BUY
    private Boolean excludeHighRisk;         // true = skip VERY_HIGH risk items
}