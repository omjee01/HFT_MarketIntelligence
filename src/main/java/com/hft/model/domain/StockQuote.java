package com.hft.model.domain;

import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a real-time or latest market quote for any financial instrument.
 * Stored in the relational DB for auditing; hot-path reads come from Redis cache.
 */
@Entity
@Table(name = "stock_quotes",
       indexes = {
           @Index(name = "idx_quote_symbol", columnList = "symbol"),
           @Index(name = "idx_quote_market", columnList = "market"),
           @Index(name = "idx_quote_timestamp", columnList = "timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;               // e.g. "RELIANCE.NSE", "AAPL"

    @Column(nullable = false, length = 120)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType assetType;

    // ─── Price Data ────────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal openPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal highPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal lowPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal previousClose;

    // ─── Change ────────────────────────────────────────────────────────────────
    @Column(precision = 10, scale = 4)
    private BigDecimal dayChange;          // absolute change

    @Column(precision = 8, scale = 4)
    private BigDecimal dayChangePercent;

    @Column(precision = 8, scale = 4)
    private BigDecimal weekChangePercent;

    @Column(precision = 8, scale = 4)
    private BigDecimal monthChangePercent;

    // ─── Volume & Market Cap ───────────────────────────────────────────────────
    @Column
    private Long volume;

    @Column
    private Long avgVolume20Day;

    @Column(precision = 20, scale = 2)
    private BigDecimal marketCap;          // in native currency

    @Column(length = 5)
    private String currency;              // "USD" or "INR"

    // ─── 52-Week Range ─────────────────────────────────────────────────────────
    @Column(precision = 15, scale = 4)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(precision = 15, scale = 4)
    private BigDecimal fiftyTwoWeekLow;

    @Column(precision = 8, scale = 4)
    private BigDecimal percentFrom52WeekHigh;

    @Column(precision = 8, scale = 4)
    private BigDecimal percentFrom52WeekLow;

    // ─── Additional Info ───────────────────────────────────────────────────────
    @Column(length = 60)
    private String sector;

    @Column(length = 60)
    private String industry;

    @Column(precision = 8, scale = 4)
    private BigDecimal peRatio;

    @Column(precision = 8, scale = 4)
    private BigDecimal dividendYield;

    @Column(precision = 8, scale = 4)
    private BigDecimal beta;                  // market sensitivity

    // ─── Metadata ──────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime timestamp;      // when this quote was fetched

    @Column
    private LocalDateTime lastUpdated;

    @Column(length = 30)
    private String dataSource;            // "ALPHA_VANTAGE", "NSE", "YAHOO"

    @Column
    private Boolean isMarketOpen;

    // ─── Convenience ──────────────────────────────────────────────────────────
    public boolean isAbove52WeekAverage() {
        if (fiftyTwoWeekHigh == null || fiftyTwoWeekLow == null) return false;
        BigDecimal avg = fiftyTwoWeekHigh.add(fiftyTwoWeekLow)
                                         .divide(BigDecimal.valueOf(2));
        return currentPrice.compareTo(avg) > 0;
    }

    public boolean isHighVolume() {
        if (volume == null || avgVolume20Day == null || avgVolume20Day == 0) return false;
        return volume > (avgVolume20Day * 1.5);
    }
}