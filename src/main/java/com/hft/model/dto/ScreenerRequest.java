package com.hft.model.dto;

import com.hft.model.enums.Market;
import com.hft.model.enums.SignalType;
import lombok.*;

/** Request body for the stock screener endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerRequest {

    private Market market;
    private SignalType signal;
    private String sector;

    // Technical filters
    private Double maxRsi;               // e.g. 40 (look for oversold)
    private Double minRsi;               // e.g. 30
    private Boolean macdBullish;         // only MACD bullish crossover stocks
    private Boolean aboveSma200;         // price > SMA200
    private Boolean goldenCross;         // SMA50 > SMA200

    // Fundamental filters
    private Double maxPe;                // e.g. 25
    private Double minRoe;               // e.g. 15 (%)
    private Boolean debtFree;            // debt/equity < 0.3

    // Composite filters
    private Double minCompositeScore;    // e.g. 65
    private Double minConfidence;        // e.g. 60
    private Integer topN;                // return top N results

    // Volume / Liquidity
    private Long minAvgVolume;           // e.g. 500000
    private Double minMarketCapCrores;   // India only
    private Double minMarketCapBillion;  // US only
}