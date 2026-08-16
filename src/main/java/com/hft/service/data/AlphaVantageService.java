package com.hft.service.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Alpha Vantage API integration — primary data source for US markets.
 *
 * Free tier: 25 API calls/day (use demo key for testing).
 * Premium tier: Unlimited calls per minute.
 *
 * API Docs: https://www.alphavantage.co/documentation/
 *
 * Set key via: export ALPHA_VANTAGE_API_KEY=your_key
 * or in application-dev.yml: hft.alpha-vantage.api-key: your_key
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlphaVantageService implements MarketDataService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AlphaVantageBudgetGuard budgetGuard;

    @Value("${hft.alpha-vantage.api-key}")
    private String apiKey;

    @Value("${hft.alpha-vantage.base-url}")
    private String baseUrl;

    @Override
    public boolean supports(Market market) {
        return market.isUS();
    }

    @Override
    public String getProviderName() {
        return "Alpha Vantage";
    }

    // ─── GLOBAL QUOTE ─────────────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = "alphaVantage", fallbackMethod = "getQuoteFallback")
    @RateLimiter(name = "alphaVantage")
    public Optional<StockQuote> getQuote(String symbol, Market market) {
        if (!budgetGuard.tryConsume()) {
            return Optional.empty();
        }
        String url = String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                baseUrl, symbol, apiKey);
        try {
            String json = executeGet(url);
            return parseGlobalQuote(json, symbol, market);
        } catch (Exception e) {
            log.error("[AlphaVantage] Failed to fetch quote for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<StockQuote> getQuoteFallback(String symbol, Market market, Throwable t) {
        log.warn("[AlphaVantage] Circuit open for quote {}, returning empty. Cause: {}", symbol, t.getMessage());
        return Optional.empty();
    }

    private Optional<StockQuote> parseGlobalQuote(String json, String symbol, Market market) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode quote = root.path("Global Quote");
        if (quote.isMissingNode() || quote.isEmpty()) {
            log.warn("[AlphaVantage] Empty quote response for {}. API limit may be hit.", symbol);
            return Optional.empty();
        }

        BigDecimal price     = bd(quote, "05. price");
        BigDecimal open      = bd(quote, "02. open");
        BigDecimal high      = bd(quote, "03. high");
        BigDecimal low       = bd(quote, "04. low");
        BigDecimal prevClose = bd(quote, "08. previous close");
        BigDecimal change    = bd(quote, "09. change");
        double changePct     = pct(quote, "10. change percent");
        long volume          = lng(quote, "06. volume");

        StockQuote sq = StockQuote.builder()
                .symbol(symbol)
                .companyName(symbol)         // Alpha Vantage GLOBAL_QUOTE doesn't return company name
                .market(market)
                .assetType(AssetType.STOCK)
                .currentPrice(price)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .previousClose(prevClose)
                .dayChange(change)
                .dayChangePercent(BigDecimal.valueOf(changePct))
                .volume(volume)
                .currency("USD")
                .timestamp(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .dataSource("Alpha Vantage")
                .isMarketOpen(true)
                .build();
        return Optional.of(sq);
    }

    // ─── HISTORICAL DATA ──────────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = "alphaVantage", fallbackMethod = "getHistoricalFallback")
    public List<OHLCVData> getHistoricalData(String symbol, Market market,
                                              LocalDate from, LocalDate to,
                                              String interval) {
        if (!budgetGuard.tryConsume()) {
            return Collections.emptyList();
        }
        // Choose function based on interval
        String function = "TIME_SERIES_DAILY_ADJUSTED";
        String outputSize = "full"; // 20 years of data

        String url = String.format("%s?function=%s&symbol=%s&outputsize=%s&apikey=%s",
                baseUrl, function, symbol, outputSize, apiKey);
        try {
            String json = executeGet(url);
            return parseTimeSeries(json, symbol, market, from, to, "1D");
        } catch (Exception e) {
            log.error("[AlphaVantage] Failed to fetch history for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<OHLCVData> getHistoricalFallback(String symbol, Market market,
                                                    LocalDate from, LocalDate to,
                                                    String interval, Throwable t) {
        log.warn("[AlphaVantage] Circuit open for history {}", symbol);
        return Collections.emptyList();
    }

    private List<OHLCVData> parseTimeSeries(String json, String symbol, Market market,
                                             LocalDate from, LocalDate to, String interval) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode timeSeries = root.path("Time Series (Daily)");
        if (timeSeries.isMissingNode()) {
            log.warn("[AlphaVantage] No time-series data for {}", symbol);
            return Collections.emptyList();
        }

        List<OHLCVData> bars = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            LocalDate date = LocalDate.parse(entry.getKey());
            if (date.isBefore(from) || date.isAfter(to)) continue;

            JsonNode bar = entry.getValue();
            OHLCVData ohlcv = OHLCVData.builder()
                    .symbol(symbol)
                    .market(market)
                    .barDate(date)
                    .intervalType(interval)
                    .open(bd(bar, "1. open"))
                    .high(bd(bar, "2. high"))
                    .low(bd(bar, "3. low"))
                    .close(bd(bar, "4. close"))
                    .adjustedClose(bd(bar, "5. adjusted close"))
                    .volume(lng(bar, "6. volume"))
                    .fetchedAt(LocalDateTime.now())
                    .dataSource("Alpha Vantage")
                    .build();
            bars.add(ohlcv);
        }

        // Sort OLDEST → NEWEST (required by TA engine)
        bars.sort(Comparator.comparing(OHLCVData::getBarDate));
        return bars;
    }

    @Override
    public List<StockQuote> getBatchQuotes(List<String> symbols, Market market) {
        List<StockQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            getQuote(symbol, market).ifPresent(quotes::add);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}  // respect rate limit
        }
        return quotes;
    }

    // ─── HTTP Helper ──────────────────────────────────────────────────────────

    private String executeGet(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("HTTP " + response.code() + " for: " + url);
            }
            return response.body().string();
        }
    }

    // ─── JSON Parsing Helpers ──────────────────────────────────────────────────

    private BigDecimal bd(JsonNode node, String field) {
        String val = node.path(field).asText("0").replace("%", "").trim();
        try { return new BigDecimal(val); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private double pct(JsonNode node, String field) {
        String val = node.path(field).asText("0").replace("%", "").trim();
        try { return Double.parseDouble(val); }
        catch (Exception e) { return 0.0; }
    }

    private long lng(JsonNode node, String field) {
        return node.path(field).asLong(0);
    }
}