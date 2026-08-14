package com.hft.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.config.CacheConfig;
import com.hft.model.domain.FundamentalData;
import com.hft.model.enums.Market;
import com.hft.repository.FundamentalDataRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Fundamental Analysis Service — fetches and scores financial fundamentals.
 *
 * US: Alpha Vantage OVERVIEW endpoint
 * India: Pre-loaded from NSE filings or manual data
 *
 * Scoring algorithm:
 *  - Valuation (P/E, P/B, EV/EBITDA): 30 pts
 *  - Growth (EPS, Revenue YoY): 25 pts
 *  - Profitability (ROE, ROCE, Margins): 25 pts
 *  - Balance Sheet (Debt/Equity, Current Ratio): 20 pts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalAnalysisService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FundamentalDataRepository fundRepo;

    @Value("${hft.alpha-vantage.api-key}")
    private String alphaVantageKey;

    @Value("${hft.alpha-vantage.base-url}")
    private String alphaVantageUrl;

    /**
     * Get fundamental data with scoring. Uses cached data if less than 24h old.
     */
    @Cacheable(value = CacheConfig.CACHE_FUNDAMENTAL, key = "#symbol + '_' + #market.name()")
    public Optional<FundamentalData> analyze(String symbol, Market market) {
        // Try DB first (refresh daily)
        Optional<FundamentalData> cached = fundRepo.findById(symbol);
        if (cached.isPresent() &&
            cached.get().getLastUpdated() != null &&
            cached.get().getLastUpdated().isAfter(LocalDate.now().minusDays(1))) {
            return cached;
        }

        // Fetch fresh data
        if (market.isUS()) {
            return fetchUSFundamentals(symbol, market);
        } else {
            return fetchIndiaFundamentals(symbol, market);
        }
    }

    // ─── US Fundamentals via Alpha Vantage OVERVIEW ───────────────────────────

    @CircuitBreaker(name = "alphaVantage")
    private Optional<FundamentalData> fetchUSFundamentals(String symbol, Market market) {
        String url = String.format("%s?function=OVERVIEW&symbol=%s&apikey=%s",
                alphaVantageUrl, symbol, alphaVantageKey);
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                String json = response.body().string();
                return parseAVOverview(json, symbol, market);
            }
        } catch (Exception e) {
            log.error("[Fundamental] Failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FundamentalData> parseAVOverview(String json, String symbol, Market market) {
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.path("Symbol").isMissingNode()) return Optional.empty();

            FundamentalData fd = FundamentalData.builder()
                    .symbol(symbol)
                    .companyName(n.path("Name").asText(symbol))
                    .market(market)
                    .sector(n.path("Sector").asText(""))
                    .industry(n.path("Industry").asText(""))
                    .peRatio(dbl(n, "PERatio"))
                    .pbRatio(dbl(n, "PriceToBookRatio"))
                    .evToEbitda(dbl(n, "EVToEBITDA"))
                    .epsCurrentYear(dbl(n, "EPS"))
                    .revenueGrowthYoY(dbl(n, "QuarterlyRevenueGrowthYOY") * 100)
                    .epsGrowthYoY(dbl(n, "QuarterlyEarningsGrowthYOY") * 100)
                    .netProfitMargin(dbl(n, "ProfitMargin") * 100)
                    .roe(dbl(n, "ReturnOnEquityTTM") * 100)
                    .dividendYield(dbl(n, "DividendYield") * 100)
                    .beta(dbl(n, "Beta"))
                    .lastUpdated(LocalDate.now())
                    .dataSource("Alpha Vantage").build();

            // Compute fundamental score
            fd.setFundamentalScore(computeFundamentalScore(fd));
            fd.setValuationStatus(determineValuationStatus(fd));

            fundRepo.save(fd);
            return Optional.of(fd);
        } catch (Exception e) {
            log.error("[Fundamental] Parse failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── India Fundamentals (Basic structure for NSE data) ───────────────────

    private Optional<FundamentalData> fetchIndiaFundamentals(String symbol, Market market) {
        // Phase-1: Return structured placeholder with sector context
        // Phase-2: Integrate Screener.in API or NSDL XML filings
        log.debug("[Fundamental] India fundamentals for {} - using cached/manual data", symbol);
        return fundRepo.findById(symbol);
    }

    // ─── Fundamental Score Algorithm (0–100) ──────────────────────────────────

    public double computeFundamentalScore(FundamentalData fd) {
        double score = 50.0;  // neutral baseline

        // ── VALUATION (30 points max) ─────────────────────────────────────────
        if (fd.getPeRatio() != null && fd.getPeRatio() > 0) {
            double pe = fd.getPeRatio();
            if      (pe < 10)  score += 15;   // very cheap
            else if (pe < 20)  score += 10;   // fair value
            else if (pe < 30)  score += 5;    // slightly expensive
            else if (pe < 50)  score -= 5;    // expensive
            else               score -= 15;   // very expensive
        }
        if (fd.getPbRatio() != null && fd.getPbRatio() > 0) {
            double pb = fd.getPbRatio();
            if      (pb < 1.0) score += 10;
            else if (pb < 2.0) score += 5;
            else if (pb > 5.0) score -= 5;
        }
        if (fd.getEvToEbitda() != null && fd.getEvToEbitda() > 0) {
            double ev = fd.getEvToEbitda();
            if      (ev < 8)   score += 5;
            else if (ev > 20)  score -= 5;
        }

        // ── GROWTH (25 points max) ────────────────────────────────────────────
        if (fd.getEpsGrowthYoY() != null) {
            double g = fd.getEpsGrowthYoY();
            if      (g > 30) score += 12;
            else if (g > 15) score += 8;
            else if (g > 5)  score += 4;
            else if (g < 0)  score -= 8;
        }
        if (fd.getRevenueGrowthYoY() != null) {
            double r = fd.getRevenueGrowthYoY();
            if      (r > 25) score += 8;
            else if (r > 10) score += 5;
            else if (r < 0)  score -= 5;
        }

        // ── PROFITABILITY (25 points max) ─────────────────────────────────────
        if (fd.getRoe() != null) {
            double roe = fd.getRoe();
            if      (roe > 25) score += 10;
            else if (roe > 15) score += 7;
            else if (roe < 5)  score -= 7;
        }
        if (fd.getNetProfitMargin() != null) {
            double npm = fd.getNetProfitMargin();
            if      (npm > 20) score += 8;
            else if (npm > 10) score += 5;
            else if (npm < 0)  score -= 8;
        }

        // ── BALANCE SHEET (20 points max) ─────────────────────────────────────
        if (fd.getDebtToEquity() != null) {
            double de = fd.getDebtToEquity();
            if      (de < 0.3) score += 10;
            else if (de < 0.7) score += 5;
            else if (de > 2.0) score -= 10;
        }
        if (fd.getCurrentRatio() != null) {
            double cr = fd.getCurrentRatio();
            if      (cr > 2.0) score += 5;
            else if (cr < 1.0) score -= 5;
        }

        return Math.min(100, Math.max(0, score));
    }

    private String determineValuationStatus(FundamentalData fd) {
        if (fd.getFundamentalScore() == null) return "UNKNOWN";
        double s = fd.getFundamentalScore();
        if (s >= 70) return "UNDERVALUED";
        if (s >= 45) return "FAIRLY_VALUED";
        return "OVERVALUED";
    }

    private double dbl(JsonNode n, String field) {
        try { return n.path(field).asDouble(0); }
        catch (Exception e) { return 0; }
    }

    // Public helper to get score for standalone use
    public Double getScore(FundamentalData fd) {
        return fd != null ? fd.getFundamentalScore() : null;
    }
}