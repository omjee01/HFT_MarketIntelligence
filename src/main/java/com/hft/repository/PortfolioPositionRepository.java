package com.hft.repository;

import com.hft.model.domain.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, String> {

    List<PortfolioPosition> findByStatusOrderByEntryDateDesc(String status);

    List<PortfolioPosition> findByUsernameOrderByEntryDateDesc(String username);

    List<PortfolioPosition> findByUsernameAndStatusOrderByEntryDateDesc(String username, String status);

    Optional<PortfolioPosition> findByIdAndUsername(String id, String username);

    /** All OPEN positions across every user — PortfolioMonitorService's scan target. */
    List<PortfolioPosition> findByStatus(String status);

    @Query("SELECT SUM(p.unrealizedPnl) FROM PortfolioPosition p WHERE p.status = 'OPEN'")
    BigDecimal totalUnrealizedPnl();

    @Query("SELECT SUM(p.realizedPnl) FROM PortfolioPosition p WHERE p.status = 'CLOSED'")
    BigDecimal totalRealizedPnl();
}