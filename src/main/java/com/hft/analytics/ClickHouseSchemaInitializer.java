package com.hft.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the ClickHouse analytics schema on boot (hft.clickhouse.enabled=true only —
 * "docker" and "prod" profiles). MergeTree, partitioned/ordered by time — the standard
 * ClickHouse layout for append-only time-series financial data.
 *
 * trade_signals is wired end-to-end: {@link ClickHouseSignalSink} inserts into it live
 * off the signals-ml-scored Kafka topic. candles_1m and market_ticks are schema-only —
 * ready for a future OHLCV/tick producer, nothing writes to them yet.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hft.clickhouse.enabled", havingValue = "true")
public class ClickHouseSchemaInitializer implements ApplicationRunner {

    private static final String CREATE_TRADE_SIGNALS = """
            CREATE TABLE IF NOT EXISTS trade_signals (
                id                       String,
                symbol                   String,
                market                   String,
                asset_type               String,
                signal                   String,
                time_horizon             String,
                risk_level               String,
                current_price            Decimal64(4),
                entry_price              Decimal64(4),
                target_price             Decimal64(4),
                stop_loss_price          Decimal64(4),
                expected_profit_percent  Float64,
                risk_reward_ratio        Float64,
                composite_score          Float64,
                confidence_percent       Float64,
                technical_score          Nullable(Float64),
                fundamental_score        Nullable(Float64),
                sentiment_score          Nullable(Float64),
                macro_score              Nullable(Float64),
                ml_score                 Nullable(Float64),
                key_reasons              String,
                key_risks                String,
                status                   String,
                generated_at             DateTime64(3),
                ingested_at              DateTime64(3) DEFAULT now64(3)
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(generated_at)
            ORDER BY (symbol, generated_at)
            """;

    private static final String CREATE_CANDLES_1M = """
            CREATE TABLE IF NOT EXISTS candles_1m (
                symbol       String,
                market       String,
                bar_time     DateTime64(3),
                open         Decimal64(4),
                high         Decimal64(4),
                low          Decimal64(4),
                close        Decimal64(4),
                volume       UInt64,
                ingested_at  DateTime64(3) DEFAULT now64(3)
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(bar_time)
            ORDER BY (symbol, bar_time)
            """;

    private static final String CREATE_MARKET_TICKS = """
            CREATE TABLE IF NOT EXISTS market_ticks (
                symbol       String,
                market       String,
                price        Decimal64(4),
                volume       UInt64,
                source       String,
                tick_time    DateTime64(3),
                ingested_at  DateTime64(3) DEFAULT now64(3)
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(tick_time)
            ORDER BY (symbol, tick_time)
            """;

    private final JdbcTemplate clickHouseJdbcTemplate;

    public ClickHouseSchemaInitializer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        clickHouseJdbcTemplate.execute(CREATE_TRADE_SIGNALS);
        clickHouseJdbcTemplate.execute(CREATE_CANDLES_1M);
        clickHouseJdbcTemplate.execute(CREATE_MARKET_TICKS);
        log.info("[ClickHouse] Schema ready: trade_signals (wired via ClickHouseSignalSink), " +
                 "candles_1m + market_ticks (schema-only, not yet producer-wired)");
    }
}
