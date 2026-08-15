package com.hft.backtest;

import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Computed performance metrics for one BacktestRun.
 * Stored embedded in BacktestRun — no separate table.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestMetrics {

    private Double totalReturnPct;       // total P&L as % of initial capital
    private Double annualizedReturnPct;  // CAGR over the backtest period
    private Double sharpeRatio;          // (mean return / std return) × √252
    private Double maxDrawdownPct;       // peak-to-trough equity decline %
    private Double winRatePct;           // profitable trades / total trades × 100
    private Double avgWinPct;            // average return on winning trades
    private Double avgLossPct;           // average return on losing trades (positive = loss size)
    private Double profitFactor;         // gross profit / gross loss (>1 = net profitable)
    private Double expectancy;           // average return per trade
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private Integer avgHoldingDays;      // average bars held per trade
}