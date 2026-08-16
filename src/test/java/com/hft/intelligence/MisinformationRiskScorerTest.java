package com.hft.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MisinformationRiskScorer — ASRB §4.2 Step 2")
class MisinformationRiskScorerTest {

    private static double[] ctx() { return new double[SourceSignal.CONTEXT_DIM]; }

    private MisinformationRiskScorer scorer() {
        // rhoThreshold, w1, w2, w3, v0, tauRisk, tauVelocity, riskAversion
        return new MisinformationRiskScorer(0.5, 1.5, 1.0, 0.8, 1.0, 0.7, 2.0, 1.0);
    }

    @Test
    @DisplayName("High velocity + low corroboration + low credibility -> high risk, flag fires")
    void highRiskScenarioFlagged() {
        MisinformationRiskScorer scorer = scorer();
        for (int i = 0; i < 10; i++) scorer.recordMentionCount("rumorClaim", 2); // stable low baseline
        double velocityZ = scorer.velocityZScore("rumorClaim", 40);              // sudden spike

        double risk = scorer.risk(/*credibility*/ 0.1, velocityZ, /*corroboration*/ 0);

        assertThat(velocityZ).as("40 mentions vs a baseline of 2 should register as a strong spike").isGreaterThan(3.0);
        assertThat(risk).as("low credibility + spike + no corroboration should score as high risk").isGreaterThan(0.7);
        assertThat(scorer.narrativeRiskFlag(risk, velocityZ)).isTrue();
    }

    @Test
    @DisplayName("Low velocity + strong independent corroboration + high credibility -> low risk, no flag")
    void lowRiskScenarioNotFlagged() {
        MisinformationRiskScorer scorer = scorer();
        for (int i = 0; i < 10; i++) scorer.recordMentionCount("steadyClaim", 5);
        double velocityZ = scorer.velocityZScore("steadyClaim", 5); // right at baseline

        double risk = scorer.risk(/*credibility*/ 0.9, velocityZ, /*corroboration*/ 4);

        assertThat(risk).isLessThan(0.3);
        assertThat(scorer.narrativeRiskFlag(risk, velocityZ)).isFalse();
    }

    @Test
    @DisplayName("Corroboration from highly-correlated sources does not count as independent confirmation")
    void correlatedReportsDoNotCountAsCorroboration() {
        CorrelationTracker tracker = new CorrelationTracker(0.95, 0.8);
        Random rnd = new Random(99);
        // Build D and E as correlated (same wire story reprinted); F independent.
        for (int t = 0; t < 80; t++) {
            double base = 50 + 30 * Math.sin(t * 0.3);
            tracker.recordPass(List.of(
                    new SourceSignal("D", clamp(base + rnd.nextGaussian() * 2), "c", Instant.now(), ctx()),
                    new SourceSignal("E", clamp(base + rnd.nextGaussian() * 2), "c", Instant.now(), ctx()),
                    new SourceSignal("F", clamp(50 + rnd.nextGaussian() * 15), "c", Instant.now(), ctx())
            ));
        }
        assertThat(tracker.correlation("D", "E")).as("precondition: D/E must actually be correlated for this test to mean anything")
                .isGreaterThan(0.5);
        assertThat(tracker.correlation("D", "F")).as("precondition: F must be independent of D")
                .isLessThan(0.3);

        MisinformationRiskScorer scorer = scorer();
        List<SourceSignal> claimReports = List.of(
                new SourceSignal("D", 20, "sharedRumor", Instant.now(), ctx()),
                new SourceSignal("E", 22, "sharedRumor", Instant.now(), ctx()) // D's correlated duplicate
        );
        int corroborationWithoutIndependentSource = scorer.corroborationCount("D", "sharedRumor", claimReports, tracker);
        assertThat(corroborationWithoutIndependentSource)
                .as("E is just a correlated re-report of D, must not count as independent corroboration")
                .isEqualTo(0);

        List<SourceSignal> withIndependentSource = List.of(
                new SourceSignal("D", 20, "sharedRumor", Instant.now(), ctx()),
                new SourceSignal("E", 22, "sharedRumor", Instant.now(), ctx()),
                new SourceSignal("F", 21, "sharedRumor", Instant.now(), ctx())  // genuinely independent
        );
        int corroborationWithIndependentSource = scorer.corroborationCount("D", "sharedRumor", withIndependentSource, tracker);
        assertThat(corroborationWithIndependentSource)
                .as("F is genuinely independent of D and must count")
                .isEqualTo(1);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
