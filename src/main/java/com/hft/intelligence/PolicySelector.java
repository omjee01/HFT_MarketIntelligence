package com.hft.intelligence;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

/**
 * ASRB §4.2 Step 5 — population-relative-stability-gated policy selection (the claimed novel
 * mechanism, §5(A)): stable sources are ranked by Gittins index (optimal once well-characterized
 * and near-stationary); new/sparse/drifting sources fall back to Thompson Sampling.
 *
 * Resolved ambiguity, documented here since the disclosure doc's literal Step 5 notation doesn't
 * type-check as written: it says the Thompson branch draws "from N(θ_i(t), Λ_i(t)⁻¹)" — a
 * 41-dimensional vector sample — but Step 6's aggregation formula needs a SCALAR policy_weight.
 * Resolution used here: both branches operate on the same moment-matched Beta(α,β) scalar
 * summary that Gittins needs anyway (mean = CR_i(t), pseudo-count = effective sample count,
 * Laplace-smoothed: α = mean·n + 1, β = (1−mean)·n + 1). The Gittins branch evaluates the
 * deterministic index formula on (α,β); the Thompson branch draws directly from Beta(α,β) —
 * this is the textbook form of Thompson Sampling for Bernoulli-shaped rewards, and it keeps the
 * two branches dimensionally consistent rather than requiring an arbitrary reference context to
 * project a 41-dim sample down to a scalar.
 *
 * Gittins index itself: UCB-style closed-form approximation (mean + gittinsZ·sqrt(variance) of
 * the moment-matched Beta), not an exact Gittins table lookup — a documented, deliberately
 * simple choice per ASRB_TECHNICAL_DISCLOSURE.md §10's allowance ("a reasonable closed-form...
 * approximation is fine").
 */
public class PolicySelector {

    public record PolicyDecision(double weight, boolean usedGittins) {}

    private final double tauStability;
    private final double gittinsZ;
    private final RandomGenerator random;

    public PolicySelector(double tauStability, double gittinsZ) {
        this(tauStability, gittinsZ, new JDKRandomGenerator());
    }

    public PolicySelector(double tauStability, double gittinsZ, RandomGenerator random) {
        this.tauStability = tauStability;
        this.gittinsZ = gittinsZ;
        this.random = random;
    }

    public PolicyDecision select(SourceReliabilityPosterior posterior, double populationRelativeStability) {
        double mean = posterior.posteriorMeanReliability();
        double n = posterior.effectiveSampleCount();
        double alpha = mean * n + 1;
        double beta = (1 - mean) * n + 1;

        if (populationRelativeStability >= tauStability) {
            return new PolicyDecision(gittinsIndex(alpha, beta), true);
        } else {
            BetaDistribution betaDist = new BetaDistribution(random, alpha, beta,
                    BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
            return new PolicyDecision(betaDist.sample(), false);
        }
    }

    private double gittinsIndex(double alpha, double beta) {
        double mean = alpha / (alpha + beta);
        double variance = (alpha * beta) / (Math.pow(alpha + beta, 2) * (alpha + beta + 1));
        return mean + gittinsZ * Math.sqrt(Math.max(variance, 0));
    }
}
