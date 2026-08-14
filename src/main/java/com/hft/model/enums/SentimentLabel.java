package com.hft.model.enums;

/** NLP sentiment classification label. */
public enum SentimentLabel {
    VERY_BULLISH ("Very Bullish",  0.6,  1.0),
    BULLISH      ("Bullish",       0.2,  0.6),
    NEUTRAL      ("Neutral",      -0.2,  0.2),
    BEARISH      ("Bearish",      -0.6, -0.2),
    VERY_BEARISH ("Very Bearish", -1.0, -0.6);

    private final String displayName;
    private final double minScore;
    private final double maxScore;

    SentimentLabel(String displayName, double minScore, double maxScore) {
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDisplayName() { return displayName; }
    public double getMinScore()    { return minScore; }
    public double getMaxScore()    { return maxScore; }

    public static SentimentLabel fromScore(double score) {
        if (score >= 0.6)  return VERY_BULLISH;
        if (score >= 0.2)  return BULLISH;
        if (score >= -0.2) return NEUTRAL;
        if (score >= -0.6) return BEARISH;
        return VERY_BEARISH;
    }
}