package com.hft.repository;

import com.hft.model.domain.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, String> {

    List<PortfolioPosition> findByStatusOrderByEntryDateDesc(String status);

    @Query("SELECT SUM(p.unrealizedPnl) FROM PortfolioPosition p WHERE p.status = 'OPEN'")
    BigDecimal totalUnrealizedPnl();

    @Query("SELECT SUM(p.realizedPnl) FROM PortfolioPosition p WHERE p.status = 'CLOSED'")
    BigDecimal totalRealizedPnl();
}