package com.hft.service.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.AssetType;
import com.hft.model.enums.Market;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NSE India unofficial API integration — primary data source for Indian markets.
 *
 * NSE requires cookie-based session headers. We simulate browser requests.
 * Works for real-time quotes (15-min delayed on free tier).
 *
 * API Base: https://www.nseindia.com/api
 * NSE Symbol format: "RELIANCE", "TCS", "HDFCBANK", "INFY"
 *
 * For live streaming data in production, use Zerodha Kite Connect API:
 * https://kite.trade/docs/connect/v3/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NSEIndiaService implements MarketDataService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${hft.nse-india.base-url}")
    private String baseUrl;

    @Value("${hft.nse-india.user-agent}")
    private String userAgent;

    @Override
    public boolean supports(Market market) {
        return market == Market.INDIA_NSE || market == Market.INDIA_BSE;
    }

    @Override
    public String getProviderName() {
        return "NSE India";
    }

    // ─── NSE Quote ────────────────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = "nseIndia", fallbackMethod = "getQuoteFallback")
    public Optional<StockQuote> getQuote(String symbol, Market market) {
        // NSE API requires a session cookie — first hit the main page
        String quoteUrl = baseUrl + "/quote-equity?symbol=" + symbol.toUpperCase();
        try {
            String json = executeGet(quoteUrl);
            return parseNSEQuote(json, symbol, market);
        } catch (Exception e) {
            log.error("[NSE] Failed to fetch quote for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<StockQuote> getQuoteFallback(String symbol, Market market, Throwable t) {
        log.warn("[NSE] Circuit open for {}: {}", symbol, t.getMessage());
        return Optional.empty();
    }

    private Optional<StockQuote> parseNSEQuote(String json, String symbol, Market market) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode priceInfo = root.path("priceInfo");
        JsonNode info = root.path("info");

        if (priceInfo.isMissingNode()) {
            log.warn("[NSE] No priceInfo in response for {}", symbol);
            return Optional.empty();
        }

        BigDecimal ltp = bd(priceInfo, "lastPrice");        // Last Traded Price
        BigDecimal open = bd(priceInfo, "open");
        BigDecimal high = bd(priceInfo, "intraDayHighLow", "max");
        BigDecimal low  = bd(priceInfo, "intraDayHighLow", "min");
        BigDecimal prev = bd(priceInfo, "previousClose");
        double pChange  = priceInfo.path("pChange").asDouble(0.0);
        long volume     = root.path("marketDeptOrderBook")
                              .path("totalSellQuantity").asLong(0);

        String companyName = info.path("companyName").asText(symbol);
        String sector      = info.path("industry").asText("");

        BigDecimal wkHigh = bd(priceInfo, "weekHighLow", "max");
        BigDecimal wkLow  = bd(priceInfo, "weekHighLow", "min");

        BigDecimal marketCap = root.path("securityInfo")
                                   .path("issuedSize").isMissingNode()
                                   ? null
                                   : ltp.multiply(bd(root.path("securityInfo"), "issuedSize"));

        StockQuote sq = StockQuote.builder()
                .symbol(symbol)
                .companyName(companyName)
                .market(market)
                .assetType(AssetType.STOCK)
                .currentPrice(ltp)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .previousClose(prev)
                .dayChange(ltp.subtract(prev))
                .dayChangePercent(BigDecimal.valueOf(pChange))
                .volume(volume)
                .marketCap(marketCap)
                .currency("INR")
                .fiftyTwoWeekHigh(wkHigh)
                .fiftyTwoWeekLow(wkLow)
                .sector(sector)
                .timestamp(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .dataSource("NSE India")
                .isMarketOpen(true)
                .build();

        return Optional.of(sq);
    }

    // ─── NSE Historical Data ──────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = "nseIndia", fallbackMethod = "getHistoricalFallback")
    public List<OHLCVData> getHistoricalData(String symbol, Market market,
                                              LocalDate from, LocalDate to,
                                              String interval) {
        // NSE historical API
        String fromStr = from.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String toStr   = to.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String url = String.format("%s/historical/cm/equity?symbol=%s&series=[\"EQ\"]&from=%s&to=%s",
                baseUrl, symbol.toUpperCase(), fromStr, toStr);
        try {
            String json = executeGet(url);
            return parseNSEHistorical(json, symbol, market, interval);
        } catch (Exception e) {
            log.error("[NSE] Failed to fetch history for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<OHLCVData> getHistoricalFallback(String symbol, Market market,
                                                    LocalDate from, LocalDate to,
                                                    String interval, Throwable t) {
        return Collections.emptyList();
    }

    private List<OHLCVData> parseNSEHistorical(String json, String symbol,
                                                Market market, String interval) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray()) return Collections.emptyList();

        List<OHLCVData> bars = new ArrayList<>();
        for (JsonNode item : data) {
            LocalDate date = LocalDate.parse(
                item.path("CH_TIMESTAMP").asText(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            );
            OHLCVData ohlcv = OHLCVData.builder()
                    .symbol(symbol)
                    .market(market)
                    .barDate(date)
                    .intervalType(interval)
                    .open(new BigDecimal(item.path("CH_OPENING_PRICE").asText("0")))
                    .high(new BigDecimal(item.path("CH_TRADE_HIGH_PRICE").asText("0")))
                    .low(new BigDecimal(item.path("CH_TRADE_LOW_PRICE").asText("0")))
                    .close(new BigDecimal(item.path("CH_CLOSING_PRICE").asText("0")))
                    .adjustedClose(new BigDecimal(item.path("CH_CLOSING_PRICE").asText("0")))
                    .volume(item.path("CH_TOT_TRADED_QTY").asLong(0))
                    .fetchedAt(LocalDateTime.now())
                    .dataSource("NSE India")
                    .build();
            bars.add(ohlcv);
        }
        bars.sort(Comparator.comparing(OHLCVData::getBarDate));
        return bars;
    }

    @Override
    public List<StockQuote> getBatchQuotes(List<String> symbols, Market market) {
        List<StockQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            getQuote(symbol, market).ifPresent(quotes::add);
        }
        return quotes;
    }

    // ─── HTTP Helper ──────────────────────────────────────────────────────────

    private String executeGet(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .get()
                // NSE requires these headers to avoid 403
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.nseindia.com/")
                .header("Connection", "keep-alive")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    // ─── JSON helpers ─────────────────────────────────────────────────────────

    private BigDecimal bd(JsonNode node, String field) {
        String val = node.path(field).asText("0").replaceAll("[^0-9.]", "");
        try { return new BigDecimal(val.isEmpty() ? "0" : val); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private BigDecimal bd(JsonNode node, String field, String subField) {
        String val = node.path(field).path(subField).asText("0").replaceAll("[^0-9.]", "");
        try { return new BigDecimal(val.isEmpty() ? "0" : val); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}