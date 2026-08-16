package com.hft.intelligence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASRB_TECHNICAL_DISCLOSURE.md §4.2 — orchestrates the six-step pipeline in the exact order
 * specified: correlation discount, then misinformation discount (both applied to evidence
 * BEFORE the posterior update — they control how much this pass's evidence moves the long-run
 * reliability estimate, not the final aggregation weight directly), then the posterior update,
 * then stability, then policy selection, then aggregation.
 *
 * Causal ordering, not spelled out explicitly in the doc but required to avoid lookahead bias:
 * this pass's discounts are computed from correlation/history state as of BEFORE this pass
 * (i.e. built from prior passes only); the tracker is updated with this pass's own data only
 * AFTER those discounts have been used, so a pass never uses its own not-yet-observed
 * correlation contribution to discount itself.
 *
 * outcomeLabel(sourceId, symbol) supplies y_i(t) for the Step 3 posterior update — in this
 * standalone module the caller provides it directly (tests use synthetic ground truth); wiring
 * this to the real recordSignalOutcome/BacktestTrade data is a deliberate later integration
 * step, not part of this module.
 */
public class AdaptiveSourceReliabilityBandit {

    public record NarrativeRiskAlert(String claimClusterId, double misinfoRisk, double velocityZ) {}

    public record CompositeScore(double score, Map<String, Double> policyWeightsUsed,
                                  Map<String, Double> effectiveWeightsUsed, List<NarrativeRiskAlert> riskAlerts) {}

    private final CorrelationTracker correlationTracker;
    private final MisinformationRiskScorer misinfoScorer;
    private final StabilityIndexCalculator stabilityCalculator;
    private final PolicySelector policySelector;
    private final double lambdaTime;
    private final double priorPrecision;

    private final Map<String, SourceReliabilityPosterior> posteriors = new ConcurrentHashMap<>();

    public AdaptiveSourceReliabilityBandit(CorrelationTracker correlationTracker,
                                            MisinformationRiskScorer misinfoScorer,
                                            StabilityIndexCalculator stabilityCalculator,
                                            PolicySelector policySelector,
                                            double lambdaTime,
                                            double priorPrecision) {
        this.correlationTracker = correlationTracker;
        this.misinfoScorer = misinfoScorer;
        this.stabilityCalculator = stabilityCalculator;
        this.policySelector = policySelector;
        this.lambdaTime = lambdaTime;
        this.priorPrecision = priorPrecision;
    }

    /**
     * @param signalsThisPass  raw evidence for this scoring pass, one per source
     * @param outcomeLabels    y_i(t) per sourceId — realized-outcome label driving the Step 3
     *                         posterior update (synthetic ground truth in tests; real deployments
     *                         source this from recordSignalOutcome/BacktestTrade, not built here)
     */
    public CompositeScore aggregate(List<SourceSignal> signalsThisPass, Map<String, Double> outcomeLabels) {
        Map<String, Integer> mentionCountThisPass = new HashMap<>();
        for (SourceSignal s : signalsThisPass) {
            mentionCountThisPass.merge(s.claimClusterId(), 1, Integer::sum);
        }

        // Steps 1-2: discount, using tracker/history state as of BEFORE this pass.
        Set<String> countedSoFar = new LinkedHashSet<>();
        Map<String, Double> effectiveWeights = new LinkedHashMap<>();
        List<NarrativeRiskAlert> alerts = new ArrayList<>();

        for (SourceSignal s : signalsThisPass) {
            double rawWeight = 1.0;
            double correlationDiscounted = correlationTracker.discountWeight(s.sourceId(), rawWeight, countedSoFar);

            SourceReliabilityPosterior posterior = posteriors.computeIfAbsent(s.sourceId(),
                    k -> new SourceReliabilityPosterior(SourceSignal.CONTEXT_DIM, lambdaTime, priorPrecision));
            double credibility = posterior.posteriorMeanReliability();

            int corroboration = misinfoScorer.corroborationCount(s.sourceId(), s.claimClusterId(), signalsThisPass, correlationTracker);
            double velocityZ = misinfoScorer.velocityZScore(s.claimClusterId(),
                    mentionCountThisPass.getOrDefault(s.claimClusterId(), 0));
            double misinfoRisk = misinfoScorer.risk(credibility, velocityZ, corroboration);
            double effectiveWeight = misinfoScorer.discountWeight(correlationDiscounted, misinfoRisk);

            if (misinfoScorer.narrativeRiskFlag(misinfoRisk, velocityZ)) {
                alerts.add(new NarrativeRiskAlert(s.claimClusterId(), misinfoRisk, velocityZ));
            }

            effectiveWeights.put(s.sourceId(), effectiveWeight);
            countedSoFar.add(s.sourceId());
        }

        // Step 3: posterior update, using each source's effective weight and supplied outcome label.
        for (SourceSignal s : signalsThisPass) {
            Double outcome = outcomeLabels.get(s.sourceId());
            if (outcome == null) continue; // no outcome yet for this source this pass — evidence still counts in steps 1-2/5-6, but doesn't move the posterior
            posteriors.get(s.sourceId()).update(s.context(), outcome, effectiveWeights.get(s.sourceId()));
        }

        // Step 4: stability, computed from the just-updated posteriors.
        Map<String, Double> rawStability = new LinkedHashMap<>();
        for (SourceSignal s : signalsThisPass) {
            rawStability.put(s.sourceId(), stabilityCalculator.rawStability(s.sourceId(), posteriors.get(s.sourceId())));
        }
        Map<String, Double> populationRank = stabilityCalculator.populationRelativeRank(rawStability);

        // Step 5: policy selection.
        Map<String, Double> policyWeights = new LinkedHashMap<>();
        for (SourceSignal s : signalsThisPass) {
            PolicySelector.PolicyDecision decision = policySelector.select(
                    posteriors.get(s.sourceId()), populationRank.get(s.sourceId()));
            policyWeights.put(s.sourceId(), decision.weight());
        }

        // Step 6: aggregate raw scores s_i(t) weighted by policy_weight_i(t).
        double numerator = 0, denominator = 0;
        for (SourceSignal s : signalsThisPass) {
            double w = Math.max(policyWeights.get(s.sourceId()), 0);
            numerator += w * s.score();
            denominator += w;
        }
        double compositeScore = denominator > 1e-9 ? numerator / denominator : 50.0; // neutral fallback

        // Post-pass bookkeeping: update tracker/history/snapshots for FUTURE passes.
        correlationTracker.recordPass(signalsThisPass);
        mentionCountThisPass.forEach(misinfoScorer::recordMentionCount);
        for (SourceSignal s : signalsThisPass) {
            stabilityCalculator.snapshot(s.sourceId(), posteriors.get(s.sourceId()));
        }

        return new CompositeScore(compositeScore, policyWeights, effectiveWeights, alerts);
    }

    public SourceReliabilityPosterior posteriorFor(String sourceId) {
        return posteriors.get(sourceId);
    }
}
