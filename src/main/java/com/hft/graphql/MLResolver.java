package com.hft.graphql;

import com.hft.ml.ModelABRouter;
import com.hft.ml.ModelPerformanceTracker;
import com.hft.ml.ModelPerformanceTracker.ModelPerformance;
import com.hft.model.enums.Market;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL resolver for ML model performance observability.
 *
 * Queries:
 *   modelPerformance(model: String!): ModelPerformance
 *       Returns accuracy stats for the requested model ("A" or "B").
 *
 * Mutations:
 *   recordSignalOutcome(symbol, market, model, actualReturnPercent, wasBullishCall): ModelPerformance
 *       Registers the actual market outcome for a past bullish/bearish call,
 *       updating hit-rate counters and Prometheus gauges.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MLResolver {

    private final ModelPerformanceTracker tracker;
    private final ModelABRouter           router;

    @QueryMapping
    public ModelPerformance modelPerformance(@Argument String model) {
        String m = model != null ? model.toUpperCase() : "A";
        if (!m.equals("A") && !m.equals("B")) {
            throw new IllegalArgumentException("model must be 'A' or 'B', got: " + model);
        }
        return tracker.getPerformance(m);
    }

    @QueryMapping
    public String modelAssignment(@Argument String symbol) {
        return router.selectedModelName(symbol);
    }

    @MutationMapping
    public ModelPerformance recordSignalOutcome(@Argument String symbol,
                                                 @Argument Market market,
                                                 @Argument String model,
                                                 @Argument double actualReturnPercent,
                                                 @Argument boolean wasBullishCall) {
        String m = model != null ? model.toUpperCase() : "A";
        if (!m.equals("A") && !m.equals("B")) {
            throw new IllegalArgumentException("model must be 'A' or 'B', got: " + model);
        }
        log.info("[MLResolver] recordSignalOutcome: symbol={} model={} return={}% bullish={}",
                 symbol, m, actualReturnPercent, wasBullishCall);
        return tracker.recordOutcome(symbol, m, actualReturnPercent, wasBullishCall);
    }
}