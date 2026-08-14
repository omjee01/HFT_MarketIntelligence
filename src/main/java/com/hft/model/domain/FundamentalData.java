package com.hft.model.domain;

import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fundamental financial metrics for a company.
 * Refreshed daily/weekly from Alpha Vantage Fundamentals or BSE/NSE filings.
 */
@Entity
@Table(name = "fundamental_data",
       indexes = @Index(name = "idx_fund_symbol", columnList = "symbol"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalData {

    @Id
    @Column(length = 30)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(length = 60)
    private String sector;

    @Column(length = 60)
    private String industry;

    // ─── Valuation Ratios ─────────────────────────────────────────────────────
    private Double peRatio;              // Price-to-Earnings
    private Double pbRatio;              // Price-to-Book
    private Double psRatio;              // Price-to-Sales
    private Double evToEbitda;           // EV/EBITDA
    private Double evToRevenue;          // EV/Revenue
    private Double priceToFreeCashFlow;

    // ─── Earnings ─────────────────────────────────────────────────────────────
    private Double epsCurrentYear;       // Earnings Per Share
    private Double epsLastYear;
    private Double epsGrowthYoY;         // % YoY
    private Double epsGrowthQoQ;         // % QoQ

    @Column(precision = 20, scale = 2)
    private BigDecimal revenueCurrentYear;

    private Double revenueGrowthYoY;     // %
    private Double revenueGrowthQoQ;     // %

    private Double ebitda;
    private Double ebitdaMargin;         // %

    // ─── Profitability ────────────────────────────────────────────────────────
    private Double grossProfitMargin;    // %
    private Double operatingMargin;      // %
    private Double netProfitMargin;      // %
    private Double roe;                  // Return on Equity %
    private Double roa;                  // Return on Assets %
    private Double roce;                 // Return on Capital Employed %

    // ─── Balance Sheet ────────────────────────────────────────────────────────
    private Double debtToEquity;
    private Double currentRatio;
    private Double quickRatio;
    private Double interestCoverageRatio;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalDebt;

    @Column(precision = 20, scale = 2)
    private BigDecimal cashAndEquivalents;

    // ─── Dividends ────────────────────────────────────────────────────────────
    private Double dividendYield;        // %
    private Double dividendPayoutRatio;  // %
    private Double dividendGrowthRate;   // 5-year CAGR %

    // ─── Holdings (especially relevant for India) ─────────────────────────────
    private Double promoterHolding;      // % (India-specific)
    private Double fiiFolioHolding;      // % FII holding
    private Double diiHolding;           // % DII holding
    private Double publicHolding;        // %
    private Double institutionalHolding; // %

    // ─── Risk / Market Sensitivity ───────────────────────────────────────────
    private Double beta;                 // 52-week beta vs index

    // ─── Growth Metrics ───────────────────────────────────────────────────────
    private Double revenueGrowthCagr3Y;  // 3-year CAGR
    private Double epsGrowthCagr3Y;
    private Double freeCashFlowGrowth;

    // ─── Scoring ──────────────────────────────────────────────────────────────
    private Double fundamentalScore;    // 0–100

    @Column(length = 20)
    private String valuationStatus;     // UNDERVALUED / FAIRLY_VALUED / OVERVALUED

    // ─── Metadata ─────────────────────────────────────────────────────────────
    private LocalDate lastUpdated;
    private LocalDate nextEarningsDate;

    @Column(length = 30)
    private String dataSource;

    // ─── Helpers ──────────────────────────────────────────────────────────────
    public boolean isValueStock()  { return peRatio != null && peRatio < 15 && pbRatio != null && pbRatio < 1.5; }
    public boolean isGrowthStock() { return epsGrowthYoY != null && epsGrowthYoY > 20 && revenueGrowthYoY != null && revenueGrowthYoY > 20; }
    public boolean hasSolidROE()   { return roe != null && roe > 15; }
    public boolean isDebtFree()    { return debtToEquity != null && debtToEquity < 0.3; }
    public boolean hasStrongPromoterHolding() { return promoterHolding != null && promoterHolding > 50; }
}