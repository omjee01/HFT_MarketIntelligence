package com.hft.intelligence;

import java.util.*;

/**
 * ASRB §4.2 Step 2 — misinformation-risk-discounted evidence weighting, with dual-use
 * narrative-risk flagging (discount as a truth signal, flag separately as a real business risk
 * regardless of truth value — see ASRB_TECHNICAL_DISCLOSURE.md §6, the Snapdeal worked example).
 */
public class MisinformationRiskScorer {

    private final double rhoThreshold;   // below this, a co-reporting source counts as independent
    private final double w1, w2, w3;
    private final double v0;
    private final double tauRisk;
    private final double tauVelocity;
    private final double riskAversion;

    private final Map<String, List<Integer>> mentionHistory = new HashMap<>();

    public MisinformationRiskScorer(double rhoThreshold, double w1, double w2, double w3, double v0,
                                     double tauRisk, double tauVelocity, double riskAversion) {
        this.rhoThreshold = rhoThreshold;
        this.w1 = w1; this.w2 = w2; this.w3 = w3;
        this.v0 = v0;
        this.tauRisk = tauRisk;
        this.tauVelocity = tauVelocity;
        this.riskAversion = riskAversion;
    }

    /**
     * κ_corr(c_i(t)) — count of OTHER sources reporting the same claim cluster this pass whose
     * correlation with sourceId is below rhoThreshold. Per-(source, claim) as specified in §4.2
     * Step 2, not a claim-level aggregate: two sources can each see a different corroboration
     * count for the same claim, depending on their own correlation profile with the reporters.
     */
    public int corroborationCount(String sourceId, String claimClusterId,
                                   List<SourceSignal> signalsThisPass, CorrelationTracker tracker) {
        Set<String> independentReporters = new HashSet<>();
        for (SourceSignal s : signalsThisPass) {
            if (!s.claimClusterId().equals(claimClusterId)) continue;
            if (s.sourceId().equals(sourceId)) continue;
            if (tracker.correlation(sourceId, s.sourceId()) < rhoThreshold) {
                independentReporters.add(s.sourceId());
            }
        }
        return independentReporters.size();
    }

    /** Z-score of this pass's mention count for claimClusterId against its own recorded history. */
    public double velocityZScore(String claimClusterId, int currentMentionCount) {
        List<Integer> history = mentionHistory.getOrDefault(claimClusterId, List.of());
        if (history.size() < 3) return 0.0; // no baseline yet — don't flag on day one
        double mean = history.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = history.stream().mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
        double std = Math.sqrt(variance);
        if (std < 1e-6) return currentMentionCount > mean ? 5.0 : 0.0;
        return (currentMentionCount - mean) / std;
    }

    public void recordMentionCount(String claimClusterId, int mentionCount) {
        mentionHistory.computeIfAbsent(claimClusterId, k -> new ArrayList<>()).add(mentionCount);
    }

    /** MisinfoRisk_i(t) = σ(w1·(1−CR) + w2·max(0,V−v0) − w3·κ_corr). */
    public double risk(double sourceCredibility, double velocityZ, int corroborationCount) {
        double raw = w1 * (1 - sourceCredibility) + w2 * Math.max(0, velocityZ - v0) - w3 * corroborationCount;
        return 1.0 / (1.0 + Math.exp(-raw));
    }

    public double discountWeight(double effectiveWeight, double misinfoRisk) {
        return effectiveWeight * (1 - riskAversion * misinfoRisk);
    }

    /** Dual-use flag — independent of, and in addition to, the discount above. */
    public boolean narrativeRiskFlag(double misinfoRisk, double velocityZ) {
        return misinfoRisk > tauRisk && velocityZ > tauVelocity;
    }
}
