package com.hft.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SourceReliabilityPosterior — ASRB §4.2 Step 3")
class SourceReliabilityPosteriorTest {

    private static final int DIM = SourceSignal.CONTEXT_DIM;

    @Test
    @DisplayName("Converges to a known sparse linear relationship as evidence accumulates")
    void convergesToKnownRelationship() {
        Random rnd = new Random(123);
        double[] trueTheta = new double[DIM];
        trueTheta[0] = 2.0;
        trueTheta[5] = -1.5;
        trueTheta[20] = 0.8;

        SourceReliabilityPosterior posterior = new SourceReliabilityPosterior(DIM, 1.0, 0.1);

        for (int i = 0; i < 800; i++) {
            double[] x = randomContext(rnd);
            double y = dot(trueTheta, x) + rnd.nextGaussian() * 0.1;
            posterior.update(x, y, 1.0);
        }

        // Predictive test on fresh, unseen contexts — robust to any weakly-constrained directions.
        double totalAbsError = 0;
        int n = 50;
        for (int i = 0; i < n; i++) {
            double[] x = randomContext(rnd);
            double trueY = dot(trueTheta, x);
            double predicted = posterior.predict(x);
            totalAbsError += Math.abs(trueY - predicted);
        }
        double meanAbsError = totalAbsError / n;
        assertThat(meanAbsError).as("prediction on held-out contexts should track the true relationship")
                .isLessThan(0.5);
    }

    @Test
    @DisplayName("lambda_time < 1 makes the posterior forget an earlier relationship in favor of a newer one")
    void discountedPosteriorTracksRegimeChange() {
        Random rnd = new Random(456);
        double[] thetaPhase1 = new double[DIM];
        thetaPhase1[0] = 3.0;
        double[] thetaPhase2 = new double[DIM];
        thetaPhase2[0] = -3.0; // sharply different regime

        SourceReliabilityPosterior discounted = new SourceReliabilityPosterior(DIM, 0.90, 0.1);
        SourceReliabilityPosterior undiscounted = new SourceReliabilityPosterior(DIM, 1.0, 0.1);

        for (int i = 0; i < 300; i++) {
            double[] x = randomContext(rnd);
            double y = dot(thetaPhase1, x) + rnd.nextGaussian() * 0.1;
            discounted.update(x, y, 1.0);
            undiscounted.update(x, y, 1.0);
        }
        for (int i = 0; i < 300; i++) {
            double[] x = randomContext(rnd);
            double y = dot(thetaPhase2, x) + rnd.nextGaussian() * 0.1;
            discounted.update(x, y, 1.0);
            undiscounted.update(x, y, 1.0);
        }

        double[] probe = randomContext(rnd);
        double truePhase2Y = dot(thetaPhase2, probe);
        double discountedError = Math.abs(discounted.predict(probe) - truePhase2Y);
        double undiscountedError = Math.abs(undiscounted.predict(probe) - truePhase2Y);

        assertThat(discountedError)
                .as("discounted posterior should track the NEW regime much better than the undiscounted one, " +
                    "which stays anchored to the average of both phases")
                .isLessThan(undiscountedError);
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
