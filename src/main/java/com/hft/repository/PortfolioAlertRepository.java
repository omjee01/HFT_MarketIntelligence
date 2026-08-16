package com.hft.repository;

import com.hft.model.domain.PortfolioAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioAlertRepository extends JpaRepository<PortfolioAlert, String> {

    List<PortfolioAlert> findByUsernameOrderByCreatedAtDesc(String username);

    List<PortfolioAlert> findByUsernameAndAcknowledgedFalseOrderByCreatedAtDesc(String username);

    List<PortfolioAlert> findByPositionIdAndAlertTypeAndAcknowledgedFalse(String positionId, String alertType);

    Optional<PortfolioAlert> findByIdAndUsername(String id, String username);
}
