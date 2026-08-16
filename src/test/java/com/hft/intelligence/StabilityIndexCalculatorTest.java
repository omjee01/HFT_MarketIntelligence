package com.hft.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StabilityIndexCalculator — ASRB §4.2 Step 4")
class StabilityIndexCalculatorTest {

    private static final int DIM = SourceSignal.CONTEXT_DIM;

    @Test
    @DisplayName("A converged source ranks more stable than a sparse/new one")
    void convergedSourceOutranksSparseSource() {
        Random rnd = new Random(1);
        double[] theta = fixedTheta();
        StabilityIndexCalculator calc = new StabilityIndexCalculator();

        SourceReliabilityPosterior converged = new SourceReliabilityPosterior(DIM, 1.0, 0.1);
        for (int i = 0; i < 500; i++) {
            double[] x = randomContext(rnd);
            converged.update(x, dot(theta, x) + rnd.nextGaussian() * 0.05, 1.0);
        }
        calc.snapshot("converged", converged);
        for (int i = 0; i < 10; i++) {
            double[] x = randomContext(rnd);
            converged.update(x, dot(theta, x) + rnd.nextGaussian() * 0.05, 1.0);
        }

        SourceReliabilityPosterior sparse = new SourceReliabilityPosterior(DIM, 1.0, 0.1);
        double[] x1 = randomContext(rnd);
        sparse.update(x1, dot(theta, x1), 1.0);
        calc.snapshot("sparse", sparse);
        double[] x2 = randomContext(rnd);
        sparse.update(x2, dot(theta, x2), 1.0);

        double convergedRaw = calc.rawStability("converged", converged);
        double sparseRaw = calc.rawStability("sparse", sparse);

        assertThat(convergedRaw).as("converged source should show low drift/variance -> high raw stability")
                .isGreaterThan(sparseRaw);

        Map<String, Double> ranks = calc.populationRelativeRank(Map.of("converged", convergedRaw, "sparse", sparseRaw));
        assertThat(ranks.get("converged")).isGreaterThan(ranks.get("sparse"));
    }

    @Test
    @DisplayName("When every source's raw stability drops together, relative ranking still discriminates")
    void populationRelativeRankSurvivesSharedRegimeShock() {
        Random rnd = new Random(2);
        double[] thetaBefore = fixedTheta();
        double[] thetaAfterShock = negate(thetaBefore);

        StabilityIndexCalculator calc = new StabilityIndexCalculator();

        SourceReliabilityPosterior moreConverged = new SourceReliabilityPosterior(DIM, 1.0, 0.1);
        SourceReliabilityPosterior lessConverged = new SourceReliabilityPosterior(DIM, 1.0, 0.1);
        for (int i = 0; i < 150; i++) {
            double[] x = randomContext(rnd);
            double y = dot(thetaBefore, x) + rnd.nextGaussian() * 0.05;
            moreConverged.update(x, y, 1.0);
            if (i < 40) lessConverged.update(x, y, 1.0);
        }
        calc.snapshot("more", moreConverged);
        calc.snapshot("less", lessConverged);

        // shared shock: both now see a sharply different relationship
        for (int i = 0; i < 15; i++) {
            double[] x = randomContext(rnd);
            double y = dot(thetaAfterShock, x) + rnd.nextGaussian() * 0.05;
            moreConverged.update(x, y, 1.0);
            lessConverged.update(x, y, 1.0);
        }

        double rawMore = calc.rawStability("more", moreConverged);
        double rawLess = calc.rawStability("less", lessConverged);
        Map<String, Double> ranks = calc.populationRelativeRank(Map.of("more", rawMore, "less", rawLess));

        assertThat(ranks.get("more")).as("both take a stability hit, but relative ranking must still separate them")
                .isNotEqualTo(ranks.get("less"));
        assertThat(ranks.get("more")).as("the more-converged source should still come out ahead in relative terms")
                .isGreaterThan(ranks.get("less"));
    }

    private static double[] fixedTheta() {
        double[] t = new double[DIM];
        t[0] = 2.0; t[10] = -1.0; t[30] = 0.5;
        return t;
    }

    private static double[] negate(double[] t) {
        double[] r = new double[t.length];
        for (int i = 0; i < t.length; i++) r[i] = -t[i] * 2;
        return r;
    }

    private static double[] randomContext(Random rnd) {
        double[] x = new double[DIM];
        for (int k = 0; k < DIM; k++) x[k] = rnd.nextGaussian();
        return x;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int k = 0; k < a.length; k++) s += a[k] * b[k];
        return s;
    }
}
