package com.hft.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ensemble Model (Model B) — blends three regime-aware sub-models.
 *
 * Sub-models and their edge:
 *   MomentumModel    — strongest in trending / bull markets (RSI, MACD, volume, OBV)
 *   MeanReversion    — strongest in range-bound markets (BB position, RSI extremes)
 *   TrendModel       — structural long-term position (SMA alignment, VWAP, macro)
 *
 * Regime-based blending (driven by VIX):
 *   Bull (VIX < 15) : momentum 50%, trend 35%, reversion 15%
 *   Bear (VIX > 25) : reversion 40%, trend 40%, momentum 20%
 *   Neutral          : balanced 33% / 34% / 33%
 *
 * Compared with Model A (weighted composite):
 *   + Adapts blend weights to live market regime
 *   + Cross-signal confirmation bonus rewards multi-signal alignment
 *   + Conflict penalty discounts contradictory signals
 *   – Requires more features; falls back to neutral defaults on missing data
 */
@Slf4j
@Component
public class EnsembleModel {

    /** Compute a 0–100 composite score for the feature vector. */
    public double computeScore(MLFeatureVector v) {
        double[] w = regimeWeights(v.getMarketRegime());
        double blended = momentumScore(v)  * w[0]
                       + reversionScore(v) * w[1]
                       + trendScore(v)     * w[2];

        blended += confirmationBonus(v);
        blended -= conflictPenalty(v);
        return clip(blended);
    }

    /** Derive confidence (25–93) from the score and market conditions. */
    public double computeConfidence(MLFeatureVector v, double score) {
        double conf = score;

        if (v.getMarketRegime() > 0.7 && score > 65)  conf += 5;   // bull regime + strong buy
        if (v.getMarketRegime() < 0.3 && score < 35)  conf += 5;   // bear regime + confirmed sell
        if (v.getFundamentalScore() > 70 && v.getRoe() > 15) conf += 4;

        if (v.getVixLevel() > 30) conf -= 8;
        if (v.getVixLevel() > 40) conf -= 8;
        if (v.getPercentFrom52High() > 0.15) conf -= 5;             // extended above 52-week high
        if (v.getFundamentalScore() == 50.0) conf -= 3;             // FA data unavailable (default)

        return Math.min(93, Math.max(25, conf));
    }

    // ─── Sub-model: Momentum ──────────────────────────────────────────────────

    private double momentumScore(MLFeatureVector v) {
        double s = 50.0;

        if      (v.getRsi14() < 30) s += 15;
        else if (v.getRsi14() < 40) s +=  8;
        else if (v.getRsi14() > 70) s -= 12;
        else if (v.getRsi14() > 60) s -=  5;

        s += v.getMacdHistogram() > 0 ? 8 : -6;
        s += v.getMacdLine()      > 0 ? 5 : -4;

        if (v.getVolumeRatio() > 1.5) s += 6;
        if (v.getVolumeRatio() < 0.5) s -= 4;

        s += v.getObvTrend() * 7;
        s += v.getDayChangePct() * 3;

        return clip(s);
    }

    // ─── Sub-model: Mean Reversion ────────────────────────────────────────────

    private double reversionScore(MLFeatureVector v) {
        double s = 50.0;

        if      (v.getBbPosition() < 0.15) s += 18;
        else if (v.getBbPosition() < 0.30) s +=  8;
        else if (v.getBbPosition() > 0.85) s -= 15;
        else if (v.getBbPosition() > 0.70) s -=  7;

        if (v.getBbWidth() > 0.08) s -= 5;  // wide band → reversion less reliable

        if (v.getPercentFrom52Low()  <  0.05) s += 10;  // near 52-week low → reversal potential
        if (v.getPercentFrom52High() > -0.05) s -=  8;  // near 52-week high → potential top

        return clip(s);
    }

    // ─── Sub-model: Trend ─────────────────────────────────────────────────────

    private double trendScore(MLFeatureVector v) {
        double s = 50.0;

        s += v.getSmaAlignment() > 0 ? 20 : -15;          // bull SMA stack vs death stack
        s += v.getSma200Distance() > 0 ? 8 : -10;         // above vs below 200-day MA
        s += (v.getMacroScore() - 50) * 0.4;              // macro 80 → +12
        s += (v.getFundamentalScore() - 50) * 0.3;        // fundamentals 70 → +6
        s += v.getFiiFlowNorm() * 8;                       // strong FII buying → +8

        return clip(s);
    }

    // ─── Regime weights [momentum, reversion, trend] ─────────────────────────

    private double[] regimeWeights(double regime) {
        if (regime > 0.7) return new double[]{0.50, 0.15, 0.35};   // bull
        if (regime < 0.3) return new double[]{0.20, 0.40, 0.40};   // bear
        return           new double[]{0.33, 0.33, 0.34};            // neutral
    }

    // ─── Cross-signal bonuses and conflict penalties ──────────────────────────

    private double confirmationBonus(MLFeatureVector v) {
        double bonus = 0;
        if (v.getTechnicalScore() > 70 && v.getSentimentRaw() > 0.2)    bonus += 3;
        if (v.getFundamentalScore() > 70 && v.getTechnicalScore() > 65)  bonus += 3;
        if (v.getVolumeRatio() > 2.0 && v.getObvTrend() > 0)            bonus += 2;
        return Math.min(8, bonus);
    }

    private double conflictPenalty(MLFeatureVector v) {
        double penalty = 0;
        if (v.getRsi14() > 70 && v.getSentimentRaw() > 0.3)             penalty += 5;  // overbought + euphoria
        if (v.getMacroScore() < 35 && v.getTechnicalScore() > 70)       penalty += 5;  // macro headwind
        if (v.getPercentFrom52High() > 0.1 && v.getVolumeRatio() < 0.7) penalty += 4;  // distribution signal
        return Math.min(12, penalty);
    }

    private double clip(double v) { return Math.min(100, Math.max(0, v)); }
}
