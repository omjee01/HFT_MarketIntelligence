package com.hft.ipo;

import com.hft.model.domain.IPOData;
import com.hft.model.domain.MacroData;
import com.hft.model.domain.SentimentData;
import com.hft.repository.IPODataRepository;
import com.hft.service.analysis.MacroGeopoliticalService;
import com.hft.service.analysis.SentimentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IPO Phase 1 (pre-listing) scoring — HFT_ARCHITECTURE.md §22.2.
 * Produces the apply/avoid subscription call. Phase 2 (post-listing hold/sell) is
 * {@link IPOLifecycleScorer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IPOAnalysisService {

    private static final double NEUTRAL_SCORE = 50.0;
    // "high" demand threshold for the RISKY hype-pop override (§22.2) — not numerically
    // specified in the doc; interpreted as the upper third of the 0-100 DemandScore range.
    private static final double HIGH_DEMAND_THRESHOLD = 60.0;
    // Corrected from the doc's original "valuationScore < 30" (unreachable — computeValuationScore's
    // true floor is exactly 30) to a threshold actually inside that formula's [30,100] range.
    private static final double RICH_VALUATION_THRESHOLD = 40.0;

    private final IPODataRepository ipoRepo;
    private final SentimentAnalysisService sentimentService;
    private final MacroGeopoliticalService macroService;

    // ─── Scheduled re-scoring (§22.2: @Scheduled, not Kafka — IPO data isn't HFT-speed) ──────

    @Scheduled(cron = "${hft.scheduler.ipo-open-rescore-cron:0 */15 * * * *}")
    public void rescoreOpenIpos() {
        LocalDate today = LocalDate.now();
        ipoRepo.findByStatus("OPEN").stream()
                .filter(ipo -> withinSubscriptionWindow(ipo, today))
                .forEach(this::scoreAndSave);
    }

    @Scheduled(cron = "${hft.scheduler.ipo-daily-rescore-cron:0 0 8 * * *}")
    public void rescoreUpcomingIpos() {
        ipoRepo.findByStatus("UPCOMING").forEach(this::scoreAndSave);
    }

    private boolean withinSubscriptionWindow(IPOData ipo, LocalDate today) {
        return ipo.getSubscriptionOpenDate() != null && ipo.getSubscriptionCloseDate() != null
                && !today.isBefore(ipo.getSubscriptionOpenDate())
                && !today.isAfter(ipo.getSubscriptionCloseDate());
    }

    // ─── Core scoring ───────────────────────────────────────────────────────────────────────

    public IPOData scoreAndSave(IPOData ipo) {
        double valuationScore = computeValuationScore(ipo);
        double demandScore = computeDemandScore(ipo);
        double qualityScore = computeQualityScore(ipo);
        double sentimentScore = computeSentimentScore(ipo);

        double compositeScore = 0.25 * valuationScore + 0.35 * demandScore
                + 0.20 * sentimentScore + 0.20 * qualityScore;

        double gmp = orZero(ipo.getGmpPercent());
        double predictedGain = gmp * 0.75 + (compositeScore - NEUTRAL_SCORE) * 0.3;

        String recommendation = deriveRecommendation(ipo, compositeScore, predictedGain, valuationScore, demandScore);
        String reason = buildReason(recommendation, compositeScore, valuationScore, demandScore, sentimentScore, qualityScore, ipo);

        ipo.setPredictedListingGainPercent(round(predictedGain));
        // listingGainConfidence isn't formula-specified in §22.2 beyond "0-100 confidence" —
        // using the composite score itself as the confidence proxy (higher composite = more
        // corroborating factors agree = more confidence in the prediction).
        ipo.setListingGainConfidence(round(clamp(compositeScore, 0, 100)));
        ipo.setRecommendation(recommendation);
        ipo.setRecommendationReason(reason);
        ipo.setLastUpdated(LocalDateTime.now());

        IPOData saved = ipoRepo.save(ipo);
        log.info("[IPO] Scored {}: composite={} demand={} valuation={} sentiment={} quality={} -> {}",
                ipo.getSymbol(), round(compositeScore), round(demandScore), round(valuationScore),
                round(sentimentScore), round(qualityScore), recommendation);
        return saved;
    }

    // ─── §22.2 formulas ─────────────────────────────────────────────────────────────────────

    double computeValuationScore(IPOData ipo) {
        Double pe = ipo.getPeAtIssuePrice();
        Double industryPe = ipo.getIndustryPeAvg();
        if (pe == null || industryPe == null || industryPe == 0) return NEUTRAL_SCORE;
        double ratio = (pe / industryPe - 1) * 120;
        // §22.2's raw formula (100 - clamp(ratio,-30,70)) can exceed 100 on a very cheap
        // valuation (ratio floors at -30 -> 130) — outer-clamped to keep this consistent with
        // the other three 0-100-scale composite inputs. The formula's own floor is exactly 30
        // (ratio capped at 70) — a richly-valued IPO can approach 30 but never go below it, so
        // the RISKY "hype-pop" override below uses RICH_VALUATION_THRESHOLD=40 (within this
        // formula's true [30,100] range), not the doc's original "<30" which was unreachable.
        // See HFT_ARCHITECTURE.md §22.2 changelog note for the corrected threshold.
        return clamp(100 - clamp(ratio, -30, 70), 0, 100);
    }

    double computeDemandScore(IPOData ipo) {
        // Additive, bounded-bonus formula — null inputs contribute 0 (no demand signal yet),
        // not a fabricated neutral midpoint, unlike QualityScore's explicit "default 50" rule.
        double gmpTerm = clamp(orZero(ipo.getGmpPercent()) * 1.8, 0, 60);
        double subscriptionTerm = clamp(orZero(ipo.getOverallSubscriptionTimes()) * 2, 0, 30);
        double qibTilt = (orZero(ipo.getQibSubscriptionTimes()) > orZero(ipo.getRetailSubscriptionTimes())) ? 10 : 0;
        return gmpTerm + subscriptionTerm + qibTilt;
    }

    double computeQualityScore(IPOData ipo) {
        if (ipo.getLeadManagerTrackRecordScore() == null) return NEUTRAL_SCORE;
        return clamp(ipo.getLeadManagerTrackRecordScore(), 0, 100);
    }

    double computeSentimentScore(IPOData ipo) {
        try {
            SentimentData sentiment = sentimentService.analyzeSentiment(ipo.getSymbol(), ipo.getMarket());
            if (sentiment == null || sentiment.getOverallSentimentScore() == null) return NEUTRAL_SCORE;
            // overallSentimentScore is on SentimentAnalysisService's native -1..+1 scale;
            // §22.2's CompositeScore formula assumes all four inputs share the 0-100 scale.
            return clamp((sentiment.getOverallSentimentScore() + 1) * 50, 0, 100);
        } catch (Exception e) {
            log.warn("[IPO] Sentiment lookup failed for {}: {}", ipo.getSymbol(), e.getMessage());
            return NEUTRAL_SCORE;
        }
    }

    // ─── Recommendation thresholds (§22.2) ─────────────────────────────────────────────────
    // The doc lists two "OR" override conditions (overvaluation-with-weak-demand -> AVOID,
    // hype-pop -> RISKY) alongside plain composite-score bands without specifying evaluation
    // order. Implemented here as: overrides checked first (they're meant to catch dangerous
    // patterns a merely-adequate composite score could otherwise mask), then score bands.

    String deriveRecommendation(IPOData ipo, double compositeScore, double predictedGain,
                                 double valuationScore, double demandScore) {
        Double pe = ipo.getPeAtIssuePrice();
        Double industryPe = ipo.getIndustryPeAvg();
        boolean richlyValued = pe != null && industryPe != null && industryPe > 0
                && pe > industryPe * 1.5;

        if (richlyValued && demandScore < 40) return "AVOID";
        if (demandScore >= HIGH_DEMAND_THRESHOLD && valuationScore < RICH_VALUATION_THRESHOLD) return "RISKY";

        if (compositeScore >= 75 && predictedGain >= 15) return "APPLY_STRONG";
        if (compositeScore >= 60) return "APPLY";
        if (compositeScore >= 40) return "RISKY";
        return "AVOID";
    }

    private String buildReason(String recommendation, double composite, double valuation,
                                double demand, double sentiment, double quality, IPOData ipo) {
        MacroData macro = safeGetMacro(ipo);
        String macroNote = macro != null && macro.getMacroSentiment() != null
                ? String.format(", macro %s", macro.getMacroSentiment()) : "";
        return String.format(
                "%s — composite %.1f/100 (valuation %.1f, demand %.1f, sentiment %.1f, quality %.1f%s)",
                recommendation, composite, valuation, demand, sentiment, quality, macroNote);
    }

    private MacroData safeGetMacro(IPOData ipo) {
        try {
            return macroService.getMacroData(ipo.getMarket());
        } catch (Exception e) {
            log.debug("[IPO] Macro context unavailable for {}: {}", ipo.getSymbol(), e.getMessage());
            return null;
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────────────────

    private static double orZero(Double v) { return v != null ? v : 0.0; }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
