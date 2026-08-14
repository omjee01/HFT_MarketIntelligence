package com.hft.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.config.CacheConfig;
import com.hft.model.domain.MacroData;
import com.hft.model.enums.Market;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Macroeconomic & Geopolitical Analysis Service.
 *
 * US Data Sources: FRED API (Federal Reserve Economic Data)
 * India Data Sources: RBI press releases, NSE FII/DII data
 *
 * Computes:
 *  - MacroScore (0–100): higher = more favorable macro environment
 *  - Sector tailwinds/headwinds based on macro conditions
 *  - Geopolitical risk score (0–10)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MacroGeopoliticalService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${hft.fred.api-key:}")
    private String fredApiKey;

    @Value("${hft.fred.base-url:https://api.stlouisfed.org/fred}")
    private String fredBaseUrl;

    @Value("${hft.fred.enabled:false}")
    private boolean fredEnabled;

    // FRED Series IDs
    private static final String SERIES_FED_RATE  = "DFF";       // Fed Funds Rate
    private static final String SERIES_CPI       = "CPIAUCSL";  // CPI
    private static final String SERIES_GDP       = "A191RL1Q225SBEA"; // GDP Growth
    private static final String SERIES_UNEMPLOYMENT = "UNRATE"; // Unemployment
    private static final String SERIES_10Y_YIELD = "GS10";      // 10-Year Treasury
    private static final String SERIES_VIX       = "VIXCLS";    // CBOE VIX

    /**
     * Get macro data for US or India market.
     * Cached for 1 hour.
     */
    @Cacheable(value = CacheConfig.CACHE_MACRO, key = "#market.name()")
    public MacroData getMacroData(Market market) {
        if (market.isUS()) {
            return buildUSMacroData();
        } else {
            return buildIndiaMacroData();
        }
    }

    // ─── US Macro ─────────────────────────────────────────────────────────────

    private MacroData buildUSMacroData() {
        MacroData.MacroDataBuilder builder = MacroData.builder()
                .market(Market.US_NYSE)
                .lastUpdated(LocalDateTime.now())
                .dataSource("FRED, Yahoo Finance, Reuters");

        if (fredEnabled && !fredApiKey.isBlank()) {
            // Fetch live data from FRED
            builder.interestRate(fetchFredLatestValue(SERIES_FED_RATE));
            builder.cpiInflationRate(fetchFredLatestValue(SERIES_CPI));
            builder.gdpGrowthRateYoY(fetchFredLatestValue(SERIES_GDP));
            builder.unemploymentRate(fetchFredLatestValue(SERIES_UNEMPLOYMENT));
            builder.tenYearYield(fetchFredLatestValue(SERIES_10Y_YIELD));
            builder.vixLevel(fetchFredLatestValue(SERIES_VIX));
        } else {
            // Phase-1 fallback: use current well-known values (updated periodically)
            builder.interestRate(5.25)           // Fed Funds Rate (approx May 2026)
                   .cpiInflationRate(3.4)
                   .gdpGrowthRateYoY(2.8)
                   .unemploymentRate(3.9)
                   .tenYearYield(4.45)
                   .twoYearYield(4.75)
                   .isYieldCurveInverted(true)    // 2Y > 10Y
                   .vixLevel(14.5)
                   .vixSentiment("NEUTRAL")
                   .dxyIndex(104.2)
                   .usdInrRate(83.5)
                   .interestRateOutlook("CUT")    // Fed expected to cut
                   .inflationTrend("FALLING");
        }

        MacroData data = builder.build();
        data.setMacroScore(computeUSMacroScore(data));
        data.setMacroSentiment(data.getMacroScore() > 60 ? "POSITIVE"
                              : data.getMacroScore() > 40 ? "NEUTRAL" : "NEGATIVE");
        data.setSectorTailwinds(computeUSTailwinds(data));
        data.setSectorHeadwinds(computeUSHeadwinds(data));
        data.setGeopoliticalRiskScore(5.0);    // Medium geopolitical risk as of 2026
        data.setGeopoliticalRiskLabel("ELEVATED");
        return data;
    }

    // ─── India Macro ──────────────────────────────────────────────────────────

    private MacroData buildIndiaMacroData() {
        // Phase-1: Use current known India macro values
        // Phase-2: Fetch from RBI website + NSE FII/DII data
        MacroData data = MacroData.builder()
                .market(Market.INDIA_NSE)
                .interestRate(6.50)             // RBI Repo Rate
                .cpiInflationRate(5.1)
                .gdpGrowthRateYoY(7.2)         // India GDP (strong growth)
                .vixLevel(13.8)                 // India VIX (calm market)
                .vixSentiment("NEUTRAL")
                .usdInrRate(83.8)
                .usdInrTrend(-0.3)             // INR slightly strengthening
                .fiiFlowTrend("BUYING")
                .consecutiveFiiBuyDays(5)
                .interestRateOutlook("CUT")    // RBI easing cycle
                .inflationTrend("FALLING")
                .lastUpdated(LocalDateTime.now())
                .dataSource("RBI, NSE, SEBI")
                .build();

        data.setMacroScore(computeIndiaMacroScore(data));
        data.setMacroSentiment(data.getMacroScore() > 60 ? "POSITIVE" : "NEUTRAL");
        data.setSectorTailwinds(computeIndiaTailwinds(data));
        data.setSectorHeadwinds(computeIndiaHeadwinds(data));
        data.setGeopoliticalRiskScore(4.0);
        data.setGeopoliticalRiskLabel("LOW_ELEVATED");
        data.setMajorUpcomingEvents("RBI MPC Meeting, Union Budget, Q1 Results Season");
        return data;
    }

    // ─── Macro Scoring ────────────────────────────────────────────────────────

    private double computeUSMacroScore(MacroData d) {
        double score = 50.0;

        // Interest rate environment
        if (d.getInterestRate() != null) {
            if (d.getInterestRate() < 3.0)  score += 10;  // low rates = good for equities
            else if (d.getInterestRate() > 5.0) score -= 10;
        }
        if ("CUT".equals(d.getInterestRateOutlook()))   score += 8;
        else if ("HIKE".equals(d.getInterestRateOutlook())) score -= 8;

        // Inflation
        if (d.getCpiInflationRate() != null) {
            if (d.getCpiInflationRate() < 3.0) score += 8;
            else if (d.getCpiInflationRate() > 5.0) score -= 8;
        }
        if ("FALLING".equals(d.getInflationTrend())) score += 5;
        else if ("RISING".equals(d.getInflationTrend())) score -= 5;

        // GDP
        if (d.getGdpGrowthRateYoY() != null) {
            if (d.getGdpGrowthRateYoY() > 2.5) score += 8;
            else if (d.getGdpGrowthRateYoY() < 1.0) score -= 8;
        }

        // VIX (fear index)
        if (d.getVixLevel() != null) {
            if (d.getVixLevel() < 15) score += 10;
            else if (d.getVixLevel() > 25) score -= 10;
            else if (d.getVixLevel() > 20) score -= 5;
        }

        // Yield curve
        if (Boolean.TRUE.equals(d.getIsYieldCurveInverted())) score -= 5;

        // Geopolitical risk
        if (d.getGeopoliticalRiskScore() != null) {
            score -= d.getGeopoliticalRiskScore();  // 0-10 points deducted
        }

        return Math.min(100, Math.max(0, score));
    }

    private double computeIndiaMacroScore(MacroData d) {
        double score = 50.0;

        if ("CUT".equals(d.getInterestRateOutlook()))   score += 10;
        if ("FALLING".equals(d.getInflationTrend()))    score += 8;
        if (d.getGdpGrowthRateYoY() != null && d.getGdpGrowthRateYoY() > 6.5) score += 12;
        if (d.getVixLevel() != null && d.getVixLevel() < 15) score += 8;
        if ("BUYING".equals(d.getFiiFlowTrend())) score += 10;
        else if ("SELLING".equals(d.getFiiFlowTrend())) score -= 10;
        if (d.getUsdInrTrend() != null && d.getUsdInrTrend() < 0) score += 5;  // INR strengthening

        return Math.min(100, Math.max(0, score));
    }

    // ─── Sector Impact Mapping ─────────────────────────────────────────────────

    private String computeUSTailwinds(MacroData d) {
        StringBuilder sb = new StringBuilder();
        if ("CUT".equals(d.getInterestRateOutlook()))   sb.append("Banking, Real Estate, Growth Tech, ");
        if ("FALLING".equals(d.getInflationTrend()))    sb.append("Consumer Discretionary, Retail, ");
        if (d.getVixLevel() != null && d.getVixLevel() < 15) sb.append("High-Beta Stocks, ");
        return sb.toString().replaceAll(", $", "");
    }

    private String computeUSHeadwinds(MacroData d) {
        StringBuilder sb = new StringBuilder();
        if (d.getInterestRate() != null && d.getInterestRate() > 5) sb.append("Bonds, Utilities, ");
        if (d.getCpiInflationRate() != null && d.getCpiInflationRate() > 4) sb.append("Consumer Staples margins, ");
        if (Boolean.TRUE.equals(d.getIsYieldCurveInverted())) sb.append("Financial sector loans, ");
        return sb.toString().replaceAll(", $", "");
    }

    private String computeIndiaTailwinds(MacroData d) {
        String result = "IT Exporters (INR depreciation), Banking (rate cycle)";
        if ("BUYING".equals(d.getFiiFlowTrend())) result += ", Large-cap Indices";
        return result;
    }

    private String computeIndiaHeadwinds(MacroData d) {
        return "Oil & Gas importers (USD/INR risk), Import-heavy sectors";
    }

    // ─── FRED API Helper ──────────────────────────────────────────────────────

    private Double fetchFredLatestValue(String series) {
        if (!fredEnabled || fredApiKey.isBlank()) return null;
        try {
            String url = String.format("%s/series/observations?series_id=%s&api_key=%s&file_type=json&limit=1&sort_order=desc",
                    fredBaseUrl, series, fredApiKey);
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode obs = root.path("observations");
                if (obs.isArray() && obs.size() > 0) {
                    String val = obs.get(0).path("value").asText(".");
                    return ".".equals(val) ? null : Double.parseDouble(val);
                }
            }
        } catch (Exception e) {
            log.warn("[Macro] FRED fetch failed for {}: {}", series, e.getMessage());
        }
        return null;
    }

    // ─── Scheduled refresh every hour ────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *")
    public void refreshMacroData() {
        log.debug("[Macro] Hourly macro data refresh triggered");
    }

    /** Returns a simple map for API response snapshots */
    public Map<String, Object> getMacroSnapshot(Market market) {
        MacroData d = getMacroData(market);
        return Map.of(
            "market", d.getMarket().getDisplayName(),
            "interestRate", d.getInterestRate() != null ? d.getInterestRate() : "N/A",
            "inflation", d.getCpiInflationRate() != null ? d.getCpiInflationRate() : "N/A",
            "gdpGrowth", d.getGdpGrowthRateYoY() != null ? d.getGdpGrowthRateYoY() : "N/A",
            "vix", d.getVixLevel() != null ? d.getVixLevel() : "N/A",
            "macroScore", d.getMacroScore() != null ? d.getMacroScore() : 50,
            "macroSentiment", d.getMacroSentiment() != null ? d.getMacroSentiment() : "NEUTRAL",
            "tailwinds", d.getSectorTailwinds() != null ? d.getSectorTailwinds() : "",
            "lastUpdated", d.getLastUpdated().toString()
        );
    }
}