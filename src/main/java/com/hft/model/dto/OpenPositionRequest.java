package com.hft.model.dto;

import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * "I bought this" — recorded AFTER the user completes a purchase on their own brokerage
 * (Zerodha Kite / INDmoney — see HFT_ARCHITECTURE.md §30). This platform never executes
 * trades; it only tracks what the user tells it they already bought elsewhere.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenPositionRequest {

    @NotBlank
    private String symbol;

    // Optional — supplied directly by the detail view that had it on hand (e.g. an IPO's
    // companyName). Without it, openPosition() tries a live quote lookup, which fails for
    // symbols that aren't actually tradeable yet (a pre-listing IPO symbol), falling back to
    // the bare symbol string.
    private String companyName;

    @NotNull
    private Market market;

    @Builder.Default
    private AssetType assetType = AssetType.STOCK;

    @NotNull @DecimalMin(value = "0.0001")
    private BigDecimal quantity;

    @NotNull @DecimalMin(value = "0.0001")
    private BigDecimal avgBuyPrice;

    // Optional — links back to the recommendation that was being viewed when the user bought,
    // so its target/stop-loss seed this position's targetPrice/stopLossPrice automatically.
    private String recommendationId;

    // Only used if recommendationId isn't supplied (e.g. buying without going through a
    // recommendation detail view first).
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
}
