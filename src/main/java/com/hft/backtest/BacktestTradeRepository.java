package com.hft.backtest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, Long> {

    List<BacktestTrade> findByRunIdOrderByEntryDate(String runId);

    List<BacktestTrade> findByRunIdAndProfitable(String runId, boolean profitable);

    long countByRunIdAndExitReason(String runId, String exitReason);
}
