package com.hft.intelligence;

import java.util.*;

/**
 * ASRB §4.2 Step 1 — online exponentially-weighted inter-source correlation Ω(t), and the
 * correlation-discount formula: effective_weight = raw_weight × (1 − κ·max(0, max_j ρ_ij)).
 *
 * Correlation is estimated from standardized score residuals observed when two sources fire
 * in the SAME pass (recordPass). Per-source mean/variance and per-pair covariance are each
 * tracked as their own EWMA with decay lambdaCorr — a source's own mean/variance decay at the
 * same rate as its covariance with others, keeping the correlation estimate self-consistent.
 *
 * Below MIN_OBSERVATIONS co-occurrences for a pair, correlation is reported as 0 (no discount)
 * rather than an unstable early estimate.
 */
public class CorrelationTracker {

    private static final int MIN_OBSERVATIONS = 5;

    private final double lambdaCorr;   // decay factor, e.g. 0.97 — closer to 1 = longer memory
    private final double kappa;        // discount sensitivity, (0,1]

    private final Map<String, Double> meanEwma = new HashMap<>();
    private final Map<String, Double> varEwma = new HashMap<>();
    private final Map<String, Double> covEwma = new HashMap<>();
    private final Map<String, Integer> pairObservations = new HashMap<>();

    public CorrelationTracker(double lambdaCorr, double kappa) {
        if (lambdaCorr <= 0 || lambdaCorr > 1) throw new IllegalArgumentException("lambdaCorr must be in (0,1]");
        if (kappa <= 0 || kappa > 1) throw new IllegalArgumentException("kappa must be in (0,1]");
        this.lambdaCorr = lambdaCorr;
        this.kappa = kappa;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Update Ω(t) from one pass's signals. Sources present alone this pass update mean/var only. */
    public void recordPass(List<SourceSignal> signalsThisPass) {
        for (SourceSignal s : signalsThisPass) {
            double prevMean = meanEwma.getOrDefault(s.sourceId(), s.score());
            double newMean = lambdaCorr * prevMean + (1 - lambdaCorr) * s.score();
            double dev = s.score() - prevMean;
            double prevVar = varEwma.getOrDefault(s.sourceId(), 1.0);
            double newVar = lambdaCorr * prevVar + (1 - lambdaCorr) * dev * dev;
            meanEwma.put(s.sourceId(), newMean);
            varEwma.put(s.sourceId(), Math.max(newVar, 1e-9));
        }
        for (int i = 0; i < signalsThisPass.size(); i++) {
            for (int j = i + 1; j < signalsThisPass.size(); j++) {
                SourceSignal a = signalsThisPass.get(i);
                SourceSignal b = signalsThisPass.get(j);
                double devA = a.score() - meanEwma.get(a.sourceId());
                double devB = b.score() - meanEwma.get(b.sourceId());
                String key = pairKey(a.sourceId(), b.sourceId());
                double prevCov = covEwma.getOrDefault(key, 0.0);
                double newCov = lambdaCorr * prevCov + (1 - lambdaCorr) * devA * devB;
                covEwma.put(key, newCov);
                pairObservations.merge(key, 1, Integer::sum);
            }
        }
    }

    /** ρ_ij(t) — 0 if the pair hasn't co-occurred enough to estimate reliably. */
    public double correlation(String i, String j) {
        if (i.equals(j)) return 1.0;
        String key = pairKey(i, j);
        if (pairObservations.getOrDefault(key, 0) < MIN_OBSERVATIONS) return 0.0;
        Double varI = varEwma.get(i), varJ = varEwma.get(j), cov = covEwma.get(key);
        if (varI == null || varJ == null || cov == null) return 0.0;
        double denom = Math.sqrt(varI * varJ);
        if (denom < 1e-9) return 0.0;
        return Math.max(-1.0, Math.min(1.0, cov / denom));
    }

    /** ASRB §4.2 Step 1 formula, discounting against the single most-correlated already-counted source. */
    public double discountWeight(String sourceId, double rawWeight, Set<String> alreadyCountedThisPass) {
        double maxRho = 0.0;
        for (String other : alreadyCountedThisPass) {
            if (other.equals(sourceId)) continue;
            maxRho = Math.max(maxRho, correlation(sourceId, other));
        }
        double penalty = kappa * Math.max(0, maxRho);
        penalty = Math.min(penalty, 1 - 1e-6); // never fully zero out evidence via correlation alone
        return rawWeight * (1 - penalty);
    }
}
