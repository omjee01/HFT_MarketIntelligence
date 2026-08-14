package com.hft.repository;

import com.hft.model.domain.IPOData;
import com.hft.model.enums.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPODataRepository extends JpaRepository<IPOData, Long> {

    List<IPOData> findByStatusAndMarket(String status, Market market);

    List<IPOData> findByStatusOrderBySubscriptionCloseDateAsc(String status);
}