package com.hft.intelligence;

import org.apache.commons.math3.linear.*;

/**
 * ASRB §4.2 Step 3 — discounted Bayesian-linear reliability posterior for one source, over the
 * 41-dim context space. Tracked in information form (precision Λ, information vector b = Λθ) so
 * the recursive update is a simple weighted accumulation; θ is recovered on demand via Λ⁻¹b.
 *
 * λ_time < 1 makes this non-stationary-aware: existing sufficient statistics are discounted
 * before each update, so old evidence is forgotten rather than accumulating forever.
 *
 * Also tracks CR_i(t), the scalar "posterior mean reliability" §4.2 Step 2 reuses directly —
 * design choice, documented since the disclosure doc doesn't pin down its exact form: a
 * lambda_time-discounted running mean of the realized outcome labels themselves (same discount
 * rate as the linear posterior, updated in the same call), NOT a context-conditional prediction
 * at some arbitrary reference point. This keeps CR simple, well-defined, and testable, and it
 * shares the update's effective weight and discount consistently with (Λ, θ).
 */
public class SourceReliabilityPosterior {

    private final int dim;
    private final double lambdaTime;

    private RealMatrix precision;          // Λ
    private RealVector information;        // b = Λθ

    private double reliabilityNumerator = 0.0;   // discounted Σ weight·outcome
    private double reliabilityDenominator = 0.0; // discounted Σ weight — also serves as effective sample count

    public SourceReliabilityPosterior(int dim, double lambdaTime, double priorPrecision) {
        if (lambdaTime <= 0 || lambdaTime > 1) throw new IllegalArgumentException("lambdaTime must be in (0,1]");
        this.dim = dim;
        this.lambdaTime = lambdaTime;
        this.precision = MatrixUtils.createRealIdentityMatrix(dim).scalarMultiply(priorPrecision);
        this.information = new ArrayRealVector(dim);
    }

    /** context: 41-dim feature vector. outcome: realized-outcome label (e.g. 0/1 or continuous return-derived score). */
    public void update(double[] context, double outcome, double effectiveWeight) {
        if (context.length != dim) throw new IllegalArgumentException("context dim mismatch");
        RealVector x = new ArrayRealVector(context);

        precision = precision.scalarMultiply(lambdaTime)
                .add(x.outerProduct(x).scalarMultiply(effectiveWeight));
        information = information.mapMultiply(lambdaTime)
                .add(x.mapMultiply(effectiveWeight * outcome));

        reliabilityNumerator = lambdaTime * reliabilityNumerator + effectiveWeight * outcome;
        reliabilityDenominator = lambdaTime * reliabilityDenominator + effectiveWeight;
    }

    public double[] getTheta() {
        return getPrecisionInverse().operate(information).toArray();
    }

    public RealMatrix getPrecisionInverse() {
        return new LUDecomposition(precision).getSolver().getInverse();
    }

    /** tr(Λ⁻¹) — posterior variance/uncertainty proxy, ASRB §4.2 Step 4. */
    public double getPrecisionInverseTrace() {
        return getPrecisionInverse().getTrace();
    }

    public double predict(double[] context) {
        double[] theta = getTheta();
        double sum = 0;
        for (int k = 0; k < dim; k++) sum += theta[k] * context[k];
        return sum;
    }

    /** CR_i(t) — see class javadoc for the design choice. 0.5 (neutral) before any evidence. */
    public double posteriorMeanReliability() {
        if (reliabilityDenominator < 1e-9) return 0.5;
        return Math.max(0.0, Math.min(1.0, reliabilityNumerator / reliabilityDenominator));
    }

    /** Discounted effective sample count — used as the Beta moment-match pseudo-count in Step 5. */
    public double effectiveSampleCount() {
        return reliabilityDenominator;
    }
}
