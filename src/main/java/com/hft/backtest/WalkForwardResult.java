package com.hft.backtest;

import java.time.LocalDate;

/**
 * Performance metrics for one window in a walk-forward validation run.
 * The full run produces one WalkForwardResult per rolling window.
 */
public record WalkForwardResult(
        int           windowIndex,
        LocalDate     fromDate,
        LocalDate     toDate,
        BacktestMetrics metrics
) {}
