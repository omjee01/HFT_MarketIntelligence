package com.hft.model.domain;

import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IPO-specific data including valuation, subscription details, and listing prediction.
 */
@Entity
@Table(name = "ipo_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IPOData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;               // post-listing symbol

    @Column(nullable = false, length = 120)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(length = 60)
    private String sector;

    @Column(length = 60)
    private String industry;

    // ─── Issue Details ────────────────────────────────────────────────────────
    @Column(precision = 15, scale = 4)
    private BigDecimal issuePriceLow;    // price band lower end

    @Column(precision = 15, scale = 4)
    private BigDecimal issuePriceHigh;   // price band upper end (cutoff)

    @Column(precision = 20, scale = 2)
    private BigDecimal issueSizeCrores;  // total issue size (₹ crores or $ million)

    @Column(precision = 20, scale = 2)
    private BigDecimal freshIssueSize;

    @Column(precision = 20, scale = 2)
    private BigDecimal ofsSize;          // Offer For Sale component

    private Integer lotSize;             // minimum lot for retail

    // ─── Dates ────────────────────────────────────────────────────────────────
    private LocalDate subscriptionOpenDate;
    private LocalDate subscriptionCloseDate;
    private LocalDate allotmentDate;
    private LocalDate listingDate;

    // ─── Subscription Data ────────────────────────────────────────────────────
    private Double retailSubscriptionTimes;   // e.g. 42.3x subscribed
    private Double qibSubscriptionTimes;      // Qualified Institutional Buyer
    private Double niiSubscriptionTimes;      // Non-Institutional Investor (HNI)
    private Double overallSubscriptionTimes;  // total

    private Double gmpPercent;               // Grey Market Premium % (unofficial)

    // ─── Valuation ────────────────────────────────────────────────────────────
    private Double peAtIssuePrice;
    private Double industryPeAvg;
    private Double evToSalesAtIssuePrice;
    private String valuationVerdict;         // "FAIRLY_VALUED" / "OVERVALUED" / "CHEAP"

    // ─── Lead Managers ────────────────────────────────────────────────────────
    @Column(length = 200)
    private String leadManagers;

    private Double leadManagerTrackRecordScore; // historical avg listing gain of this manager

    // ─── ML Prediction ────────────────────────────────────────────────────────
    private Double predictedListingGainPercent; // our ML prediction
    private Double listingGainConfidence;       // 0–100 confidence in prediction

    @Column(length = 20)
    private String recommendation;       // "APPLY_STRONG" / "APPLY" / "AVOID" / "RISKY"

    @Column(length = 500)
    private String recommendationReason;

    // ─── Status ───────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 20)
    private String status;               // "UPCOMING" / "OPEN" / "CLOSED" / "LISTED"

    private Double actualListingGainPercent;   // filled after listing

    @Column(nullable = false)
    private LocalDateTime lastUpdated;
}