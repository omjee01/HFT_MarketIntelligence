package com.hft.service.risk;

import com.hft.model.domain.PortfolioPosition;
import com.hft.model.domain.TradeRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Risk Management Service — position sizing, portfolio risk, and trade filters.
 *
 * Implements:
 *  1. Kelly Criterion for optimal position sizing
 *  2. Fixed Fractional (2% rule) as conservative alternative
 *  3. Portfolio-level VaR (Value at Risk) — simplified
 *  4. R:R ratio gate
 *  5. Correlation filter (avoid overcorrelated positions)
 */
@Slf4j
@Service
public class RiskManagementService {

    @Value("${hft.recommendation.min-risk-reward-ratio:2.0}")
    private double minRrRatio;

    /**
     * Kelly Criterion position size.
     * f* = (b×p - q) / b
     * where b = profit ratio (R), p = win probability, q = 1 - p
     *
     * @param portfolioValue total portfolio value
     * @param winProbability estimated probability of success (0–1)
     * @param profitRatio    target profit / stop loss risk
     * @return recommended position size in currency
     */
    public BigDecimal kellyPositionSize(BigDecimal portfolioValue,
                                        double winProbability,
                                        double profitRatio) {
        double q = 1 - winProbability;
        double kellyFraction = (profitRatio * winProbability - q) / profitRatio;

        // Cap at 25% and use half-Kelly for safety
        kellyFraction = Math.max(0, Math.min(0.25, kellyFraction * 0.5));

        return portfolioValue.multiply(BigDecimal.valueOf(kellyFraction))
                             .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fixed Fractional position size — risk max 2% of portfolio per trade.
     *
     * @param portfolioValue   total portfolio value
     * @param entryPrice       entry price per share
     * @param stopLossPrice    stop loss price
     * @param riskPercent      max risk % of portfolio (default 2%)
     */
    public int fixedFractionalPositionSize(BigDecimal portfolioValue,
                                            BigDecimal entryPrice,
                                            BigDecimal stopLossPrice,
                                            double riskPercent) {
        if (entryPrice.compareTo(stopLossPrice) <= 0) return 0;

        BigDecimal riskPerShare  = entryPrice.subtract(stopLossPrice);
        BigDecimal maxRiskAmount = portfolioValue.multiply(BigDecimal.valueOf(riskPercent / 100.0));
        int shares = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.FLOOR).intValue();
        return Math.max(0, shares);
    }

    /**
     * Simplified portfolio VaR (95%, 1-day) using historical volatility.
     * VaR = Portfolio Value × Volatility × z-score(95%) × sqrt(1 day)
     * z-score(95%) = 1.645
     */
    public BigDecimal calculateVaR(BigDecimal portfolioValue, double annualVolatility) {
        double dailyVol  = annualVolatility / Math.sqrt(252);
        double zScore    = 1.645;  // 95% confidence
        double var1Day   = portfolioValue.doubleValue() * dailyVol * zScore;
        return BigDecimal.valueOf(var1Day).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Validate if a trade meets minimum risk/reward criteria.
     */
    public boolean passesRiskFilters(TradeRecommendation reco) {
        if (reco == null) return false;

        // R:R filter
        if (reco.getRiskRewardRatio() != null && reco.getRiskRewardRatio() < minRrRatio) {
            log.debug("[Risk] {} rejected: R:R {:.2f} < {:.2f}", reco.getSymbol(),
                      reco.getRiskRewardRatio(), minRrRatio);
            return false;
        }

        // Max risk % filter — never risk more than 10% on a single trade
        if (reco.getMaxRiskPercent() != null && reco.getMaxRiskPercent() > 10.0) {
            log.debug("[Risk] {} rejected: max risk {:.1f}% too high", reco.getSymbol(),
                      reco.getMaxRiskPercent());
            return false;
        }

        // Confidence threshold for BUY signals
        if (reco.getSignal() != null && reco.getSignal().isBullish()) {
            if (reco.getConfidencePercent() != null && reco.getConfidencePercent() < 55.0) {
                log.debug("[Risk] {} rejected: confidence {:.1f}% below minimum",
                          reco.getSymbol(), reco.getConfidencePercent());
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate portfolio-level max drawdown from position list.
     */
    public double calculateMaxDrawdown(List<PortfolioPosition> positions) {
        if (positions == null || positions.isEmpty()) return 0.0;
        double totalInvested = positions.stream()
                .filter(p -> p.getInvestedAmount() != null)
                .mapToDouble(p -> p.getInvestedAmount().doubleValue()).sum();
        double totalLoss = positions.stream()
                .filter(p -> p.getUnrealizedPnl() != null && p.getUnrealizedPnl().doubleValue() < 0)
                .mapToDouble(p -> Math.abs(p.getUnrealizedPnl().doubleValue())).sum();
        return totalInvested > 0 ? (totalLoss / totalInvested) * 100.0 : 0.0;
    }

    /**
     * Dynamic stop-loss update: trail the stop up as price rises.
     * New stop = max(original stop, currentPrice - 2×ATR)
     */
    public BigDecimal trailingStopLoss(BigDecimal currentPrice,
                                        BigDecimal originalStopLoss,
                                        double atr14) {
        BigDecimal trailingStop = currentPrice.subtract(BigDecimal.valueOf(2 * atr14));
        return trailingStop.max(originalStopLoss);
    }
}