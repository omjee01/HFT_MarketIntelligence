package com.hft.repository;

import com.hft.model.domain.TechnicalIndicators;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TechnicalIndicatorsRepository extends JpaRepository<TechnicalIndicators, Long> {

    Optional<TechnicalIndicators> findTopBySymbolOrderByComputedAtDesc(String symbol);

    Optional<TechnicalIndicators> findTopBySymbolAndIntervalTypeOrderByComputedAtDesc(
            String symbol, String intervalType);
}