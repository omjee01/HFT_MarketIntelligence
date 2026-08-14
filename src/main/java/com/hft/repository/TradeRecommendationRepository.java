package com.hft.repository;

import com.hft.model.domain.TradeRecommendation;
import com.hft.model.enums.Market;
import com.hft.model.enums.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRecommendationRepository extends JpaRepository<TradeRecommendation, String> {

    List<TradeRecommendation> findByMarketAndStatusOrderByCompositeScoreDesc(
            Market market, String status);

    List<TradeRecommendation> findBySignalAndStatusOrderByConfidencePercentDesc(
            SignalType signal, String status);

    @Query("SELECT r FROM TradeRecommendation r WHERE r.status = 'ACTIVE' " +
           "AND r.signal IN ('BUY', 'STRONG_BUY') " +
           "ORDER BY r.compositeScore DESC")
    List<TradeRecommendation> findActiveTopBuys();

    @Query("SELECT r FROM TradeRecommendation r WHERE r.market = :market " +
           "AND r.status = 'ACTIVE' AND r.generatedAt > :since " +
           "ORDER BY r.compositeScore DESC")
    List<TradeRecommendation> findRecentByMarket(Market market, LocalDateTime since);

    @Query("SELECT r FROM TradeRecommendation r WHERE r.symbol = :symbol " +
           "ORDER BY r.generatedAt DESC")
    List<TradeRecommendation> findHistoryForSymbol(String symbol);
}