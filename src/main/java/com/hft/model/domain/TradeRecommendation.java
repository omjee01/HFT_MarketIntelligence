package com.hft.model.domain;

import com.hft.model.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The core output of the HFT platform — a fully data-backed trade recommendation.
 *
 * Every field is populated by the RecommendationEngine after aggregating results
 * from all analysis engines (Technical, Fundamental, Sentiment, Macro, ML).
 */
@Entity
@Table(name = "trade_recommendations",
       indexes = {
           @Index(name = "idx_reco_symbol", columnList = "symbol"),
           @Index(name = "idx_reco_market_signal", columnList = "market, signal_type"),
           @Index(name = "idx_reco_generated", columnList = "generated_at"),
           @Index(name = "idx_reco_status", columnList = "status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRecommendation {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    // ─── Asset Identity ───────────────────────────────────────────────────────
    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType assetType;

    @Column(length = 60)
    private String sector;

    @Column(length = 20)
    private String sectorOutlook;          // "POSITIVE" / "NEUTRAL" / "NEGATIVE"

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MarketCapTier marketCapTier;   // MEGA/LARGE/MID/SMALL/MICRO — dashboard grouping (Stage 13)

    // ─── Signal ───────────────────────────────────────────────────────────────
    // Explicit column name: "signal" is a MySQL reserved word (used by the SIGNAL
    // statement). H2 doesn't reserve it, so this was silently fine until MySQL.
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 20)
    private SignalType signal;             // BUY / SELL / HOLD / STRONG_BUY / etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimeHorizon timeHorizon;       // SHORT_TERM / MEDIUM_TERM / LONG_TERM

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    // ─── Price Targets ────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal currentPrice;       // price at time of recommendation

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;         // recommended entry price

    @Column(length = 40)
    private String entryPriceRange;        // e.g. "₹2,380–₹2,400"

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal targetPrice;        // price target (exit point)

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal stopLossPrice;      // stop loss (max pain point)

    // ─── Expected Returns ─────────────────────────────────────────────────────
    @Column(nullable = false)
    private Double expectedProfitPercent;  // e.g. 18.44 (%)

    @Column(nullable = false)
    private Double maxRiskPercent;         // stop loss risk %

    @Column(nullable = false)
    private Double riskRewardRatio;        // e.g. 3.54 (3.54:1 ratio)

    // ─── Timing ───────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate entryDate;           // when to enter (today or next dip)

    @Column(nullable = false)
    private LocalDate exitDate;            // expected exit date

    @Column(nullable = false)
    private Integer holdingPeriodDays;

    // ─── Scoring (0–100) ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private Double compositeScore;         // overall score

    @Column(nullable = false)
    private Double confidencePercent;      // confidence in this recommendation

    private Double technicalScore;
    private Double fundamentalScore;
    private Double sentimentScore;
    private Double macroScore;
    private Double mlScore;

    // ─── Reasoning (WHY) ──────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reco_reasons",
                     joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "reason", length = 300)
    private List<String> keyReasons;       // bullet points WHY this is recommended

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reco_risks",
                     joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "risk", length = 300)
    private List<String> keyRisks;         // what could go wrong

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reco_news",
                     joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "headline", length = 400)
    private List<String> relatedNews;      // top 3 supporting news headlines

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reco_data_sources",
                     joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "source", length = 60)
    private List<String> dataSources;      // "Alpha Vantage", "NSE Live Data", etc.

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    @Column(nullable = false, length = 20)
    private String status;                 // "ACTIVE" / "CLOSED" / "EXPIRED" / "TRIGGERED"

    // Explicit column name: "rank" became a MySQL reserved word in 8.0 (window
    // functions). Same H2-vs-MySQL gap as "signal" above.
    @Column(name = "reco_rank", length = 30)
    private String rank;                   // "TOP_1", "TOP_2", etc. within its category

    // ─── Helpers ──────────────────────────────────────────────────────────────
    public boolean isBuy()  { return signal == SignalType.BUY || signal == SignalType.STRONG_BUY; }
    public boolean isSell() { return signal == SignalType.SELL || signal == SignalType.STRONG_SELL; }
    public boolean isHighConfidence() { return confidencePercent != null && confidencePercent >= 75.0; }

    public String getFormattedSummary() {
        return String.format("[%s] %s (%s) — %s | Entry: %s | Target: %s | +%.1f%% | Conf: %.0f%%",
                signal.getEmoji(), symbol, market.getCurrency(),
                signal.getDisplayName(), entryPrice, targetPrice,
                expectedProfitPercent, confidencePercent);
    }
}