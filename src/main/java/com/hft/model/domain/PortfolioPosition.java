package com.hft.model.domain;

import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A position in the virtual portfolio tracker.
 * Tracks entry, current value, and P&L for each trade.
 */
@Entity
@Table(name = "portfolio_positions",
       indexes = @Index(name = "idx_pos_symbol", columnList = "symbol"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPosition {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    @Column(nullable = false, length = 60)
    private String username;               // owner — added Stage 13 (this entity predates Identity/Auth)

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

    // ─── Position Details ─────────────────────────────────────────────────────
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;           // shares/units/lots

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal avgBuyPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal targetPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal stopLossPrice;

    // ─── P&L ─────────────────────────────────────────────────────────────────
    @Column(precision = 15, scale = 4)
    private BigDecimal investedAmount;     // avgBuyPrice × quantity

    @Column(precision = 15, scale = 4)
    private BigDecimal currentValue;       // currentPrice × quantity

    @Column(precision = 15, scale = 4)
    private BigDecimal unrealizedPnl;      // currentValue - investedAmount

    @Column(precision = 8, scale = 4)
    private BigDecimal unrealizedPnlPercent;

    @Column(precision = 15, scale = 4)
    private BigDecimal realizedPnl;        // filled on close

    // ─── Dates ────────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate entryDate;

    private LocalDate exitDate;            // null if still open

    private LocalDate targetExitDate;      // from recommendation

    // ─── Linked Recommendation ────────────────────────────────────────────────
    @Column(length = 36)
    private String recommendationId;       // FK to TradeRecommendation

    // ─── Status ───────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 20)
    private String status;                 // "OPEN" / "CLOSED" / "STOPPED_OUT" / "TARGET_HIT"

    @Column(length = 20)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    // ─── Helpers ──────────────────────────────────────────────────────────────
    public boolean isOpen()    { return "OPEN".equals(status); }
    public boolean isClosed()  { return "CLOSED".equals(status); }
    public boolean isProfitable() {
        return unrealizedPnl != null && unrealizedPnl.compareTo(BigDecimal.ZERO) > 0;
    }
}