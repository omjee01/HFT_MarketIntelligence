package com.hft.backtest;

import com.hft.model.enums.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, String> {

    List<BacktestRun> findByMarketOrderByStartedAtDesc(Market market);

    List<BacktestRun> findByStatusOrderByStartedAtDesc(String status);

    List<BacktestRun> findTop20ByOrderByStartedAtDesc();
}