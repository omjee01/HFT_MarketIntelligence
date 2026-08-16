package com.hft.intelligence;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end test justifying ASRB's existence: a mix of reliable, unreliable, correlated, and
 * injected-misinformation sources should fuse to a composite score CLOSER to the synthetic
 * ground truth than a naive equal-weight average of the same raw scores would produce.
 */
@DisplayName("AdaptiveSourceReliabilityBandit — end-to-end (ASRB_TECHNICAL_DISCLOSURE.md §4.2)")
class AdaptiveSourceReliabilityBanditTest {

    private static final int DIM = SourceSignal.CONTEXT_DIM;
    private static final double TRUE_SCORE = 80.0;

    @Test
    @DisplayName("Composite score beats a naive equal-weight average when the input mix includes an injected misinformation claim")
    void compositeScoreOutperformsNaiveAverage() {
        Random rnd = new Random(2026);

        CorrelationTracker correlationTracker = new CorrelationTracker(0.95, 0.8);
        MisinformationRiskScorer misinfoScorer = new MisinformationRiskScorer(0.5, 1.5, 1.0, 0.8, 1.0, 0.6, 1.5, 1.0);
        StabilityIndexCalculator stabilityCalculator = new StabilityIndexCalculator();
        PolicySelector policySelector = new PolicySelector(0.5, 0.5, new JDKRandomGenerator(2026));
        AdaptiveSourceReliabilityBandit bandit = new AdaptiveSourceReliabilityBandit(
                correlationTracker, misinfoScorer, stabilityCalculator, policySelector, 0.97, 0.1);

        // Establish a low, boring baseline for the claim cluster the misinformation will later spike.
        for (int i = 0; i < 10; i++) misinfoScorer.recordMentionCount("misinfoClaim", 0);

        // Training phase: build genuine track records via the real pipeline, not by poking internals.
        for (int pass = 0; pass < 40; pass++) {
            double baseA = 78 + rnd.nextGaussian() * 2;
            double baseM = 15 + rnd.nextGaussian() * 2;

            List<SourceSignal> signals = List.of(
                    new SourceSignal("reliableA", clamp(baseA), "claimA_train" + pass, Instant.now(), randomContext(rnd)),
                    new SourceSignal("correlatedCopyOfA", clamp(baseA + rnd.nextGaussian()), "claimACopy_train" + pass, Instant.now(), randomContext(rnd)),
                    new SourceSignal("unreliableM", clamp(baseM), "claimM_train" + pass, Instant.now(), randomContext(rnd)),
                    new SourceSignal("correlatedCopyOfM", clamp(baseM + rnd.nextGaussian()), "claimMCopy_train" + pass, Instant.now(), randomContext(rnd))
            );
            Map<String, Double> outcomes = Map.of(
                    "reliableA", 1.0, "correlatedCopyOfA", 1.0,
                    "unreliableM", 0.0, "correlatedCopyOfM", 0.0
            );
            bandit.aggregate(signals, outcomes);
        }

        // Final pass: reliable + brand-new independent + a correlated misinformation burst.
        List<SourceSignal> finalPass = List.of(
                new SourceSignal("reliableA", 78, "claimA_final", Instant.now(), randomContext(rnd)),
                new SourceSignal("newIndependent", 82, "claimIndependent_final", Instant.now(), randomContext(rnd)),
                new SourceSignal("unreliableM", 15, "misinfoClaim", Instant.now(), randomContext(rnd)),
                new SourceSignal("correlatedCopyOfM", 18, "misinfoClaim", Instant.now(), randomContext(rnd))
        );
        double naiveAverage = finalPass.stream().mapToDouble(SourceSignal::score).average().orElseThrow();

        AdaptiveSourceReliabilityBandit.CompositeScore result = bandit.aggregate(finalPass, Map.of());

        assertThat(Math.abs(result.score() - TRUE_SCORE))
                .as("ASRB composite (%.2f) should land closer to ground truth (%.0f) than the naive average (%.2f) does",
                        result.score(), TRUE_SCORE, naiveAverage)
                .isLessThan(Math.abs(naiveAverage - TRUE_SCORE));

        assertThat(result.riskAlerts())
                .as("the correlated, low-corroboration burst on misinfoClaim should raise a narrative-risk alert")
                .anyMatch(alert -> alert.claimClusterId().equals("misinfoClaim"));
    }

    private static double[] randomContext(Random rnd) {
        double[] x = new double[DIM];
        for (int k = 0; k < DIM; k++) x[k] = rnd.nextGaussian() * 0.3;
        return x;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
