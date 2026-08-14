package com.hft.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Multi-layer cache configuration.
 *
 * L1: Caffeine (in-process, ultra-fast, ~100ns)
 * L2: Redis   (distributed, ~1ms)  — configured via spring.data.redis in yml
 *
 * Cache name constants are defined here and used as @Cacheable("name") everywhere.
 */
@Configuration
public class CacheConfig {

    // ─── Cache Name Constants ──────────────────────────────────────────────────
    public static final String CACHE_QUOTES           = "quotes";           // TTL 30s
    public static final String CACHE_TA               = "technical-analysis";// TTL 5min
    public static final String CACHE_SENTIMENT        = "sentiment";         // TTL 15min
    public static final String CACHE_RECOMMENDATIONS  = "recommendations";   // TTL 5min
    public static final String CACHE_FUNDAMENTAL      = "fundamental";       // TTL 24h
    public static final String CACHE_MACRO            = "macro";             // TTL 1h
    public static final String CACHE_IPO              = "ipo";               // TTL 30min

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Different TTLs per cache
        manager.registerCustomCache(CACHE_QUOTES,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(10_000).build());

        manager.registerCustomCache(CACHE_TA,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(5_000).build());

        manager.registerCustomCache(CACHE_SENTIMENT,
                Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(2_000).build());

        manager.registerCustomCache(CACHE_RECOMMENDATIONS,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(500).build());

        manager.registerCustomCache(CACHE_FUNDAMENTAL,
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(5_000).build());

        manager.registerCustomCache(CACHE_MACRO,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(10).build());

        manager.registerCustomCache(CACHE_IPO,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(200).build());

        return manager;
    }
}