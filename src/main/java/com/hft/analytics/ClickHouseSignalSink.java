package com.hft.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.config.KafkaConfig;
import com.hft.model.domain.TradeRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * Mirrors every ML-scored trading signal into ClickHouse for fast analytical
 * read/write (transaction/signal history) — the "unstructured financial data"
 * store separate from the primary RDBMS. A dedicated consumer group
 * (hft-clickhouse-sink) so it never interferes with Processor 5's own
 * signals-ml-scored -> backtest-results passthrough (KafkaStreamsTopology).
 *
 * Best-effort: a failed insert is logged and dropped, not retried — this is an
 * analytics mirror, not the system of record (TradeRecommendation in the
 * primary DB remains authoritative).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hft.clickhouse.enabled", havingValue = "true")
public class ClickHouseSignalSink {

    private static final String INSERT_SQL = """
            INSERT INTO trade_signals
                (id, symbol, market, asset_type, signal, time_horizon, risk_level,
                 current_price, entry_price, target_price, stop_loss_price,
                 expected_profit_percent, risk_reward_ratio, composite_score, confidence_percent,
                 technical_score, fundamental_score, sentiment_score, macro_score, ml_score,
                 key_reasons, key_risks, status, generated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate clickHouseJdbcTemplate;
    private final ObjectMapper objectMapper;

    public ClickHouseSignalSink(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate,
                                 ObjectMapper objectMapper) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_SIGNALS_ML_SCORED, groupId = "hft-clickhouse-sink")
    public void onSignal(String json) {
        try {
            TradeRecommendation r = objectMapper.readValue(json, TradeRecommendation.class);
            if (r == null || r.getSymbol() == null) return;
            clickHouseJdbcTemplate.update(INSERT_SQL,
                    r.getId(),
                    r.getSymbol(),
                    r.getMarket() != null ? r.getMarket().name() : "",
                    r.getAssetType() != null ? r.getAssetType().name() : "",
                    r.getSignal() != null ? r.getSignal().name() : "",
                    r.getTimeHorizon() != null ? r.getTimeHorizon().name() : "",
                    r.getRiskLevel() != null ? r.getRiskLevel().name() : "",
                    r.getCurrentPrice(), r.getEntryPrice(), r.getTargetPrice(), r.getStopLossPrice(),
                    r.getExpectedProfitPercent(), r.getRiskRewardRatio(),
                    r.getCompositeScore(), r.getConfidencePercent(),
                    r.getTechnicalScore(), r.getFundamentalScore(), r.getSentimentScore(),
                    r.getMacroScore(), r.getMlScore(),
                    joined(r.getKeyReasons()), joined(r.getKeyRisks()),
                    r.getStatus() != null ? r.getStatus() : "",
                    r.getGeneratedAt() != null ? Timestamp.valueOf(r.getGeneratedAt()) : new Timestamp(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("[ClickHouseSignalSink] Dropped one signal — failed to persist: {}", e.getMessage());
        }
    }

    private String joined(List<String> values) {
        return values == null ? "" : String.join("; ", values);
    }
}
