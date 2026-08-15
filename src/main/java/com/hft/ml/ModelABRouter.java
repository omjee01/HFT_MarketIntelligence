package com.hft.ml;

import com.hft.model.domain.*;
import com.hft.model.enums.Market;
import com.hft.service.ml.MLPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * A/B Model Router — deterministic routing between Model A and Model B.
 *
 * Routing is symbol-stable: the same symbol always maps to the same model
 * within a configured split fraction, making performance comparison fair
 * (each model sees the same symbol across time, not random subsets).
 *
 * Config:
 *   hft.ml.model-router.model-b-fraction: 0.10   → 10% of symbols to Model B
 *
 * Model A: MLPredictionService (weighted composite + linear regression)
 * Model B: EnsembleModel (Momentum + MeanReversion + Trend, regime-blended)
 *
 * Both paths return MLPredictionService.MLPrediction — drop-in replacement
 * for the existing RecommendationEngine call site.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelABRouter {

    private final MLPredictionService    modelA;
    private final EnsembleModel          modelB;
    private final MLFeatureExtractor     featureExtractor;
    private final ModelPerformanceTracker tracker;

    @Value("${hft.ml.model-router.model-b-fraction:0.10}")
    private double modelBFraction;

    public MLPredictionService.MLPrediction route(
            String symbol, Market market, BigDecimal currentPrice,
            StockQuote quote, TechnicalIndicators ta, FundamentalData fd,
            SentimentData sentiment, MacroData macro, List<OHLCVData> recentBars) {

        boolean useB  = selectModelB(symbol);
        String  label = useB ? "B" : "A";

        MLPredictionService.MLPrediction prediction = useB
                ? runModelB(symbol, market, currentPrice, quote, ta, fd, sentiment, macro, recentBars)
                : modelA.predict(symbol, market, currentPrice, ta, fd, sentiment, macro, recentBars);

        tracker.recordPrediction(symbol, market, label, prediction);

        log.debug("[A/B] {} → Model{}: score={} conf={}%",
                  symbol, label, prediction.getCompositeScore(), prediction.getConfidencePercent());

        return prediction;
    }

    /** True when this symbol belongs to the Model B cohort (consistent hash). */
    public boolean selectModelB(String symbol) {
        if (modelBFraction <= 0) return false;
        if (modelBFraction >= 1) return true;
        return (Math.abs(symbol.hashCode()) % 100) < (int)(modelBFraction * 100);
    }

    public String selectedModelName(String symbol) {
        return selectModelB(symbol) ? "B_ENSEMBLE" : "A_WEIGHTED_COMPOSITE";
    }

    // ─── Model B path ────────────────────────────────────────────────────────

    private MLPredictionService.MLPrediction runModelB(
            String symbol, Market market, BigDecimal currentPrice,
            StockQuote quote, TechnicalIndicators ta, FundamentalData fd,
            SentimentData sentiment, MacroData macro, List<OHLCVData> recentBars) {

        MLFeatureVector fv = featureExtractor.extract(symbol, market, quote, ta, fd, sentiment, macro);

        double score      = modelB.computeScore(fv);
        double confidence = modelB.computeConfidence(fv, score);

        // Delegate price target / stop-loss to Model A (not its scoring — just pricing math)
        MLPredictionService.MLPrediction base = modelA.predict(
                symbol, market, currentPrice, ta, fd, sentiment, macro, recentBars);

        return MLPredictionService.MLPrediction.builder()
                .symbol(symbol)
                .market(market)
                .compositeScore(r(score))
                .technicalScore(r(fv.getTechnicalScore()))
                .fundamentalScore(r(fv.getFundamentalScore()))
                .sentimentScore(r(fv.getNormalizedSentiment()))
                .macroScore(r(fv.getMacroScore()))
                .mlModelScore(r(score))
                .currentPrice(currentPrice)
                .predictedTargetPrice(base.getPredictedTargetPrice())
                .stopLossPrice(base.getStopLossPrice())
                .expectedProfitPercent(r(base.getExpectedProfitPercent()))
                .maxRiskPercent(r(base.getMaxRiskPercent()))
                .riskRewardRatio(r(base.getRiskRewardRatio()))
                .confidencePercent(r(confidence))
                .entryDate(base.getEntryDate())
                .exitDate(base.getExitDate())
                .holdingPeriodDays(base.getHoldingPeriodDays())
                .build();
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }
}