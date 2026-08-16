package com.hft.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the ASRB module (§4 of ASRB_TECHNICAL_DISCLOSURE.md) as Spring beans. The classes
 * themselves are deliberately plain POJOs with no Spring annotations (built and tested
 * standalone in Stage 9b) — this is the one place that turns them into a singleton,
 * app-wide-shared instance. Singleton is intentional: source reliability is a property of
 * the SOURCE (e.g. "how trustworthy is Reddit sentiment in general"), not per-symbol, so one
 * shared AdaptiveSourceReliabilityBandit accumulates evidence across every symbol it scores.
 *
 * All hyperparameters are external (application.yml hft.asrb.*) — see that file's comments
 * for defaults and the explicit "not yet calibrated" caveat from the disclosure doc §10.
 */
@Configuration
public class AsrbConfig {

    @Bean
    public CorrelationTracker correlationTracker(
            @Value("${hft.asrb.lambda-corr:0.97}") double lambdaCorr,
            @Value("${hft.asrb.kappa:0.5}") double kappa) {
        return new CorrelationTracker(lambdaCorr, kappa);
    }

    @Bean
    public MisinformationRiskScorer misinformationRiskScorer(
            @Value("${hft.asrb.rho-threshold:0.3}") double rhoThreshold,
            @Value("${hft.asrb.w1:2.0}") double w1,
            @Value("${hft.asrb.w2:0.5}") double w2,
            @Value("${hft.asrb.w3:0.7}") double w3,
            @Value("${hft.asrb.v0:1.5}") double v0,
            @Value("${hft.asrb.tau-risk:0.7}") double tauRisk,
            @Value("${hft.asrb.tau-velocity:2.0}") double tauVelocity,
            @Value("${hft.asrb.risk-aversion:0.6}") double riskAversion) {
        return new MisinformationRiskScorer(rhoThreshold, w1, w2, w3, v0, tauRisk, tauVelocity, riskAversion);
    }

    @Bean
    public StabilityIndexCalculator stabilityIndexCalculator() {
        return new StabilityIndexCalculator();
    }

    @Bean
    public PolicySelector policySelector(
            @Value("${hft.asrb.tau-stability:0.6}") double tauStability,
            @Value("${hft.asrb.gittins-z:1.5}") double gittinsZ) {
        return new PolicySelector(tauStability, gittinsZ);
    }

    @Bean
    public AdaptiveSourceReliabilityBandit adaptiveSourceReliabilityBandit(
            CorrelationTracker correlationTracker,
            MisinformationRiskScorer misinformationRiskScorer,
            StabilityIndexCalculator stabilityIndexCalculator,
            PolicySelector policySelector,
            @Value("${hft.asrb.lambda-time:0.99}") double lambdaTime,
            @Value("${hft.asrb.prior-precision:1.0}") double priorPrecision) {
        return new AdaptiveSourceReliabilityBandit(correlationTracker, misinformationRiskScorer,
                stabilityIndexCalculator, policySelector, lambdaTime, priorPrecision);
    }
}
