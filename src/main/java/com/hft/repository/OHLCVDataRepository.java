package com.hft.repository;

import com.hft.model.domain.OHLCVData;
import com.hft.model.enums.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OHLCVDataRepository extends JpaRepository<OHLCVData, Long> {

    List<OHLCVData> findBySymbolAndMarketAndIntervalTypeAndBarDateBetweenOrderByBarDate(
            String symbol, Market market, String intervalType, LocalDate from, LocalDate to);

    List<OHLCVData> findBySymbolAndMarketAndIntervalTypeOrderByBarDate(
            String symbol, Market market, String intervalType);

    @Query("SELECT DISTINCT o.symbol FROM OHLCVData o WHERE o.market = :market AND o.intervalType = :interval")
    List<String> findDistinctSymbolsByMarketAndInterval(Market market, String interval);

    @Query("SELECT MIN(o.barDate) FROM OHLCVData o WHERE o.symbol = :symbol AND o.market = :market")
    LocalDate findEarliestDate(String symbol, Market market);

    @Query("SELECT MAX(o.barDate) FROM OHLCVData o WHERE o.symbol = :symbol AND o.market = :market")
    LocalDate findLatestDate(String symbol, Market market);
}
