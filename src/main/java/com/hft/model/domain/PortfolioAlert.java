package com.hft.model.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A notification raised by PortfolioMonitorService against an open PortfolioPosition —
 * target hit, stop-loss hit, or the underlying signal deteriorating. Stage 13.
 */
@Entity
@Table(name = "portfolio_alerts",
       indexes = {
           @Index(name = "idx_alert_username", columnList = "username"),
           @Index(name = "idx_alert_position", columnList = "position_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAlert {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "position_id", nullable = false, length = 36)
    private String positionId;

    @Column(nullable = false, length = 30)
    private String symbol;

    // TARGET_HIT / STOP_LOSS_HIT / SIGNAL_DETERIORATED
    @Column(nullable = false, length = 30)
    private String alertType;

    // SELL / HOLD / REVIEW — what the user should consider doing about it
    @Column(nullable = false, length = 20)
    private String suggestedAction;

    @Column(nullable = false, length = 400)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean acknowledged;
}
