package com.hft.backtest;

import com.hft.model.enums.Market;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks one backtest execution — configuration, live progress, and final results.
 *
 * Lifecycle:
 *   PENDING  → created but not yet started (async scheduling gap)
 *   RUNNING  → BacktestRunner is actively processing bars
 *   COMPLETE → all bars processed, metrics computed
 *   FAILED   → exception during processing
 */
@Entity
@Table(name = "backtest_runs",
       indexes = {
           @Index(name = "idx_btr_market",  columnList = "market"),
           @Index(name = "idx_btr_started", columnList = "started_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRun {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    // ─── Configuration ────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "backtest_run_symbols",
                     joinColumns = @JoinColumn(name = "run_id"))
    @Column(name = "symbol", length = 30)
    @Builder.Default
    private List<String> symbols = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(length = 10)
    private String modelVariant;        // "A" or "B"

    @Column(precision = 20, scale = 2)
    private BigDecimal initialCapital;

    @Column
    private Boolean walkForward;

    @Column
    private Integer walkForwardWindows;

    // ─── Status & Progress ────────────────────────────────────────────────────
    @Column(nullable = false, length = 20)
    private String status;              // PENDING / RUNNING / COMPLETE / FAILED

    @Column
    private Integer progressPercent;    // 0–100, updated during RUNNING

    @Column
    private Integer totalBarsProcessed;

    @Column
    private Integer totalTradesSimulated;

    @Column(length = 500)
    private String failureReason;

    // ─── Timestamps ───────────────────────────────────────────────────────────
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    // ─── Results ─────────────────────────────────────────────────────────────
    @Embedded
    private BacktestMetrics metrics;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BacktestTrade> trades = new ArrayList<>();
}