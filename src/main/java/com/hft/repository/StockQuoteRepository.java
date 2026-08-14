package com.hft.repository;

import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockQuoteRepository extends JpaRepository<StockQuote, Long> {

    Optional<StockQuote> findTopBySymbolOrderByTimestampDesc(String symbol);

    List<StockQuote> findByMarketOrderByDayChangePercentDesc(Market market);

    @Query("SELECT s FROM StockQuote s WHERE s.market = :market AND s.dayChangePercent IS NOT NULL " +
           "ORDER BY s.dayChangePercent DESC")
    List<StockQuote> findTopGainersByMarket(Market market);

    @Query("SELECT s FROM StockQuote s WHERE s.market = :market AND s.volume IS NOT NULL " +
           "AND s.avgVolume20Day IS NOT NULL AND s.volume > s.avgVolume20Day * 1.5")
    List<StockQuote> findHighVolumeStocks(Market market);
}