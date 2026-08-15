package com.hft.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.domain.TradeRecommendation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Redis Pub/Sub bridge for multi-node deployment.
 *
 * When hft.redis-pubsub.enabled=true, each Kafka Streams event is published
 * to a Redis channel instead of being written to the local StreamSinkBridge directly.
 * All application nodes (including the publisher) subscribe to those channels and push
 * the event into their local StreamSinkBridge. This fans out live feeds across all
 * nodes, enabling horizontal scaling of GraphQL subscriptions and gRPC streaming.
 *
 * Channel layout:
 *   hft:quotes:{symbol}_{market}  → StockQuote ticks
 *   hft:signals                   → TradeRecommendation (enriched)
 *   hft:candles:{symbol}          → OHLCVData (1-min bars)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hft.redis-pubsub.enabled", havingValue = "true")
public class RedisPubSubBridge {

    static final String CH_QUOTES  = "hft:quotes:";
    static final String CH_SIGNALS = "hft:signals";
    static final String CH_CANDLES = "hft:candles:";

    private final StringRedisTemplate    redis;
    private final RedisConnectionFactory connectionFactory;
    private final StreamSinkBridge       sinkBridge;
    private final ObjectMapper           mapper;

    private RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    void subscribeAll() {
        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);

        listenerContainer.addMessageListener(
                listener(StockQuote.class, sinkBridge::emitQuote),
                new PatternTopic(CH_QUOTES + "*"));

        listenerContainer.addMessageListener(
                listener(TradeRecommendation.class, sinkBridge::emitSignal),
                new PatternTopic(CH_SIGNALS));

        listenerContainer.addMessageListener(
                listener(OHLCVData.class, sinkBridge::emitCandle),
                new PatternTopic(CH_CANDLES + "*"));

        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        log.info("[RedisPubSub] Subscribed to hft:quotes:*, hft:signals, hft:candles:*");
    }

    @PreDestroy
    void shutdown() {
        if (listenerContainer != null && listenerContainer.isRunning()) {
            listenerContainer.stop();
            log.info("[RedisPubSub] Listener container stopped");
        }
    }

    // ─── Publish methods (called from KafkaStreamsTopology) ──────────────────

    public void publishQuote(StockQuote quote) {
        if (quote == null || quote.getSymbol() == null || quote.getMarket() == null) return;
        publish(CH_QUOTES + quote.getSymbol() + "_" + quote.getMarket().name(), quote);
    }

    public void publishSignal(TradeRecommendation signal) {
        if (signal == null) return;
        publish(CH_SIGNALS, signal);
    }

    public void publishCandle(OHLCVData candle) {
        if (candle == null || candle.getSymbol() == null) return;
        publish(CH_CANDLES + candle.getSymbol(), candle);
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private void publish(String channel, Object payload) {
        try {
            redis.convertAndSend(channel, mapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("[RedisPubSub] Publish failed on channel {}: {}", channel, e.getMessage());
        }
    }

    private <T> MessageListener listener(Class<T> type, Consumer<T> sink) {
        return (message, pattern) -> {
            try {
                T obj = mapper.readValue(message.getBody(), type);
                if (obj != null) sink.accept(obj);
            } catch (Exception e) {
                log.warn("[RedisPubSub] Deserialize failed for {}: {}", type.getSimpleName(), e.getMessage());
            }
        };
    }
}
