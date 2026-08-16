package com.hft.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CorrelationTracker — ASRB §4.2 Step 1")
class CorrelationTrackerTest {

    private static double[] ctx() { return new double[SourceSignal.CONTEXT_DIM]; }

    @Test
    @DisplayName("Two sources built from the same base signal correlate highly; an independent third does not")
    void correlatedSourcesDetected() {
        CorrelationTracker tracker = new CorrelationTracker(0.95, 0.8);
        Random rnd = new Random(42);

        for (int t = 0; t < 80; t++) {
            double base = 50 + 30 * Math.sin(t * 0.3);
            double a = base + rnd.nextGaussian() * 2;
            double b = base + rnd.nextGaussian() * 2;         // correlated with A via shared base
            double c = 50 + rnd.nextGaussian() * 15;           // independent of base entirely

            tracker.recordPass(List.of(
                    new SourceSignal("A", clamp(a), "c", Instant.now(), ctx()),
                    new SourceSignal("B", clamp(b), "c", Instant.now(), ctx()),
                    new SourceSignal("C", clamp(c), "c", Instant.now(), ctx())
            ));
        }

        double rhoAB = tracker.correlation("A", "B");
        double rhoAC = tracker.correlation("A", "C");

        assertThat(rhoAB).as("A/B share a base signal, should correlate strongly").isGreaterThan(0.5);
        assertThat(rhoAC).as("A/C are independent, should not correlate strongly").isLessThan(0.3);
    }

    @Test
    @DisplayName("discountWeight penalizes a source more when a highly-correlated source was already counted")
    void discountFavorsIndependentEvidence() {
        CorrelationTracker tracker = new CorrelationTracker(0.95, 0.8);
        Random rnd = new Random(7);

        for (int t = 0; t < 80; t++) {
            double base = 50 + 30 * Math.sin(t * 0.3);
            double a = base + rnd.nextGaussian() * 2;
            double b = base + rnd.nextGaussian() * 2;
            double c = 50 + rnd.nextGaussian() * 15;
            tracker.recordPass(List.of(
                    new SourceSignal("A", clamp(a), "c", Instant.now(), ctx()),
                    new SourceSignal("B", clamp(b), "c", Instant.now(), ctx()),
                    new SourceSignal("C", clamp(c), "c", Instant.now(), ctx())
            ));
        }

        Set<String> alreadyCounted = new LinkedHashSet<>(List.of("A"));
        double weightForCorrelatedB = tracker.discountWeight("B", 1.0, alreadyCounted);
        double weightForIndependentC = tracker.discountWeight("C", 1.0, alreadyCounted);

        assertThat(weightForCorrelatedB)
                .as("B duplicates A's information and should be discounted more than independent C")
                .isLessThan(weightForIndependentC);
    }

    @Test
    @DisplayName("Correlation defaults to 0 (no discount) before enough co-occurrences are observed")
    void insufficientDataYieldsNoDiscount() {
        CorrelationTracker tracker = new CorrelationTracker(0.95, 0.8);
        tracker.recordPass(List.of(
                new SourceSignal("A", 70, "c", Instant.now(), ctx()),
                new SourceSignal("B", 70, "c", Instant.now(), ctx())
        ));
        assertThat(tracker.correlation("A", "B")).isEqualTo(0.0);
        assertThat(tracker.discountWeight("B", 1.0, Set.of("A"))).isEqualTo(1.0);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
