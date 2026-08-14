package com.hft.repository;

import com.hft.model.domain.FundamentalData;
import com.hft.model.enums.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundamentalDataRepository extends JpaRepository<FundamentalData, String> {

    List<FundamentalData> findByMarketAndFundamentalScoreGreaterThanOrderByFundamentalScoreDesc(
            Market market, Double minScore);

    @Query("SELECT f FROM FundamentalData f WHERE f.market = :market " +
           "AND f.peRatio IS NOT NULL AND f.peRatio < :maxPe " +
           "AND f.roe IS NOT NULL AND f.roe > :minRoe")
    List<FundamentalData> findValueStocks(Market market, double maxPe, double minRoe);
}