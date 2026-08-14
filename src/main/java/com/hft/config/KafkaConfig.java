package com.hft.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions for the HFT event streaming pipeline.
 * Topics are auto-created on application startup (if they don't exist).
 */
@Configuration
public class KafkaConfig {

    // ─── Topic Name Constants (use these everywhere) ─────────────────────────
    public static final String TOPIC_MARKET_DATA_RAW   = "market-data-raw";
    public static final String TOPIC_TA_RESULTS        = "ta-results";
    public static final String TOPIC_SENTIMENT_SCORES  = "sentiment-scores";
    public static final String TOPIC_MACRO_DATA        = "macro-data";
    public static final String TOPIC_ML_PREDICTIONS    = "ml-predictions";
    public static final String TOPIC_SIGNALS           = "trading-signals";
    public static final String TOPIC_RECOMMENDATIONS   = "recommendations";
    public static final String TOPIC_ALERTS            = "alerts";
    public static final String TOPIC_NEWS_RAW          = "news-raw";
    public static final String TOPIC_SOCIAL_RAW        = "social-raw";

    @Bean
    public NewTopic marketDataRawTopic() {
        return TopicBuilder.name(TOPIC_MARKET_DATA_RAW)
                .partitions(64)   // partitioned by ticker hash for parallelism
                .replicas(1)      // increase to 3 in production cluster
                .build();
    }

    @Bean
    public NewTopic taResultsTopic() {
        return TopicBuilder.name(TOPIC_TA_RESULTS)
                .partitions(32)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sentimentScoresTopic() {
        return TopicBuilder.name(TOPIC_SENTIMENT_SCORES)
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic macroDataTopic() {
        return TopicBuilder.name(TOPIC_MACRO_DATA)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic mlPredictionsTopic() {
        return TopicBuilder.name(TOPIC_ML_PREDICTIONS)
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradingSignalsTopic() {
        return TopicBuilder.name(TOPIC_SIGNALS)
                .partitions(16)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic recommendationsTopic() {
        return TopicBuilder.name(TOPIC_RECOMMENDATIONS)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(TOPIC_ALERTS)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic newsRawTopic() {
        return TopicBuilder.name(TOPIC_NEWS_RAW)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic socialRawTopic() {
        return TopicBuilder.name(TOPIC_SOCIAL_RAW)
                .partitions(16)
                .replicas(1)
                .build();
    }
}