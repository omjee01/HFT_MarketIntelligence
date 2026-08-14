package com.hft.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.config.CacheConfig;
import com.hft.model.domain.SentimentData;
import com.hft.model.enums.Market;
import com.hft.model.enums.SentimentLabel;
import com.hft.util.SentimentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Sentiment Analysis Service — aggregates news and social media sentiment per ticker.
 *
 * Data Sources (Phase 1, using available free APIs):
 *  - Alpha Vantage News API (free with API key)
 *  - NewsAPI.org (optional, enable with NEWSAPI_ENABLED=true)
 *
 * Phase 2 will add:
 *  - Twitter/X API cashtag monitoring
 *  - Reddit PRAW API (r/investing, r/wallstreetbets, r/IndiaInvestments)
 *  - StockTwits sentiment feed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SentimentUtil sentimentUtil;

    @Value("${hft.alpha-vantage.api-key}")
    private String alphaVantageKey;

    @Value("${hft.alpha-vantage.base-url}")
    private String alphaVantageUrl;

    @Value("${hft.newsapi.api-key:}")
    private String newsApiKey;

    @Value("${hft.newsapi.base-url:https://newsapi.org/v2}")
    private String newsApiUrl;

    @Value("${hft.newsapi.enabled:false}")
    private boolean newsApiEnabled;

    /**
     * Compute sentiment score for a symbol from news + social sources.
     * Cached for 15 minutes.
     */
    @Cacheable(value = CacheConfig.CACHE_SENTIMENT, key = "#symbol + '_' + #market.name()")
    public SentimentData analyzeSentiment(String symbol, Market market) {
        log.debug("[Sentiment] Analyzing: {} on {}", symbol, market);

        List<String> headlines = new ArrayList<>();
        int positiveCount = 0, negativeCount = 0, neutralCount = 0;

        // ── Alpha Vantage News ────────────────────────────────────────────────
        List<String> avHeadlines = fetchAlphaVantageNews(symbol);
        headlines.addAll(avHeadlines);

        // ── NewsAPI ───────────────────────────────────────────────────────────
        if (newsApiEnabled && !newsApiKey.isBlank()) {
            headlines.addAll(fetchNewsApi(symbol, market));
        }

        // Score each headline
        double newsSentimentTotal = 0;
        for (String headline : headlines) {
            double s = sentimentUtil.score(headline);
            newsSentimentTotal += s;
            if (s > 0.1)       positiveCount++;
            else if (s < -0.1) negativeCount++;
            else               neutralCount++;
        }

        double newsSentiment = headlines.isEmpty() ? 0 :
                               newsSentimentTotal / headlines.size();

        // Social media sentiment (Phase-1: simulated score based on news trend)
        // Phase-2: will replace with real Twitter/Reddit data
        double socialSentiment = newsSentiment * 0.8 + (Math.random() - 0.5) * 0.2;

        // Weighted aggregate
        double overall = (newsSentiment * 0.6) + (socialSentiment * 0.4);
        overall = Math.max(-1.0, Math.min(1.0, overall));

        return SentimentData.builder()
                .symbol(symbol)
                .market(market)
                .overallSentimentScore(round(overall))
                .newsSentimentScore(round(newsSentiment))
                .socialSentimentScore(round(socialSentiment))
                .sentimentLabel(SentimentLabel.fromScore(overall))
                .totalMentions24h(headlines.size())
                .newsArticles24h(headlines.size())
                .mentionTrend(overall > 0.2 ? "RISING" : overall < -0.2 ? "FALLING" : "STABLE")
                .isTrending(headlines.size() > 10)
                .positiveNewsCount(positiveCount)
                .negativeNewsCount(negativeCount)
                .neutralNewsCount(neutralCount)
                .keyHeadlines(headlines.subList(0, Math.min(5, headlines.size())))
                .hasFedMention(containsFedMention(headlines))
                .hasRbiMention(containsRbiMention(headlines))
                .hasPoliticalRisk(containsPoliticalRisk(headlines))
                .computedAt(LocalDateTime.now())
                .windowPeriod("24H")
                .build();
    }

    // ─── Alpha Vantage News API ────────────────────────────────────────────────

    private List<String> fetchAlphaVantageNews(String symbol) {
        String url = String.format("%s?function=NEWS_SENTIMENT&tickers=%s&limit=20&apikey=%s",
                alphaVantageUrl, symbol, alphaVantageKey);
        List<String> headlines = new ArrayList<>();
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return headlines;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode feed = root.path("feed");
                if (feed.isArray()) {
                    for (JsonNode article : feed) {
                        String title = article.path("title").asText("");
                        if (!title.isBlank()) headlines.add(title);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Sentiment] Alpha Vantage news failed for {}: {}", symbol, e.getMessage());
        }
        return headlines;
    }

    // ─── NewsAPI ──────────────────────────────────────────────────────────────

    private List<String> fetchNewsApi(String symbol, Market market) {
        String query = symbol + (market.isIndian() ? " NSE India" : " stock");
        String url = String.format("%s/everything?q=%s&language=en&sortBy=publishedAt&pageSize=20&apiKey=%s",
                newsApiUrl, query.replace(" ", "%20"), newsApiKey);
        List<String> headlines = new ArrayList<>();
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return headlines;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode articles = root.path("articles");
                if (articles.isArray()) {
                    for (JsonNode article : articles) {
                        String title = article.path("title").asText("");
                        if (!title.isBlank()) headlines.add(title);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Sentiment] NewsAPI failed for {}: {}", symbol, e.getMessage());
        }
        return headlines;
    }

    // ─── Scheduled refresh (every 15 min during market hours) ────────────────

    @Scheduled(cron = "${hft.scheduler.sentiment-refresh-cron:0 */15 * * * *}")
    @Async("analysisExecutor")
    public void refreshSentimentCache() {
        log.debug("[Sentiment] Scheduled sentiment refresh triggered");
        // The actual refresh happens on-demand via cache eviction + re-call
        // For production: pre-warm cache for watchlist symbols
    }

    // ─── Keyword Detection ────────────────────────────────────────────────────

    private boolean containsFedMention(List<String> headlines) {
        return headlines.stream().anyMatch(h -> h.toLowerCase().matches(
                ".*(federal reserve|fed meeting|fed rate|powell|fomc|rate hike|rate cut).*"));
    }

    private boolean containsRbiMention(List<String> headlines) {
        return headlines.stream().anyMatch(h -> h.toLowerCase().matches(
                ".*(rbi|reserve bank|monetary policy|repo rate|shaktikanta das).*"));
    }

    private boolean containsPoliticalRisk(List<String> headlines) {
        return headlines.stream().anyMatch(h -> h.toLowerCase().matches(
                ".*(war|sanction|tariff|trade war|election|geopolit|conflict|crisis).*"));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}