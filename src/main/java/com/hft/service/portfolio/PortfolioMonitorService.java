package com.hft.service.portfolio;

import com.hft.model.domain.PortfolioAlert;
import com.hft.model.domain.PortfolioPosition;
import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import com.hft.model.enums.SignalType;
import com.hft.repository.PortfolioAlertRepository;
import com.hft.repository.PortfolioPositionRepository;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.service.signal.RecommendationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Watches every OPEN PortfolioPosition (across all users — this is a scan, not per-user)
 * and raises a PortfolioAlert when: the target price is reached, the stop-loss is breached, or
 * the underlying signal has turned bearish since purchase. Stage 13, HFT_ARCHITECTURE.md §30.
 *
 * This platform never auto-sells anything — an alert only ever *suggests* an action; the user
 * decides and executes it themselves on their own brokerage, same as the original purchase.
 *
 * Cost note: the signal-deterioration check re-runs RecommendationEngine.generateRecommendation()
 * per open position, which makes real external API calls (subject to the same
 * AlphaVantageBudgetGuard as everything else — Stage 12). Fine for a handful of positions;
 * would need staggering/batching if portfolio sizes grow large. Not built here since there's
 * nothing to stagger yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioMonitorService {

    private final PortfolioPositionRepository positionRepo;
    private final PortfolioAlertRepository alertRepo;
    private final MarketDataAggregatorService marketData;
    private final RecommendationEngine recommendationEngine;

    @Value("${hft.portfolio.monitor-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${hft.portfolio.monitor-poll-ms:900000}")   // 15 min default
    @Async("analysisExecutor")
    public void monitorOpenPositions() {
        if (!enabled) return;
        List<PortfolioPosition> open = positionRepo.findByStatus("OPEN");
        if (open.isEmpty()) return;

        log.debug("[PortfolioMonitor] Scanning {} open position(s)", open.size());
        for (PortfolioPosition position : open) {
            try {
                checkPosition(position);
            } catch (Exception e) {
                log.warn("[PortfolioMonitor] Check failed for position {} ({}): {}",
                        position.getId(), position.getSymbol(), e.getMessage());
            }
        }
    }

    private void checkPosition(PortfolioPosition position) {
        Optional<StockQuote> quoteOpt = marketData.getQuote(position.getSymbol(), position.getMarket());
        if (quoteOpt.isEmpty() || quoteOpt.get().getCurrentPrice() == null) return;

        BigDecimal currentPrice = quoteOpt.get().getCurrentPrice();
        updatePriceAndPnl(position, currentPrice);

        if (position.getTargetPrice() != null && currentPrice.compareTo(position.getTargetPrice()) >= 0) {
            raiseAlertOnce(position, "TARGET_HIT", "SELL", String.format(
                    "%s reached its target of %s (now %s) — consider selling to lock in the gain.",
                    position.getSymbol(), position.getTargetPrice(), currentPrice));
        }

        if (position.getStopLossPrice() != null && currentPrice.compareTo(position.getStopLossPrice()) <= 0) {
            raiseAlertOnce(position, "STOP_LOSS_HIT", "SELL", String.format(
                    "%s fell to its stop-loss of %s (now %s) — consider selling to limit further loss.",
                    position.getSymbol(), position.getStopLossPrice(), currentPrice));
        }

        checkSignalDeterioration(position);
    }

    private void updatePriceAndPnl(PortfolioPosition position, BigDecimal currentPrice) {
        BigDecimal currentValue = currentPrice.multiply(position.getQuantity());
        BigDecimal unrealizedPnl = currentValue.subtract(position.getInvestedAmount());
        BigDecimal unrealizedPnlPercent = position.getInvestedAmount().signum() != 0
                ? unrealizedPnl.divide(position.getInvestedAmount(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        position.setCurrentPrice(currentPrice);
        position.setCurrentValue(currentValue);
        position.setUnrealizedPnl(unrealizedPnl);
        position.setUnrealizedPnlPercent(unrealizedPnlPercent);
        position.setLastUpdated(LocalDateTime.now());
        positionRepo.save(position);
    }

    /** Re-scores the symbol fresh; a bearish signal on a position bought on a bullish call is
     *  itself the deterioration signal — no stored baseline confidence needed. */
    private void checkSignalDeterioration(PortfolioPosition position) {
        Optional<TradeRecommendation> fresh =
                recommendationEngine.generateRecommendation(position.getSymbol(), position.getMarket());
        if (fresh.isEmpty()) return;

        SignalType signal = fresh.get().getSignal();
        if (signal == SignalType.SELL || signal == SignalType.STRONG_SELL) {
            raiseAlertOnce(position, "SIGNAL_DETERIORATED", "REVIEW", String.format(
                    "%s's outlook has turned %s (confidence %.0f%%) since you bought it — worth reviewing.",
                    position.getSymbol(), signal, fresh.get().getConfidencePercent()));
        }
    }

    private void raiseAlertOnce(PortfolioPosition position, String alertType, String suggestedAction, String message) {
        boolean alreadyAlerted = !alertRepo
                .findByPositionIdAndAlertTypeAndAcknowledgedFalse(position.getId(), alertType)
                .isEmpty();
        if (alreadyAlerted) return;   // don't spam — one open alert of this type per position at a time

        PortfolioAlert alert = PortfolioAlert.builder()
                .username(position.getUsername())
                .positionId(position.getId())
                .symbol(position.getSymbol())
                .alertType(alertType)
                .suggestedAction(suggestedAction)
                .message(message)
                .createdAt(LocalDateTime.now())
                .acknowledged(false)
                .build();
        alertRepo.save(alert);
        log.info("[PortfolioMonitor] {} alert for {} ({}): {}",
                alertType, position.getUsername(), position.getSymbol(), message);
    }
}
