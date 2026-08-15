package com.hft.backtest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computes standard strategy performance metrics from a list of completed trades.
 *
 * Metrics:
 *   totalReturnPct      — sum of all trade returns as % of initial capital
 *   annualizedReturnPct — CAGR over the backtest period
 *   sharpeRatio         — (mean daily return / std daily return) × √252
 *   maxDrawdownPct      — largest peak-to-trough decline in running equity
 *   winRatePct          — profitable / total × 100
 *   avgWinPct           — mean return on winning trades
 *   avgLossPct          — mean |return| on losing trades (positive = magnitude of loss)
 *   profitFactor        — gross profit / gross loss
 *   expectancy          — mean return per trade (avg_win×win_rate – avg_loss×loss_rate)
 */
@Component
public class StrategyMetricsEngine {

    private static final double RISK_FREE_RATE = 0.05;  // 5% annual (US T-bill proxy)

    public BacktestMetrics compute(List<BacktestTrade> trades,
                                    BigDecimal initialCapital,
                                    LocalDate fromDate,
                                    LocalDate toDate) {
        if (trades == null || trades.isEmpty()) {
            return BacktestMetrics.builder()
                    .totalTrades(0).winningTrades(0).losingTrades(0)
                    .winRatePct(0.0).totalReturnPct(0.0).sharpeRatio(0.0)
                    .maxDrawdownPct(0.0).profitFactor(0.0).expectancy(0.0)
                    .build();
        }

        int    total   = trades.size();
        int    wins    = (int) trades.stream().filter(t -> Boolean.TRUE.equals(t.getProfitable())).count();
        int    losses  = total - wins;

        List<Double> returns = trades.stream()
                .map(t -> t.getReturnPercent() != null ? t.getReturnPercent() : 0.0)
                .toList();

        double grossProfit = returns.stream().filter(r -> r > 0).mapToDouble(Double::doubleValue).sum();
        double grossLoss   = Math.abs(returns.stream().filter(r -> r < 0).mapToDouble(Double::doubleValue).sum());

        double avgWin    = wins   > 0 ? grossProfit / wins       : 0.0;
        double avgLoss   = losses > 0 ? grossLoss   / losses     : 0.0;
        double winRate   = (double) wins / total;
        double lossRate  = 1.0 - winRate;
        double expectancy = avgWin * winRate - avgLoss * lossRate;

        double totalReturn = returns.stream().mapToDouble(Double::doubleValue).sum();

        long   calendarDays  = ChronoUnit.DAYS.between(fromDate, toDate);
        double yearsElapsed  = calendarDays / 365.25;
        double annualized    = yearsElapsed > 0
                ? (Math.pow(1 + totalReturn / 100.0, 1.0 / yearsElapsed) - 1) * 100.0
                : 0.0;

        double sharpe       = computeSharpe(returns, yearsElapsed);
        double maxDrawdown  = computeMaxDrawdown(returns);

        int avgHolding = (int) trades.stream()
                .filter(t -> t.getHoldingDays() != null)
                .mapToInt(BacktestTrade::getHoldingDays)
                .average().orElse(0);

        return BacktestMetrics.builder()
                .totalTrades(total)
                .winningTrades(wins)
                .losingTrades(losses)
                .totalReturnPct(round(totalReturn))
                .annualizedReturnPct(round(annualized))
                .sharpeRatio(round(sharpe))
                .maxDrawdownPct(round(maxDrawdown))
                .winRatePct(round(winRate * 100))
                .avgWinPct(round(avgWin))
                .avgLossPct(round(avgLoss))
                .profitFactor(round(grossLoss > 0 ? grossProfit / grossLoss : grossProfit))
                .expectancy(round(expectancy))
                .avgHoldingDays(avgHolding)
                .build();
    }

    // ─── Sharpe via per-trade returns (annualized) ────────────────────────────

    private double computeSharpe(List<Double> returns, double yearsElapsed) {
        if (returns.size() < 2) return 0.0;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2)).sum() / (returns.size() - 1);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) return 0.0;

        double riskFreePerTrade = RISK_FREE_RATE / Math.max(1, returns.size() / Math.max(1, yearsElapsed));
        return (mean - riskFreePerTrade) / stdDev * Math.sqrt(252);
    }

    // ─── Max drawdown on running equity curve ─────────────────────────────────

    private double computeMaxDrawdown(List<Double> returns) {
        double peak     = 100.0;
        double equity   = 100.0;
        double maxDD    = 0.0;

        for (double ret : returns) {
            equity += equity * (ret / 100.0);
            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak * 100.0;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}