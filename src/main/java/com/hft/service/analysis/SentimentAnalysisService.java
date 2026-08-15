package com.hft.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.admin.PlatformCredentialProvider;
import com.hft.admin.PlatformSettingsService;
import com.hft.config.CacheConfig;
import com.hft.model.domain.SentimentData;
import com.hft.model.enums.Market;
import com.hft.model.enums.SentimentLabel;
import com.hft.util.SentimentUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.FormBody;
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
 * Data Sources (Phase 0, real integrations):
 *  - Alpha Vantage News API (free with API key)
 *  - NewsAPI.org (optional, enable with NEWSAPI_ENABLED=true)
 *  - SEC EDGAR full-text search (US only, free, no key — company filings as news signal)
 *  - Reddit (app-only OAuth2, free tier — real social sentiment, replaces Phase-1 Math.random())
 *  - StockTwits public API (free, no key — see fetchStockTwitsScores() note: currently returns
 *    a Cloudflare bot-challenge page rather than JSON when called from this environment; wired
 *    up and safe to leave enabled since a non-JSON response fails closed to an empty list, but
 *    does not currently contribute real data — needs re-verification from the deploy environment)
 *
 * Twitter/X is deliberately not implemented — no free API tier (budget decision, see
 * HFT_ARCHITECTURE.md §24.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private static final String REDDIT_USER_AGENT = "HFT-Market-Intelligence-Platform/1.0 (by /u/hmip_research)";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SentimentUtil sentimentUtil;
    private final PlatformSettingsService platformSettingsService;

    private volatile String redditAccessToken;
    private volatile long redditTokenExpiryEpochMs = 0L;

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

    @Value("${hft.reddit.client-id:}")
    private String redditClientId;

    @Value("${hft.reddit.client-secret:}")
    private String redditClientSecret;

    @Value("${hft.reddit.enabled:false}")
    private boolean redditEnabled;

    @Value("${hft.stocktwits.base-url:https://api.stocktwits.com/api/2}")
    private String stocktwitsBaseUrl;

    @Value("${hft.stocktwits.enabled:false}")
    private boolean stocktwitsEnabled;

    @Value("${hft.sec-edgar.base-url:https://efts.sec.gov/LATEST/search-index}")
    private String secEdgarBaseUrl;

    @Value("${hft.sec-edgar.user-agent:HFT-Market-Intelligence-Platform research@hmip.local}")
    private String secEdgarUserAgent;

    @Value("${hft.sec-edgar.enabled:false}")
    private boolean secEdgarEnabled;

    // ─── Admin-settings overrides (com.hft.admin) — checked before the @Value default ──

    private String resolveNewsApiKey() {
        return platformSettingsService.getOverride(PlatformCredentialProvider.NEWSAPI, "apiKey").orElse(newsApiKey);
    }

    private String resolveRedditClientId() {
        return platformSettingsService.getOverride(PlatformCredentialProvider.REDDIT, "clientId").orElse(redditClientId);
    }

    private String resolveRedditClientSecret() {
        return platformSettingsService.getOverride(PlatformCredentialProvider.REDDIT, "clientSecret").orElse(redditClientSecret);
    }

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
        if (newsApiEnabled && !resolveNewsApiKey().isBlank()) {
            headlines.addAll(fetchNewsApi(symbol, market));
        }

        // ── SEC EDGAR filings (US only) — company-history / regulatory signal ──
        headlines.addAll(fetchSecEdgarFilings(symbol, market));

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

        // ── Social sentiment: Reddit + StockTwits (real data — replaces Phase-1 Math.random()) ──
        List<String> redditPosts = fetchRedditPosts(symbol, market);
        List<Double> stockTwitsScores = fetchStockTwitsScores(symbol);

        List<Double> socialScores = new ArrayList<>();
        for (String post : redditPosts) socialScores.add(sentimentUtil.score(post));
        socialScores.addAll(stockTwitsScores);

        double socialSentiment;
        if (!socialScores.isEmpty()) {
            double sum = 0;
            for (double s : socialScores) sum += s;
            socialSentiment = sum / socialScores.size();
        } else {
            // No social source configured/reachable this pass (e.g. Reddit credentials not
            // supplied). Lean on the news trend rather than inventing noise — an honest
            // "no independent signal" fallback, not a claim of real social data.
            socialSentiment = newsSentiment * 0.8;
        }

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
                .totalMentions24h(headlines.size() + socialScores.size())
                .newsArticles24h(headlines.size())
                .redditMentions24h(redditPosts.size())
                .stockTwitsMentions24h(stockTwitsScores.size())
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
                newsApiUrl, query.replace(" ", "%20"), resolveNewsApiKey());
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

    // ─── SEC EDGAR full-text search (US only, free, no key) ───────────────────
    // Verified reachable and returning real JSON against efts.sec.gov during development.

    @CircuitBreaker(name = "secEdgar")
    private List<String> fetchSecEdgarFilings(String symbol, Market market) {
        List<String> items = new ArrayList<>();
        if (!secEdgarEnabled || market.isIndian()) return items;
        try {
            String url = String.format("%s?q=%s&forms=8-K,10-K,10-Q",
                    secEdgarBaseUrl, symbol);
            Request request = new Request.Builder().url(url).get()
                    .header("User-Agent", secEdgarUserAgent)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return items;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode hits = root.path("hits").path("hits");
                if (hits.isArray()) {
                    for (JsonNode hit : hits) {
                        JsonNode src = hit.path("_source");
                        String form = src.path("form").asText("");
                        JsonNode names = src.path("display_names");
                        String company = names.isArray() && names.size() > 0
                                ? names.get(0).asText(symbol) : symbol;
                        if (!form.isBlank()) {
                            items.add(company + " filed " + form + " with the SEC");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Sentiment] SEC EDGAR fetch failed for {}: {}", symbol, e.getMessage());
        }
        return items;
    }

    // ─── Reddit (app-only OAuth2 client-credentials flow) ─────────────────────
    // Token endpoint verified reachable (returns real 401 on unauthenticated calls).
    // Full flow requires real hft.reddit.client-id/client-secret from the user — see report.

    private synchronized String getRedditAccessToken() {
        if (redditAccessToken != null && System.currentTimeMillis() < redditTokenExpiryEpochMs) {
            return redditAccessToken;
        }
        try {
            FormBody body = new FormBody.Builder().add("grant_type", "client_credentials").build();
            Request request = new Request.Builder()
                    .url("https://www.reddit.com/api/v1/access_token")
                    .header("Authorization", Credentials.basic(resolveRedditClientId(), resolveRedditClientSecret()))
                    .header("User-Agent", REDDIT_USER_AGENT)
                    .post(body)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[Sentiment] Reddit auth failed: HTTP {}", response.code());
                    return null;
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                String token = root.path("access_token").asText(null);
                int expiresIn = root.path("expires_in").asInt(3600);
                if (token != null) {
                    redditAccessToken = token;
                    redditTokenExpiryEpochMs = System.currentTimeMillis() + (long) (expiresIn - 60) * 1000L;
                }
                return token;
            }
        } catch (Exception e) {
            log.warn("[Sentiment] Reddit auth request failed: {}", e.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = "reddit")
    private List<String> fetchRedditPosts(String symbol, Market market) {
        List<String> posts = new ArrayList<>();
        if (!redditEnabled || resolveRedditClientId().isBlank() || resolveRedditClientSecret().isBlank()) return posts;
        String token = getRedditAccessToken();
        if (token == null) return posts;

        List<String> subreddits = market.isIndian()
                ? List.of("IndiaInvestments")
                : List.of("investing", "stocks", "wallstreetbets");

        for (String subreddit : subreddits) {
            try {
                String url = String.format(
                        "https://oauth.reddit.com/r/%s/search.json?q=%s&restrict_sr=true&sort=new&limit=25",
                        subreddit, symbol);
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", REDDIT_USER_AGENT)
                        .get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;
                    JsonNode root = objectMapper.readTree(response.body().string());
                    JsonNode children = root.path("data").path("children");
                    if (children.isArray()) {
                        for (JsonNode child : children) {
                            JsonNode d = child.path("data");
                            String title = d.path("title").asText("");
                            String selftext = d.path("selftext").asText("");
                            if (!title.isBlank()) posts.add(title);
                            if (!selftext.isBlank() && selftext.length() < 500) posts.add(selftext);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[Sentiment] Reddit fetch failed for {} on r/{}: {}", symbol, subreddit, e.getMessage());
            }
        }
        return posts;
    }

    // ─── StockTwits (free public API, no key) ──────────────────────────────────
    // NOTE: verified during development that this endpoint currently returns a Cloudflare
    // "Just a moment..." JS-challenge page to plain server-side HTTP clients, not JSON — a
    // headless browser would be needed to pass it, which is out of scope here. Left wired up
    // and enabled since a non-JSON response fails closed to an empty list (see catch block),
    // but does not currently contribute real data. Needs re-verification from the actual
    // deploy environment before relying on it.

    @CircuitBreaker(name = "stocktwits")
    private List<Double> fetchStockTwitsScores(String symbol) {
        List<Double> scores = new ArrayList<>();
        if (!stocktwitsEnabled) return scores;
        try {
            String url = stocktwitsBaseUrl + "/streams/symbol/" + symbol + ".json";
            Request request = new Request.Builder().url(url).get()
                    .header("User-Agent", "HFT-Market-Intelligence-Platform/1.0")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return scores;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode messages = root.path("messages");
                if (messages.isArray()) {
                    for (JsonNode msg : messages) {
                        String basic = msg.path("entities").path("sentiment").path("basic").asText("");
                        String bodyText = msg.path("body").asText("");
                        if ("Bullish".equalsIgnoreCase(basic))      scores.add(1.0);
                        else if ("Bearish".equalsIgnoreCase(basic)) scores.add(-1.0);
                        else if (!bodyText.isBlank())               scores.add(sentimentUtil.score(bodyText));
                    }
                }
            }
        } catch (Exception e) {
            // Expected in practice right now — see class-level note: Cloudflare challenge
            // page fails JSON parsing and lands here, which correctly degrades to "no data".
            log.warn("[Sentiment] StockTwits fetch failed for {} (see class doc — likely bot-challenge): {}",
                    symbol, e.getMessage());
        }
        return scores;
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