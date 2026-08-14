package com.hft.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lightweight response DTO for live quote endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteResponse {

    private String symbol;
    private String companyName;
    private Market market;
    private AssetType assetType;
    private String currency;

    private BigDecimal price;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal previousClose;

    private BigDecimal change;
    private Double changePercent;

    private Long volume;
    private Long avgVolume;
    private Double volumeRatio;

    private BigDecimal marketCap;
    private Double peRatio;
    private Double beta;

    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private Double percentFrom52WeekHigh;

    private String sector;
    private LocalDateTime timestamp;
    private Boolean isMarketOpen;
    private String dataSource;
}