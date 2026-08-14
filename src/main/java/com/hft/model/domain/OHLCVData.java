package com.hft.model.domain;

import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OHLCV candlestick bar — the fundamental unit for all technical analysis.
 * Indexed aggressively for fast time-range queries.
 */
@Entity
@Table(name = "ohlcv_data",
       indexes = {
           @Index(name = "idx_ohlcv_symbol_date", columnList = "symbol, bar_date"),
           @Index(name = "idx_ohlcv_symbol_interval", columnList = "symbol, interval_type")
       },
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"symbol", "bar_date", "interval_type"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OHLCVData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(name = "bar_date", nullable = false)
    private LocalDate barDate;

    @Column(name = "interval_type", nullable = false, length = 10)
    private String intervalType;    // "1D", "1W", "1M", "1h", "15m", "5m", "1m"

    // ─── OHLCV ─────────────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal close;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal adjustedClose;  // split/dividend adjusted

    @Column(nullable = false)
    private Long volume;

    // ─── Derived ───────────────────────────────────────────────────────────────
    @Column(precision = 8, scale = 4)
    private BigDecimal changePercent;

    @Column(precision = 15, scale = 4)
    private BigDecimal vwap;           // Volume Weighted Average Price

    @Column
    private LocalDateTime fetchedAt;

    @Column(length = 30)
    private String dataSource;

    // ─── Candlestick helpers ───────────────────────────────────────────────────
    public boolean isBullish()  { return close.compareTo(open) > 0; }
    public boolean isBearish()  { return close.compareTo(open) < 0; }
    public boolean isDoji()     {
        BigDecimal bodySize = close.subtract(open).abs();
        BigDecimal range = high.subtract(low);
        if (range.compareTo(BigDecimal.ZERO) == 0) return true;
        return bodySize.divide(range, 4, java.math.RoundingMode.HALF_UP)
                       .compareTo(BigDecimal.valueOf(0.1)) < 0;
    }

    public BigDecimal bodySize()  { return close.subtract(open).abs(); }
    public BigDecimal upperWick() { return high.subtract(isBullish() ? close : open); }
    public BigDecimal lowerWick() { return (isBullish() ? open : close).subtract(low); }
    public BigDecimal range()     { return high.subtract(low); }
}