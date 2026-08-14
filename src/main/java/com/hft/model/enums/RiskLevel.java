package com.hft.model.enums;

/**
 * Risk classification of a trade recommendation.
 * Determined by volatility, R:R ratio, and confidence score.
 */
public enum RiskLevel {
    VERY_LOW ("Very Low",  1, "R:R > 4:1, Confidence > 85%, Low volatility"),
    LOW      ("Low",       2, "R:R > 3:1, Confidence > 75%"),
    MEDIUM   ("Medium",    3, "R:R > 2:1, Confidence > 65%"),
    HIGH     ("High",      4, "R:R > 1.5:1, Confidence > 55%, High beta/Options"),
    VERY_HIGH("Very High", 5, "Options/Commodities/Penny stocks, High leverage");

    private final String displayName;
    private final int level;           // 1 = safest, 5 = riskiest
    private final String description;

    RiskLevel(String displayName, int level, String description) {
        this.displayName = displayName;
        this.level = level;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public int getLevel()          { return level; }
    public String getDescription() { return description; }

    public static RiskLevel fromRiskRewardAndConfidence(double rrRatio, double confidence) {
        if (rrRatio > 4.0 && confidence > 85) return VERY_LOW;
        if (rrRatio > 3.0 && confidence > 75) return LOW;
        if (rrRatio > 2.0 && confidence > 65) return MEDIUM;
        if (rrRatio > 1.5 && confidence > 55) return HIGH;
        return VERY_HIGH;
    }
}