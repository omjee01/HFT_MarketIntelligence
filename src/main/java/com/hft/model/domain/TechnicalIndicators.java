package com.hft.model.domain;

import com.hft.model.enums.TrendDirection;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Computed technical indicators for a given symbol at a point in time.
 * Persisted for analytics; served from Redis cache (TTL=5min) on hot path.
 */
@Entity
@Table(name = "technical_indicators",
       indexes = {
           @Index(name = "idx_ta_symbol", columnList = "symbol"),
           @Index(name = "idx_ta_computed_at", columnList = "computed_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicators {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String intervalType;    // "1D", "1h", etc.

    // ─── Trend Indicators ──────────────────────────────────────────────────────
    private Double sma20;
    private Double sma50;
    private Double sma200;

    private Double ema9;
    private Double ema21;
    private Double ema55;

    private Double supertrend;              // + = bullish, - = bearish
    private Boolean supertrendBullish;

    // ─── Momentum Indicators ──────────────────────────────────────────────────
    private Double rsi14;                   // 0–100; >70 overbought, <30 oversold

    private Double macdLine;
    private Double macdSignal;
    private Double macdHistogram;
    private Boolean macdBullishCrossover;   // macdLine just crossed above signal

    private Double stochasticK;
    private Double stochasticD;
    private Boolean stochasticBullishCrossover;

    private Double williamsR;               // 0 to -100; <-80 oversold

    private Double cci20;                   // Commodity Channel Index

    // ─── Volatility Indicators ────────────────────────────────────────────────
    private Double bollingerUpper;
    private Double bollingerMiddle;
    private Double bollingerLower;
    private Double bollingerBandWidth;      // (upper-lower)/middle * 100
    private Boolean bollingerBreakoutUp;
    private Boolean bollingerBreakoutDown;

    private Double atr14;                   // Average True Range (for stop-loss sizing)

    private Double historicalVolatility20;  // 20-day HV (annualized %)
    private Double impliedVolatility;       // for options (if available)

    // ─── Volume Indicators ────────────────────────────────────────────────────
    private Long obv;                       // On-Balance Volume
    private Boolean obvTrendingUp;

    private Double vwap;                    // Volume Weighted Average Price

    private Double mfi14;                   // Money Flow Index (0–100)

    private Double volumeRatio;             // current vol / 20-day avg vol

    // ─── Support & Resistance ─────────────────────────────────────────────────
    private Double supportLevel;
    private Double resistanceLevel;

    private Double fibonacci236;            // Fib retracement levels
    private Double fibonacci382;
    private Double fibonacci500;
    private Double fibonacci618;

    // ─── Pattern Detection ────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "detected_patterns",
                     joinColumns = @JoinColumn(name = "indicator_id"))
    @Column(name = "pattern", length = 60)
    private List<String> detectedPatterns;  // e.g. ["GOLDEN_CROSS","HAMMER","BULL_FLAG"]

    @Enumerated(EnumType.STRING)
    private TrendDirection trendDirection;

    // ─── Composite Score (0–100) ───────────────────────────────────────────────
    private Double technicalScore;

    // ─── Metadata ──────────────────────────────────────────────────────────────
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(length = 10)
    private String priceAtCompute;   // price snapshot when computed

    // ─── Signal helpers ───────────────────────────────────────────────────────
    public boolean isRsiOversold()    { return rsi14 != null && rsi14 < 30; }
    public boolean isRsiOverbought()  { return rsi14 != null && rsi14 > 70; }
    public boolean isMacdBullish()    { return macdBullishCrossover != null && macdBullishCrossover; }
    public boolean isGoldenCross()    { return sma50 != null && sma200 != null && sma50 > sma200; }
    public boolean isDeathCross()     { return sma50 != null && sma200 != null && sma50 < sma200; }
    public boolean isAboveVWAP()      { return vwap != null; } // caller compares with price
    public boolean isHighVolume()     { return volumeRatio != null && volumeRatio > 1.5; }
}