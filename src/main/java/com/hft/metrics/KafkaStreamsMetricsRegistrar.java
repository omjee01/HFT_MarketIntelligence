package com.hft.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Binds Kafka Streams internal metrics to the Micrometer registry so they
 * appear in /actuator/metrics and are scraped by Prometheus.
 *
 * Key metrics exposed:
 *   kafka.stream.thread.poll.records.avg     — records per poll cycle
 *   kafka.stream.thread.process.records.rate — processing throughput
 *   kafka.consumer.fetch.manager.records.lag — consumer lag per partition
 *   kafka.stream.task.process.latency.avg    — per-task processing time
 *   kafka.stream.state.store.size            — RocksDB state store size
 *
 * Binding is deferred to ApplicationReadyEvent because KafkaStreams may not
 * be started at bean construction time (auto-startup=false in dev).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStreamsMetricsRegistrar {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;
    private final MeterRegistry             meterRegistry;

    private KafkaStreamsMetrics metricsBinding;

    @EventListener(ApplicationReadyEvent.class)
    public void bindMetrics() {
        KafkaStreams streams = streamsBuilderFactoryBean.getKafkaStreams();
        if (streams == null) {
            log.debug("[Metrics] KafkaStreams not started (auto-startup=false) — metrics binding skipped");
            return;
        }
        metricsBinding = new KafkaStreamsMetrics(streams);
        metricsBinding.bindTo(meterRegistry);
        log.info("[Metrics] Kafka Streams metrics registered with Micrometer ({})",
                meterRegistry.getClass().getSimpleName());
    }

    @PreDestroy
    void close() {
        if (metricsBinding != null) {
            try {
                metricsBinding.close();
            } catch (Exception e) {
                log.warn("[Metrics] Error closing KafkaStreamsMetrics: {}", e.getMessage());
            }
        }
    }
}