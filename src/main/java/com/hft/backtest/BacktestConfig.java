package com.hft.backtest;

import com.hft.model.enums.Market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable input configuration for one backtest run.
 *
 * symbols          — list of tickers to simulate (e.g. ["AAPL", "MSFT"])
 * market           — exchange for all symbols
 * fromDate/toDate  — inclusive date range for historical bars
 * modelVariant     — "A" (WeightedComposite) or "B" (EnsembleModel)
 * initialCapital   — virtual starting capital (used for P&L tracking)
 * minConfidence    — minimum confidence% to act on a BUY signal
 * maxHoldingDays   — cut the trade at this many days if target/stop not hit
 * walkForward      — if true, WalkForwardValidator runs instead of a single pass
 * walkForwardWindows — number of rolling windows (default 5)
 */
public record BacktestConfig(
        List<String> symbols,
        Market       market,
        LocalDate    fromDate,
        LocalDate    toDate,
        String       modelVariant,
        BigDecimal   initialCapital,
        double       minConfidence,
        int          maxHoldingDays,
        boolean      walkForward,
        int          walkForwardWindows
) {
    public BacktestConfig {
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("symbols required");
        if (fromDate == null || toDate == null)   throw new IllegalArgumentException("date range required");
        if (fromDate.isAfter(toDate))             throw new IllegalArgumentException("fromDate must be before toDate");
        if (modelVariant == null)  modelVariant = "A";
        if (initialCapital == null) initialCapital = BigDecimal.valueOf(100_000);
        if (minConfidence <= 0)    minConfidence = 60.0;
        if (maxHoldingDays <= 0)   maxHoldingDays = 45;
        if (walkForwardWindows <= 0) walkForwardWindows = 5;
    }
}
