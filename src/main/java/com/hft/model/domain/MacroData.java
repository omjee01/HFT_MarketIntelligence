package com.hft.model.domain;

import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Macroeconomic and geopolitical data for US and Indian markets.
 * Aggregated from FRED (US), RBI (India), World Bank, and news sources.
 */
@Entity
@Table(name = "macro_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    // ─── Interest Rates ───────────────────────────────────────────────────────
    private Double interestRate;            // Fed Funds Rate (US) / RBI Repo Rate (India)
    private Double interestRatePreviousMeeting;
    private String interestRateOutlook;     // "HIKE" / "CUT" / "HOLD"

    // ─── Inflation ────────────────────────────────────────────────────────────
    private Double cpiInflationRate;        // CPI %
    private Double wpiInflationRate;        // WPI % (India only)
    private Double ppiInflationRate;        // PPI % (US only)
    private String inflationTrend;          // "RISING" / "FALLING" / "STABLE"

    // ─── Growth ───────────────────────────────────────────────────────────────
    private Double gdpGrowthRateQoQ;        // % QoQ
    private Double gdpGrowthRateYoY;        // % YoY
    private Double gdpGrowthForecast;       // IMF/WB forecast
    private Double unemploymentRate;        // % (US only)

    // ─── Market Indicators ────────────────────────────────────────────────────
    private Double vixLevel;               // CBOE VIX (US) / India VIX
    private String vixSentiment;           // "FEAR" / "NEUTRAL" / "GREED"

    private Double tenYearYield;            // US 10Y Treasury (%)
    private Double twoYearYield;            // US 2Y Treasury (%)
    private Boolean isYieldCurveInverted;  // 2Y > 10Y = recession signal

    // ─── Currency ─────────────────────────────────────────────────────────────
    private Double usdInrRate;             // USD/INR exchange rate
    private Double dxyIndex;              // Dollar Index
    private Double usdInrTrend;           // % change (negative = INR strengthening)

    // ─── India-Specific ───────────────────────────────────────────────────────
    @Column(precision = 20, scale = 2)
    private BigDecimal fiiNetFlowCrores;   // FII net buy/sell in ₹ Crores (+ = buy)

    @Column(precision = 20, scale = 2)
    private BigDecimal diiNetFlowCrores;   // DII net buy/sell in ₹ Crores

    private String fiiFlowTrend;           // "BUYING" / "SELLING" / "NEUTRAL"
    private Integer consecutiveFiiBuyDays;
    private Integer consecutiveFiiSellDays;

    // ─── Commodity Prices ─────────────────────────────────────────────────────
    private Double crudeBrentPrice;        // USD/barrel
    private Double crudeBrentChangePercent;

    private Double goldPriceUsd;           // USD/troy oz
    private Double goldPriceInr;           // INR/10g

    // ─── Geopolitical Risk ────────────────────────────────────────────────────
    private Double geopoliticalRiskScore;  // 0–10 (10 = extreme risk)
    private String geopoliticalRiskLabel;  // "LOW" / "ELEVATED" / "HIGH" / "EXTREME"

    @Column(length = 500)
    private String activeGeopoliticalEvents; // comma-separated list of active events

    @Column(length = 200)
    private String majorUpcomingEvents;      // e.g. "Fed Meeting Jun 12, RBI Jun 6"

    // ─── Composite Macro Score ────────────────────────────────────────────────
    private Double macroScore;             // 0–100 (higher = better macro env)

    @Column(length = 20)
    private String macroSentiment;         // "POSITIVE" / "NEUTRAL" / "NEGATIVE"

    // ─── Sector Impact Signals ────────────────────────────────────────────────
    @Column(length = 500)
    private String sectorTailwinds;        // e.g. "IT, Banking" (from rate cut)

    @Column(length = 500)
    private String sectorHeadwinds;        // e.g. "Real Estate, Oil&Gas"

    // ─── Metadata ─────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(length = 30)
    private String dataSource;             // "FRED, RBI, NSE, Reuters"
}