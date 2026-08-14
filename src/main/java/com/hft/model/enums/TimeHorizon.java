package com.hft.model.enums;

/**
 * Expected holding period for a trade recommendation.
 */
public enum TimeHorizon {
    INTRADAY    ("Intraday",      0,   1,  "Same day trade"),
    SHORT_TERM  ("Short-Term",    1,   7,  "1–7 days swing trade"),
    MEDIUM_TERM ("Medium-Term",   7,  28,  "1–4 weeks positional trade"),
    LONG_TERM   ("Long-Term",    28, 180,  "1–6 months investment");

    private final String displayName;
    private final int minDays;
    private final int maxDays;
    private final String description;

    TimeHorizon(String displayName, int minDays, int maxDays, String description) {
        this.displayName = displayName;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public int getMinDays()        { return minDays; }
    public int getMaxDays()        { return maxDays; }
    public String getDescription() { return description; }

    public static TimeHorizon fromDays(int days) {
        if (days <= 1)  return INTRADAY;
        if (days <= 7)  return SHORT_TERM;
        if (days <= 28) return MEDIUM_TERM;
        return LONG_TERM;
    }
}