package com.hft.model.enums;

import java.math.BigDecimal;

/**
 * Market-capitalization tier, largest first. Thresholds match the bucketing
 * com.hft.ml.MLFeatureExtractor.marketCapClass() already used for the ML feature vector
 * (0=micro..4=mega) — this enum is the single source of truth now; that method delegates here.
 */
public enum MarketCapTier {
    MEGA("Mega Cap"),
    LARGE("Large Cap"),
    MID("Mid Cap"),
    SMALL("Small Cap"),
    MICRO("Micro Cap");

    private final String displayName;

    MarketCapTier(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MarketCapTier fromMarketCap(BigDecimal marketCap) {
        if (marketCap == null) return MID;   // unknown — assume the middle of the range, not an extreme
        double mc = marketCap.doubleValue();
        if (mc < 300_000_000)        return MICRO;
        if (mc < 2_000_000_000)      return SMALL;
        if (mc < 10_000_000_000.0)   return MID;
        if (mc < 200_000_000_000.0)  return LARGE;
        return MEGA;
    }
}
