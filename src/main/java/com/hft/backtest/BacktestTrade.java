package com.hft.backtest;

import com.hft.model.enums.SignalType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single simulated trade within a BacktestRun.
 * One record per BUY/STRONG_BUY signal that was acted on.
 */
@Entity
@Table(name = "backtest_trades",
       indexes = {
           @Index(name = "idx_bt_run",    columnList = "run_id"),
           @Index(name = "idx_bt_symbol", columnList = "symbol")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BacktestRun run;

    @Column(nullable = false, length = 30)
    private String symbol;

    // Explicit column name: "signal" is a MySQL reserved word (the SIGNAL statement).
    // H2 doesn't reserve it, so this was silently fine until MySQL.
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 20)
    private SignalType signal;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column
    private LocalDate exitDate;

    @Column(precision = 15, scale = 4)
    private BigDecimal exitPrice;

    /**
     * Why the trade was closed:
     *   TARGET_HIT  — price reached predictedTargetPrice
     *   STOP_HIT    — price fell to stopLossPrice
     *   TIME_EXPIRY — holding period elapsed without hitting either level
     */
    @Column(length = 20)
    private String exitReason;

    @Column
    private Double returnPercent;        // (exitPrice – entryPrice) / entryPrice × 100

    @Column
    private Boolean profitable;

    @Column
    private Integer holdingDays;

    @Column
    private Double compositeScore;       // ML score at entry signal

    @Column
    private Double confidencePercent;

    @Column(precision = 15, scale = 4)
    private BigDecimal targetPrice;

    @Column(precision = 15, scale = 4)
    private BigDecimal stopLossPrice;
}