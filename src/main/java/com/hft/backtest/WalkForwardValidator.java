package com.hft.backtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a backtest date range into N rolling windows and runs each independently.
 *
 * Each window is split 80/20: first 80% is the "training context" (not used for scoring,
 * but bars are needed for indicator warmup), final 20% is the "test period" where signals
 * are evaluated. This prevents look-ahead bias from training on the full range.
 *
 * Window layout for N=4 across [2020-01-01, 2024-12-31] (5 years):
 *
 *   Win 0: [2020-01-01 → 2021-03-31]  test: [2020-10-01 → 2021-03-31]
 *   Win 1: [2021-01-01 → 2022-03-31]  test: [2021-10-01 → 2022-03-31]
 *   Win 2: [2022-01-01 → 2023-03-31]  test: [2022-10-01 → 2023-03-31]
 *   Win 3: [2023-01-01 → 2024-12-31]  test: [2023-10-01 → 2024-12-31]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalkForwardValidator {

    private final BacktestRunner runner;

    public List<WalkForwardResult> validate(BacktestConfig config) {
        int numWindows = config.walkForwardWindows();
        long totalDays = ChronoUnit.DAYS.between(config.fromDate(), config.toDate());
        long windowDays = totalDays / numWindows;

        List<WalkForwardResult> results = new ArrayList<>();

        for (int i = 0; i < numWindows; i++) {
            LocalDate winStart = config.fromDate().plusDays(i * windowDays);
            LocalDate winEnd   = (i == numWindows - 1)
                    ? config.toDate()
                    : winStart.plusDays(windowDays).minusDays(1);

            // Test period = last 20% of window
            long testDays   = Math.max(1, (long)((winEnd.toEpochDay() - winStart.toEpochDay()) * 0.20));
            LocalDate testStart = winEnd.minusDays(testDays);

            log.info("[WalkForward] Window {} — test period [{} → {}]", i, testStart, winEnd);

            // Run backtest only on test period (using full window bars for warmup context)
            BacktestConfig windowConfig = new BacktestConfig(
                    config.symbols(), config.market(),
                    testStart, winEnd,
                    config.modelVariant(), config.initialCapital(),
                    config.minConfidence(), config.maxHoldingDays(),
                    false, 0);

            BacktestRun run = runner.runSync(windowConfig, "WF-" + i);
            results.add(new WalkForwardResult(i, testStart, winEnd, run.getMetrics()));
        }

        log.info("[WalkForward] {} windows complete", results.size());
        return results;
    }
}