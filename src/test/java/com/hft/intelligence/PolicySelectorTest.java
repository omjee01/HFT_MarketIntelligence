package com.hft.intelligence;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PolicySelector — ASRB §4.2 Step 5")
class PolicySelectorTest {

    private static final int DIM = SourceSignal.CONTEXT_DIM;

    @Test
    @DisplayName("Stable sources (>= tau) get a deterministic, index-based weight")
    void stableSourceIsDeterministic() {
        SourceReliabilityPosterior posterior = new SourceReliabilityPosterior(DIM, 1.0, 0.1);
        Random rnd = new Random(1);
        for (int i = 0; i < 300; i++) {
            double[] x = randomContext(rnd);
            posterior.update(x, 0.8, 1.0); // consistently "mostly correct" outcome label
        }

        PolicySelector selector = new PolicySelector(0.5, 0.5);
        PolicySelector.PolicyDecision first = selector.select(posterior, 0.9); // above tau

        for (int i = 0; i < 10; i++) {
            PolicySelector.PolicyDecision again = selector.select(posterior, 0.9);
            assertThat(again.usedGittins()).isTrue();
            assertThat(again.weight()).as("Gittins branch must be deterministic for unchanged posterior state")
                    .isEqualTo(first.weight());
        }
    }

    @Test
    @DisplayName("Sparse/new sources (< tau) get a genuinely stochastic Thompson-sampled weight")
    void sparseSourceIsStochastic() {
        SourceReliabilityPosterior posterior = new SourceReliabilityPosterior(DIM, 1.0, 0.1); // untouched: n≈0
        PolicySelector selector = new PolicySelector(0.5, 0.5, new JDKRandomGenerator(123));

        Set<Double> distinctWeights = new HashSet<>();
        boolean anyGittins = false;
        double sum = 0, sumSq = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            PolicySelector.PolicyDecision d = selector.select(posterior, 0.1); // below tau
            anyGittins |= d.usedGittins();
            distinctWeights.add(d.weight());
            sum += d.weight();
            sumSq += d.weight() * d.weight();
        }
        double mean = sum / trials;
        double variance = sumSq / trials - mean * mean;

        assertThat(anyGittins).as("below tau must never use the Gittins branch").isFalse();
        assertThat(distinctWeights.size())
                .as("Thompson sampling must not silently collapse into a constant return value")
                .isGreaterThan(trials / 2);
        assertThat(variance).as("repeated draws from the same posterior must show real variance")
                .isGreaterThan(1e-6);
    }

    private static double[] randomContext(Random rnd) {
        double[] x = new double[DIM];
        for (int k = 0; k < DIM; k++) x[k] = rnd.nextGaussian();
        return x;
    }
}
