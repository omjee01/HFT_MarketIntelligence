package com.hft.model.enums;

/** Price trend direction based on technical analysis. */
public enum TrendDirection {
    STRONG_UPTREND  ("Strong Uptrend",   2),
    UPTREND         ("Uptrend",          1),
    SIDEWAYS        ("Sideways",         0),
    DOWNTREND       ("Downtrend",       -1),
    STRONG_DOWNTREND("Strong Downtrend",-2);

    private final String displayName;
    private final int score;   // used for composite scoring

    TrendDirection(String displayName, int score) {
        this.displayName = displayName;
        this.score = score;
    }

    public String getDisplayName() { return displayName; }
    public int getScore()          { return score; }
}