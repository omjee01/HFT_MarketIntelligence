package com.hft.intelligence;

import java.util.*;

/**
 * ASRB §4.2 Step 4 — population-relative stability index. The novel part per §5(A): the
 * exploit/explore gate in Step 5 is keyed off S_i(t), a PERCENTILE RANK among the current
 * cross-source population, not a fixed threshold on the raw value — so it self-calibrates
 * across regimes instead of a fixed cutoff suddenly classifying every source as "unstable"
 * during a broadly chaotic period.
 */
public class StabilityIndexCalculator {

    private final Map<String, double[]> thetaSnapshots = new HashMap<>();

    /** raw_stability_i(t) = 1 / (1 + drift + var). Call snapshot(sourceId, posterior) periodically
     *  to establish θ(t−Δ); before any snapshot exists, drift is treated as 0 (no history yet). */
    public double rawStability(String sourceId, SourceReliabilityPosterior posterior) {
        double[] currentTheta = posterior.getTheta();
        double[] previousTheta = thetaSnapshots.get(sourceId);
        double drift = previousTheta == null ? 0.0 : euclideanDistance(currentTheta, previousTheta);
        double variance = posterior.getPrecisionInverseTrace();
        return 1.0 / (1.0 + drift + variance);
    }

    public void snapshot(String sourceId, SourceReliabilityPosterior posterior) {
        thetaSnapshots.put(sourceId, posterior.getTheta());
    }

    /** S_i(t) — percentile rank of each source's raw stability among the given population, in [0,1]. */
    public Map<String, Double> populationRelativeRank(Map<String, Double> rawStabilityBySource) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rawStabilityBySource.entrySet());
        sorted.sort(Comparator.comparingDouble(Map.Entry::getValue));
        int n = sorted.size();
        Map<String, Double> ranks = new HashMap<>();
        if (n == 1) {
            ranks.put(sorted.get(0).getKey(), 1.0);
            return ranks;
        }
        for (int idx = 0; idx < n; idx++) {
            // fraction of the population this source is >= to; highest raw value -> rank 1.0
            ranks.put(sorted.get(idx).getKey(), n == 1 ? 1.0 : (double) idx / (n - 1));
        }
        return ranks;
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int k = 0; k < a.length; k++) {
            double d = a[k] - b[k];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
