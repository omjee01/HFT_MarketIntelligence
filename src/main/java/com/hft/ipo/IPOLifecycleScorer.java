package com.hft.ipo;

import com.hft.model.domain.IPOData;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.TradeRecommendation;
import com.hft.repository.IPODataRepository;
import com.hft.repository.OHLCVDataRepository;
import com.hft.service.signal.RecommendationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * IPO Phase 2 (post-listing) hold/sell lifecycle — HFT_ARCHITECTURE.md §22.3.
 * Day 0 flip decision, Days 1-19 reduced-indicator scoring (no SMA200 yet), Day 20+
 * graduates to the standard {@link RecommendationEngine} pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IPOLifecycleScorer {

    // Mirrors BacktestRunner.WARMUP_BARS (that field is private there, so it can't be
    // imported directly — same value, same rationale: SMA200 warm-up needs bars this system
    // doesn't yet have before day 20).
    static final int GRADUATION_BAR_COUNT = 20;
    private static final String INTERVAL_1D = "1D";

    private final IPODataRepository ipoRepo;
    private final OHLCVDataRepository ohlcvRepo;
    private final IPOAnalysisService ipoAnalysisService;
    private final RecommendationEngine recommendationEngine;

    public record LifecycleDecision(String action, String reason, TradeRecommendation graduatedRecommendation) {}

    public LifecycleDecision evaluate(IPOData ipo) {
        List<OHLCVData> bars = ohlcvRepo.findBySymbolAndMarketAndIntervalTypeOrderByBarDate(
                ipo.getSymbol(), ipo.getMarket(), INTERVAL_1D);

        if (bars.isEmpty()) {
            return new LifecycleDecision("HOLD", "Not yet listed — no OHLCV bars available", null);
        }
        if (bars.size() >= GRADUATION_BAR_COUNT) {
            return graduate(ipo);
        }

        OHLCVData day0 = bars.get(0);
        if (bars.size() == 1) {
            return evaluateDayZero(ipo, day0);
        }
        return evaluateEarlyDays(ipo, bars, day0);
    }

    // ─── Day 20+: graduate to the standard pipeline ────────────────────────────────────────

    private LifecycleDecision graduate(IPOData ipo) {
        return recommendationEngine.generateRecommendation(ipo.getSymbol(), ipo.getMarket())
                .map(r -> new LifecycleDecision(r.getSignal().name(),
                        "Graduated to standard RecommendationEngine (" + GRADUATION_BAR_COUNT + "+ bars) — IPO lifecycle handling ends here",
                        r))
                .orElse(new LifecycleDecision("HOLD",
                        "Graduated (" + GRADUATION_BAR_COUNT + "+ bars) but RecommendationEngine returned no recommendation", null));
    }

    // ─── Day 0: the flip decision ───────────────────────────────────────────────────────────

    private LifecycleDecision evaluateDayZero(IPOData ipo, OHLCVData day0) {
        BigDecimal issueHigh = ipo.getIssuePriceHigh();
        if (issueHigh == null || issueHigh.compareTo(BigDecimal.ZERO) == 0) {
            return new LifecycleDecision("HOLD", "No issue price on record — cannot compute actual listing gain", null);
        }

        double actualGain = day0.getOpen().subtract(issueHigh)
                .divide(issueHigh, 6, RoundingMode.HALF_UP)
                .doubleValue() * 100.0;

        ipo.setActualListingGainPercent(round(actualGain));
        ipo.setStatus("LISTED");

        double predicted = ipo.getPredictedListingGainPercent() != null ? ipo.getPredictedListingGainPercent() : 0.0;
        double quality = ipoAnalysisService.computeQualityScore(ipo);

        String action;
        String reason;
        if (actualGain >= 1.5 * predicted && predicted > 0) {
            action = "PARTIAL_SELL";
            reason = String.format("Listing gain %.1f%% is >= 1.5x the predicted %.1f%% — lock in the pop", actualGain, predicted);
        } else if (actualGain < predicted && quality >= 60) {
            action = "HOLD";
            reason = String.format("Gain %.1f%% below predicted %.1f%%, but quality score %.1f >= 60 — thesis intact", actualGain, predicted, quality);
        } else if (actualGain < 0 && quality < 60) {
            action = "SELL";
            reason = String.format("Negative listing gain (%.1f%%) and weak quality score (%.1f) — both the pop and the thesis failed", actualGain, quality);
        } else {
            action = "HOLD";
            reason = String.format("Listing gain %.1f%% — no override condition met, default hold", actualGain);
        }

        ipoRepo.save(ipo);
        return new LifecycleDecision(action, reason, null);
    }

    // ─── Days 1-19: reduced-indicator scorer (no SMA200 yet) ───────────────────────────────

    private LifecycleDecision evaluateEarlyDays(IPOData ipo, List<OHLCVData> bars, OHLCVData day0) {
        OHLCVData latest = bars.get(bars.size() - 1);
        boolean heldAboveOpen = latest.getClose().compareTo(day0.getOpen()) >= 0;

        int earlyWindow = Math.min(3, bars.size());
        double earlyAvgVolume = bars.subList(0, earlyWindow).stream()
                .mapToLong(OHLCVData::getVolume).average().orElse(0);
        int recentWindow = Math.min(3, bars.size());
        double recentAvgVolume = bars.subList(bars.size() - recentWindow, bars.size()).stream()
                .mapToLong(OHLCVData::getVolume).average().orElse(0);
        boolean volumeFading = earlyAvgVolume > 0 && recentAvgVolume < earlyAvgVolume * 0.6;

        Double vol10 = realizedVolatility(bars, 10);
        Double vol5 = realizedVolatility(bars, 5);

        // Relative strength vs sector index is specified in §22.3 but omitted here — this
        // system has no sector-index OHLCV feed (no NIFTY/sector-ETF ingestion exists), and
        // fabricating one would violate the doc's own "omit undefined indicators rather than
        // default to zero" convention. A real gap, not a silent shortcut.

        List<String> negativeSignals = new ArrayList<>();
        if (!heldAboveOpen) negativeSignals.add("price below listing-day open (gap-fill failed)");
        if (volumeFading) negativeSignals.add("volume fading vs day 0-2");
        if (vol10 != null && vol10 > 0.05) negativeSignals.add(String.format("elevated 10-day volatility (%.1f%%)", vol10 * 100));

        // Two or more independently-confirming negative signals -> SELL; one alone is treated
        // as early noise (not yet enough bars for a confident call) -> cautious HOLD.
        String action = negativeSignals.size() >= 2 ? "SELL" : "HOLD";
        String reason = negativeSignals.isEmpty()
                ? String.format("Day %d — no negative signals, thesis holding", bars.size())
                : String.format("Day %d — %s", bars.size(), String.join("; ", negativeSignals));

        return new LifecycleDecision(action, reason, null);
    }

    private Double realizedVolatility(List<OHLCVData> bars, int window) {
        if (bars.size() < window + 1) return null;
        List<OHLCVData> recent = bars.subList(bars.size() - window - 1, bars.size());
        List<Double> logReturns = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            double prevClose = recent.get(i - 1).getClose().doubleValue();
            double close = recent.get(i).getClose().doubleValue();
            if (prevClose > 0) logReturns.add(Math.log(close / prevClose));
        }
        if (logReturns.isEmpty()) return null;
        double mean = logReturns.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = logReturns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).sum() / logReturns.size();
        return Math.sqrt(variance);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
