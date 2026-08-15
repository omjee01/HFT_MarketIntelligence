package com.hft.ml;

import com.hft.model.enums.Market;
import com.hft.model.enums.SignalType;
import com.hft.service.ml.MLPredictionService;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Tracks per-model prediction accuracy and publishes Micrometer metrics.
 *
 * Metrics exposed at /actuator/prometheus:
 *   hft_ml_predictions_total{model="A|B", signal="BUY|SELL|..."}   — Counter
 *   hft_ml_correct_predictions_total{model="A|B"}                   — Counter
 *   hft_ml_hit_rate{model="A|B"}                                    — Gauge (%)
 *   hft_ml_avg_return_pct{model="A|B"}                              — Gauge (%)
 *
 * A bullish call (BUY / STRONG_BUY) is "correct" when actualReturnPct > 0.
 * Outcomes are recorded via recordOutcome() — called by the GraphQL mutation
 * recordSignalOutcome or by a scheduled reconciliation job.
 *
 * Redis stores raw prediction metadata (TTL 90 days) for cross-restart continuity.
 * In-memory counters accumulate since last restart (lightweight; reset on redeploy).
 */
@Slf4j
@Component
public class ModelPerformanceTracker {

    private static final String REDIS_PREFIX = "hft:ml:outcome:";

    private final StringRedisTemplate redis;
    private final MeterRegistry       meterRegistry;

    private final ConcurrentHashMap<String, AtomicInteger> totalPredictions   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> correctPredictions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder>   totalReturn        = new ConcurrentHashMap<>();

    public ModelPerformanceTracker(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis         = redis;
        this.meterRegistry = meterRegistry;

        for (String model : new String[]{"A", "B"}) {
            final String m = model;
            Gauge.builder("hft.ml.hit.rate",    () -> hitRate(m))
                    .tag("model", model)
                    .description("Prediction hit rate — correct / total predictions")
                    .register(meterRegistry);
            Gauge.builder("hft.ml.avg.return.pct", () -> avgReturn(m))
                    .tag("model", model)
                    .description("Average actual return % for bullish predictions")
                    .register(meterRegistry);
        }
    }

    // ─── Record a new prediction ──────────────────────────────────────────────

    public void recordPrediction(String symbol, Market market, String model,
                                  MLPredictionService.MLPrediction prediction) {
        Counter.builder("hft.ml.predictions")
                .tag("model",  model)
                .tag("signal", SignalType.fromScore(prediction.getCompositeScore()).name())
                .register(meterRegistry)
                .increment();

        totalPredictions.computeIfAbsent(model, k -> new AtomicInteger()).incrementAndGet();

        try {
            String key  = REDIS_PREFIX + symbol + ":" + market.name() + ":" + model;
            String value = prediction.getCompositeScore() + ":"
                         + prediction.getConfidencePercent() + ":"
                         + SignalType.fromScore(prediction.getCompositeScore()).name();
            redis.opsForValue().set(key, value, Duration.ofDays(90));
        } catch (Exception e) {
            log.debug("[ModelPerf] Redis store skipped: {}", e.getMessage());
        }
    }

    // ─── Record an outcome for a past bullish call ────────────────────────────

    public ModelPerformance recordOutcome(String symbol, String model,
                                           double actualReturnPct, boolean wasBullishCall) {
        if (wasBullishCall) {
            boolean correct = actualReturnPct > 0;
            if (correct) {
                correctPredictions.computeIfAbsent(model, k -> new AtomicInteger()).incrementAndGet();
                Counter.builder("hft.ml.correct.predictions")
                        .tag("model", model)
                        .register(meterRegistry)
                        .increment();
            }
            totalReturn.computeIfAbsent(model, k -> new DoubleAdder()).add(actualReturnPct);
        }

        log.info("[ModelPerf] Outcome: symbol={} model={} return={}% correct={}",
                 symbol, model, actualReturnPct, wasBullishCall && actualReturnPct > 0);

        try {
            String key = REDIS_PREFIX + symbol + ":outcome:" + model;
            redis.opsForValue().set(key, String.valueOf(actualReturnPct), Duration.ofDays(90));
        } catch (Exception e) {
            log.debug("[ModelPerf] Redis outcome store skipped: {}", e.getMessage());
        }

        return getPerformance(model);
    }

    // ─── Snapshot query ───────────────────────────────────────────────────────

    public ModelPerformance getPerformance(String model) {
        return new ModelPerformance(
                model,
                totalPredictions.getOrDefault(model, new AtomicInteger()).get(),
                correctPredictions.getOrDefault(model, new AtomicInteger()).get(),
                hitRate(model),
                avgReturn(model));
    }

    // ─── Internal gauges ──────────────────────────────────────────────────────

    private double hitRate(String model) {
        int total   = totalPredictions.getOrDefault(model, new AtomicInteger()).get();
        int correct = correctPredictions.getOrDefault(model, new AtomicInteger()).get();
        return total > 0 ? (double) correct / total * 100.0 : 0.0;
    }

    private double avgReturn(String model) {
        int    total = totalPredictions.getOrDefault(model, new AtomicInteger()).get();
        DoubleAdder ret = totalReturn.getOrDefault(model, new DoubleAdder());
        return total > 0 ? ret.sum() / total : 0.0;
    }

    // ─── DTO ─────────────────────────────────────────────────────────────────

    public record ModelPerformance(
            String model,
            int    totalPredictions,
            int    correctPredictions,
            double hitRatePct,
            double avgReturnPct) {}
}
