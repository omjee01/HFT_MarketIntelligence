package com.hft.ml;

import lombok.*;

/**
 * 41-feature vector assembled from all domain analysis objects.
 * Consumed by both ScoringModel implementations for consistent, typed input.
 *
 * Groups:
 *   Technical  (14) — price action, momentum, volatility, volume
 *   Fundamental ( 8) — valuation, profitability, health, growth
 *   Sentiment   ( 7) — news NLP, social metrics, mention velocity
 *   Macro       ( 7) — rates, inflation, VIX, FII flow, regime
 *   Price       ( 5) — 52-week range, day change, volume spike
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLFeatureVector {

    String symbol;
    String market;

    // ─── Technical (14) ───────────────────────────────────────────────────────
    double rsi14;             // 0–100; >70 overbought, <30 oversold
    double macdLine;          // raw MACD; positive = bullish momentum
    double macdHistogram;     // histogram; positive = momentum accelerating
    double bbPosition;        // (price – lower) / (upper – lower), 0=oversold 1=overbought
    double bbWidth;           // (upper – lower) / middle — volatility proxy
    double sma20Distance;     // (price / sma20) – 1
    double sma50Distance;     // (price / sma50) – 1
    double sma200Distance;    // (price / sma200) – 1; positive = long-term uptrend
    double ema9Distance;      // (price / ema9) – 1; short-term momentum
    double atrNormalized;     // atr14 / price — volatility as % of price
    double volumeRatio;       // current vol / 20-day avg vol
    double obvTrend;          // +1 uptrend, –1 downtrend (from obvTrendingUp)
    double technicalScore;    // 0–100 composite TA score
    double smaAlignment;      // +1 if price>ema9>sma20>sma50>sma200, –1 otherwise

    // ─── Fundamental (8) ──────────────────────────────────────────────────────
    double peRatioNorm;       // P/E ratio capped at 100 (0 if unavailable)
    double pbRatio;           // Price-to-Book
    double roe;               // Return on Equity %
    double debtToEquity;      // D/E ratio; lower = healthier
    double revenueGrowthYoY;  // Revenue growth YoY %
    double epsGrowthYoY;      // EPS growth YoY %
    double dividendYield;     // Dividend yield %
    double fundamentalScore;  // 0–100 composite FA score

    // ─── Sentiment (7) ────────────────────────────────────────────────────────
    double sentimentRaw;      // overallSentimentScore –1 to +1
    double bullishPercent;    // positiveNews / totalNews × 100
    double bearishPercent;    // negativeNews / totalNews × 100
    double newsCountLog;      // log(1 + newsArticles24h)
    double mentionsLog;       // log(1 + totalMentions24h)
    double sentimentMomentum; // +1 RISING, 0 STABLE, –1 FALLING
    double normalizedSentiment; // 0–100 via getNormalizedScore()

    // ─── Macro (7) ────────────────────────────────────────────────────────────
    double gdpGrowthRate;     // GDP growth YoY %
    double inflationRate;     // CPI inflation %
    double centralBankRate;   // Central bank policy rate %
    double vixLevel;          // VIX; <15 = calm, >25 = fearful
    double fiiFlowNorm;       // FII net flow normalized –1 to +1
    double macroScore;        // 0–100 composite macro score
    double marketRegime;      // 1=bull(VIX<15), 0.5=neutral, 0=bear(VIX>25)

    // ─── Price (5) ────────────────────────────────────────────────────────────
    double percentFrom52High; // (price / 52wHigh) – 1; negative = below high
    double percentFrom52Low;  // (price / 52wLow) – 1; positive = premium over low
    double dayChangePct;      // day change % (BigDecimal.doubleValue)
    double volumeSpike;       // volumeRatio – 1; positive = above-average volume
    double marketCapClass;    // 0=micro <$300M, 1=small, 2=mid, 3=large, 4=mega ≥$200B

    /**
     * Flattens the 41 double fields above, in declaration order — the exact shape
     * com.hft.intelligence.SourceSignal's context expects (SourceSignal.CONTEXT_DIM = 41,
     * see that class's javadoc). Keep this in lockstep with the field list above.
     */
    public double[] toContextArray() {
        return new double[] {
            rsi14, macdLine, macdHistogram, bbPosition, bbWidth, sma20Distance, sma50Distance,
            sma200Distance, ema9Distance, atrNormalized, volumeRatio, obvTrend, technicalScore, smaAlignment,
            peRatioNorm, pbRatio, roe, debtToEquity, revenueGrowthYoY, epsGrowthYoY, dividendYield, fundamentalScore,
            sentimentRaw, bullishPercent, bearishPercent, newsCountLog, mentionsLog, sentimentMomentum, normalizedSentiment,
            gdpGrowthRate, inflationRate, centralBankRate, vixLevel, fiiFlowNorm, macroScore, marketRegime,
            percentFrom52High, percentFrom52Low, dayChangePct, volumeSpike, marketCapClass
        };
    }
}
