package com.hft.service.portfolio;

import com.hft.model.domain.PortfolioAlert;
import com.hft.model.domain.PortfolioPosition;
import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import com.hft.model.dto.ClosePositionRequest;
import com.hft.model.dto.OpenPositionRequest;
import com.hft.repository.PortfolioAlertRepository;
import com.hft.repository.PortfolioPositionRepository;
import com.hft.repository.TradeRecommendationRepository;
import com.hft.service.data.MarketDataAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Virtual portfolio tracker (Stage 13, HFT_ARCHITECTURE.md §30). This platform never executes
 * trades or touches brokerage credentials — a position is recorded here only after the user
 * has already bought/sold it themselves on Zerodha Kite / INDmoney. PortfolioMonitorService
 * watches OPEN positions and raises PortfolioAlerts; this service is the CRUD + query surface
 * PortfolioController exposes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioPositionRepository positionRepo;
    private final PortfolioAlertRepository alertRepo;
    private final TradeRecommendationRepository recoRepo;
    private final MarketDataAggregatorService marketData;

    public PortfolioPosition openPosition(String username, OpenPositionRequest req) {
        String companyName = req.getSymbol();
        BigDecimal targetPrice = req.getTargetPrice();
        BigDecimal stopLossPrice = req.getStopLossPrice();

        if (req.getRecommendationId() != null && !req.getRecommendationId().isBlank()) {
            TradeRecommendation reco = recoRepo.findById(req.getRecommendationId())
                    .orElseThrow(() -> new NoSuchElementException(
                            "Recommendation not found: " + req.getRecommendationId()));
            companyName = reco.getCompanyName();
            if (targetPrice == null) targetPrice = reco.getTargetPrice();
            if (stopLossPrice == null) stopLossPrice = reco.getStopLossPrice();
        } else if (req.getCompanyName() != null && !req.getCompanyName().isBlank()) {
            companyName = req.getCompanyName();
        } else {
            Optional<StockQuote> quote = marketData.getQuote(req.getSymbol(), req.getMarket());
            if (quote.isPresent() && quote.get().getCompanyName() != null) {
                companyName = quote.get().getCompanyName();
            }
        }

        BigDecimal investedAmount = req.getAvgBuyPrice().multiply(req.getQuantity());

        PortfolioPosition position = PortfolioPosition.builder()
                .username(username)
                .symbol(req.getSymbol().toUpperCase())
                .companyName(companyName)
                .market(req.getMarket())
                .assetType(req.getAssetType())
                .quantity(req.getQuantity())
                .avgBuyPrice(req.getAvgBuyPrice())
                .currentPrice(req.getAvgBuyPrice())
                .targetPrice(targetPrice)
                .stopLossPrice(stopLossPrice)
                .investedAmount(investedAmount)
                .currentValue(investedAmount)
                .unrealizedPnl(BigDecimal.ZERO)
                .unrealizedPnlPercent(BigDecimal.ZERO)
                .entryDate(LocalDate.now())
                .targetExitDate(null)
                .recommendationId(req.getRecommendationId())
                .status("OPEN")
                .currency(req.getMarket().getCurrency())
                .lastUpdated(LocalDateTime.now())
                .build();

        PortfolioPosition saved = positionRepo.save(position);
        log.info("[Portfolio] {} opened {} x{} @ {} ({})",
                username, saved.getSymbol(), saved.getQuantity(), saved.getAvgBuyPrice(), saved.getId());
        return saved;
    }

    public List<PortfolioPosition> listPositions(String username, String status) {
        return status != null
                ? positionRepo.findByUsernameAndStatusOrderByEntryDateDesc(username, status)
                : positionRepo.findByUsernameOrderByEntryDateDesc(username);
    }

    public PortfolioPosition closePosition(String username, String positionId, ClosePositionRequest req) {
        PortfolioPosition position = positionRepo.findByIdAndUsername(positionId, username)
                .orElseThrow(() -> new NoSuchElementException("Position not found: " + positionId));

        BigDecimal realizedPnl = req.getExitPrice().subtract(position.getAvgBuyPrice())
                .multiply(position.getQuantity());

        position.setExitDate(LocalDate.now());
        position.setCurrentPrice(req.getExitPrice());
        position.setRealizedPnl(realizedPnl);
        position.setStatus("CLOSED");
        position.setLastUpdated(LocalDateTime.now());

        PortfolioPosition saved = positionRepo.save(position);
        log.info("[Portfolio] {} closed {} @ {} — realized P&L {}",
                username, saved.getSymbol(), req.getExitPrice(), realizedPnl);
        return saved;
    }

    public void deletePosition(String username, String positionId) {
        PortfolioPosition position = positionRepo.findByIdAndUsername(positionId, username)
                .orElseThrow(() -> new NoSuchElementException("Position not found: " + positionId));
        positionRepo.delete(position);
    }

    // ─── Alerts ─────────────────────────────────────────────────────────────────

    public List<PortfolioAlert> listAlerts(String username, boolean onlyUnacknowledged) {
        return onlyUnacknowledged
                ? alertRepo.findByUsernameAndAcknowledgedFalseOrderByCreatedAtDesc(username)
                : alertRepo.findByUsernameOrderByCreatedAtDesc(username);
    }

    public PortfolioAlert acknowledgeAlert(String username, String alertId) {
        PortfolioAlert alert = alertRepo.findByIdAndUsername(alertId, username)
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + alertId));
        alert.setAcknowledged(true);
        return alertRepo.save(alert);
    }

    static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP);
    }
}
