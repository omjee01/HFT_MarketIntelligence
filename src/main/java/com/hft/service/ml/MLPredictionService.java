package com.hft.service.ml;

import com.hft.model.domain.*;
import com.hft.model.enums.Market;
import com.hft.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * ML Prediction Engine — produces price targets, confidence scores, and timing estimates.
 *
 * Phase-1 Implementation: Weighted Ensemble + Linear Regression
 *  - Composite scoring model (weighted sum of all analysis scores)
 *  - Linear regression on recent price trend for target estimation
 *  - ATR-based stop loss
 *  - Historical average holding period by sector/signal strength
 *
 * Phase-2 (future): LSTM Neural Network for 30-day price series prediction
 *
 * Output per symbol:
 *  - Composite Score (0–100)
 *  - Expected Price Target
 *  - Stop Loss Price
 *  - Expected Profit %
 *  - Confidence Score
 *  - Expected Entry & Exit Date
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MLPredictionService {

    private final DateUtil dateUtil;

    @Value("${hft.recommendation.composite-weights.technical:0.35}")
    private double technicalWeight;

    @Value("${hft.recommendation.composite-weights.fundamental:0.25}")
    private double fundamentalWeight;

    @Value("${hft.recommendation.composite-weights.sentiment:0.20}")
    private double sentimentWeight;

    @Value("${hft.recommendation.composite-weights.macro:0.15}")
    private double macroWeight;

    @Value("${hft.recommendation.composite-weights.ml:0.05}")
    private double mlModelWeight;

    /**
     * Full ML prediction for a symbol given all analysis inputs.
     */
    public MLPrediction predict(String symbol, Market market,
                                 BigDecimal currentPrice,
                                 TechnicalIndicators ta,
                                 FundamentalData fd,
                                 SentimentData sentiment,
                                 MacroData macro,
                                 List<OHLCVData> recentBars) {

        // ── STEP 1: Normalize all scores to 0-100 ────────────────────────────
        double techScore = ta != null && ta.getTechnicalScore() != null
                           ? ta.getTechnicalScore() : 50.0;

        double fundScore = fd != null && fd.getFundamentalScore() != null
                           ? fd.getFundamentalScore() : 50.0;

        double sentScore = sentiment != null
                           ? sentiment.getNormalizedScore() : 50.0;

        double macroScore = macro != null && macro.getMacroScore() != null
                            ? macro.getMacroScore() : 50.0;

        // ── STEP 2: Regression-based ML score ────────────────────────────────
        double mlScore = computeRegressionScore(recentBars);

        // ── STEP 3: Apply sector-aware weights ────────────────────────────────
        double[] weights = adjustWeightsForMarket(market, macro);

        double compositeScore = (techScore  * weights[0])
                              + (fundScore  * weights[1])
                              + (sentScore  * weights[2])
                              + (macroScore * weights[3])
                              + (mlScore    * weights[4]);

        compositeScore = Math.min(100, Math.max(0, compositeScore));

        // ── STEP 4: Price Target Estimation ──────────────────────────────────
        double priceTarget = estimatePriceTarget(currentPrice.doubleValue(),
                                                  compositeScore, ta, recentBars);
        double stopLoss    = computeStopLoss(currentPrice.doubleValue(), ta);

        double profitPct   = (priceTarget - currentPrice.doubleValue())
                            / currentPrice.doubleValue() * 100.0;
        double maxRiskPct  = (currentPrice.doubleValue() - stopLoss)
                            / currentPrice.doubleValue() * 100.0;
        double rrRatio     = maxRiskPct > 0 ? profitPct / maxRiskPct : 1.0;

        // ── STEP 5: Confidence Score ──────────────────────────────────────────
        double confidence  = computeConfidence(compositeScore, ta, sentiment, macro);

        // ── STEP 6: Timing Estimate ───────────────────────────────────────────
        LocalDate entryDate = computeEntryDate(ta, recentBars);
        int holdingDays     = estimateHoldingDays(compositeScore);
        LocalDate exitDate  = dateUtil.addTradingDays(entryDate, holdingDays);

        log.debug("[ML] {} → Composite: {:.1f}, Target: {:.2f}, Conf: {:.0f}%",
                  symbol, compositeScore, priceTarget, confidence);

        return MLPrediction.builder()
                .symbol(symbol)
                .market(market)
                .compositeScore(round(compositeScore))
                .technicalScore(round(techScore))
                .fundamentalScore(round(fundScore))
                .sentimentScore(round(sentScore))
                .macroScore(round(macroScore))
                .mlModelScore(round(mlScore))
                .currentPrice(currentPrice)
                .predictedTargetPrice(BigDecimal.valueOf(round(priceTarget)))
                .stopLossPrice(BigDecimal.valueOf(round(stopLoss)))
                .expectedProfitPercent(round(profitPct))
                .maxRiskPercent(round(maxRiskPct))
                .riskRewardRatio(round(rrRatio))
                .confidencePercent(round(confidence))
                .entryDate(entryDate)
                .exitDate(exitDate)
                .holdingPeriodDays(holdingDays)
                .build();
    }

    // ─── Price Target via Linear Regression ──────────────────────────────────

    private double computeRegressionScore(List<OHLCVData> bars) {
        if (bars == null || bars.size() < 10) return 50.0;

        SimpleRegression reg = new SimpleRegression();
        int n = Math.min(bars.size(), 30);  // last 30 bars
        List<OHLCVData> recent = bars.subList(bars.size() - n, bars.size());
        for (int i = 0; i < recent.size(); i++) {
            reg.addData(i, recent.get(i).getClose().doubleValue());
        }

        double slope = reg.getSlope();
        double r2    = reg.getRSquare(); // goodness of fit

        // Convert slope to a score: positive slope = bullish
        if (!Double.isFinite(slope) || !Double.isFinite(r2)) return 50.0;

        double priceMean = recent.stream()
                                 .mapToDouble(b -> b.getClose().doubleValue())
                                 .average().orElse(1);
        double normalizedSlope = slope / priceMean * 100;  // slope as % of price per day

        double score = 50.0 + (normalizedSlope * 500);  // amplify small slope to score
        return Math.min(100, Math.max(0, score));
    }

    private double estimatePriceTarget(double currentPrice, double compositeScore,
                                        TechnicalIndicators ta, List<OHLCVData> bars) {
        // Base: use composite score to project return
        double baseReturn;
        if      (compositeScore >= 80) baseReturn = 0.18;  // 18% target for strong buy
        else if (compositeScore >= 65) baseReturn = 0.12;  // 12% for buy
        else if (compositeScore >= 55) baseReturn = 0.06;  // 6% for watch
        else if (compositeScore >= 45) baseReturn = 0.02;  // 2% hold
        else                           baseReturn = -0.08; // -8% sell

        // Adjust using regression projection
        if (bars != null && bars.size() >= 10) {
            SimpleRegression reg = new SimpleRegression();
            int n = Math.min(bars.size(), 20);
            List<OHLCVData> recent = bars.subList(bars.size() - n, bars.size());
            for (int i = 0; i < recent.size(); i++)
                reg.addData(i, recent.get(i).getClose().doubleValue());
            // Project 15 trading days ahead
            double projected = reg.predict(n + 15);
            if (Double.isFinite(projected) && projected > 0) {
                double regReturn = (projected - currentPrice) / currentPrice;
                baseReturn = (baseReturn + regReturn) / 2;  // average with composite estimate
            }
        }

        // Cap maximum target at ±40% (realistic bounds)
        baseReturn = Math.max(-0.40, Math.min(0.40, baseReturn));
        return currentPrice * (1 + baseReturn);
    }

    private double computeStopLoss(double currentPrice, TechnicalIndicators ta) {
        if (ta != null && ta.getAtr14() != null && ta.getAtr14() > 0) {
            // Stop = Entry - 2 × ATR (dynamic risk buffer)
            return Math.max(currentPrice * 0.85, currentPrice - 2 * ta.getAtr14());
        }
        // Fallback: 7% below current price
        return currentPrice * 0.93;
    }

    private double computeConfidence(double compositeScore,
                                      TechnicalIndicators ta,
                                      SentimentData sentiment,
                                      MacroData macro) {
        // Base confidence tracks composite score
        double conf = compositeScore;

        // Boost if multiple signals align
        if (ta != null) {
            if (ta.getMacdBullishCrossover() != null && ta.getMacdBullishCrossover()) conf += 5;
            if (ta.isRsiOversold()) conf += 5;
            if (ta.getDetectedPatterns() != null && ta.getDetectedPatterns().size() >= 2) conf += 3;
            if (Boolean.TRUE.equals(ta.getObvTrendingUp())) conf += 3;
        }
        if (sentiment != null && sentiment.isBullish()) conf += 4;
        if (macro != null && macro.getMacroScore() != null && macro.getMacroScore() > 60) conf += 3;

        // Penalize contradicting signals
        if (ta != null && ta.isRsiOverbought() && sentiment != null && sentiment.isBullish()) {
            conf -= 5;  // Overbought + bullish sentiment = less conviction
        }

        return Math.min(95, Math.max(25, conf));  // clamp 25-95%
    }

    private LocalDate computeEntryDate(TechnicalIndicators ta, List<OHLCVData> bars) {
        // If RSI oversold or MACD just crossed bullish → enter today
        if (ta != null && (ta.isRsiOversold() || Boolean.TRUE.equals(ta.getMacdBullishCrossover()))) {
            return LocalDate.now();
        }
        // Otherwise, wait for next dip: entry in 1-3 trading days
        return dateUtil.addTradingDays(LocalDate.now(), 1);
    }

    private int estimateHoldingDays(double compositeScore) {
        // Higher conviction = can hold longer for bigger target
        if (compositeScore >= 80) return 45;   // medium-term
        if (compositeScore >= 65) return 21;   // 3 weeks
        if (compositeScore >= 55) return 10;   // short-term swing
        return 5;                               // quick trade
    }

    private double[] adjustWeightsForMarket(Market market, MacroData macro) {
        // Base weights from config
        double[] w = { technicalWeight, fundamentalWeight, sentimentWeight, macroWeight, mlModelWeight };

        // Adjust for high volatility: increase macro weight
        if (macro != null && macro.getVixLevel() != null && macro.getVixLevel() > 25) {
            w[3] += 0.05;  // more macro weight when VIX is high
            w[0] -= 0.05;  // less TA weight
        }

        // Normalize to sum to 1.0
        double sum = 0;
        for (double wt : w) sum += wt;
        for (int i = 0; i < w.length; i++) w[i] /= sum;

        return w;
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ─── Inner Result DTO ──────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MLPrediction {
        private String symbol;
        private Market market;
        private double compositeScore;
        private double technicalScore;
        private double fundamentalScore;
        private double sentimentScore;
        private double macroScore;
        private double mlModelScore;
        private BigDecimal currentPrice;
        private BigDecimal predictedTargetPrice;
        private BigDecimal stopLossPrice;
        private double expectedProfitPercent;
        private double maxRiskPercent;
        private double riskRewardRatio;
        private double confidencePercent;
        private LocalDate entryDate;
        private LocalDate exitDate;
        private int holdingPeriodDays;
    }
}