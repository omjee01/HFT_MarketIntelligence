package com.hft.model.enums;

/**
 * Represents the type of financial instrument being analyzed.
 */
public enum AssetType {
    STOCK("Equity Share"),
    OPTION("Derivative Option"),
    FUTURE("Derivative Future"),
    COMMODITY("Physical / Futures Commodity"),
    IPO("Initial Public Offering"),
    ETF("Exchange Traded Fund"),
    MUTUAL_FUND("Mutual Fund"),
    BOND("Fixed Income Bond"),
    CURRENCY("Forex Currency Pair"),
    CRYPTO("Cryptocurrency");

    private final String displayName;

    AssetType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}