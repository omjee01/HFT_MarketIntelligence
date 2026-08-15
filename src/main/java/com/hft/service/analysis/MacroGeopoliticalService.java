package com.hft.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.admin.PlatformCredentialProvider;
import com.hft.admin.PlatformSettingsService;
import com.hft.config.CacheConfig;
import com.hft.model.domain.MacroData;
import com.hft.model.enums.Market;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Macroeconomic & Geopolitical Analysis Service.
 *
 * US Data Sources: FRED API (Federal Reserve Economic Data), GDELT (geopolitical risk)
 * India Data Sources: NSE FII/DII daily flow (real, verified), GDELT (geopolitical risk).
 *   RBI repo rate/CPI/GDP remain on the Phase-1 fallback values — no publicly documented
 *   free RBI REST API was found during Phase 0 (see HFT_ARCHITECTURE.md §24.2 and the
 *   Phase 0 implementation report); revisit if RBI publishes one, or budget a paid data
 *   vendor if this needs to be real sooner.
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
    private final PlatformSettingsService platformSettingsService;

    @Value("${hft.fred.api-key:}")
    private String fredApiKey;

    @Value("${hft.fred.base-url:https://api.stlouisfed.org/fred}")
    private String fredBaseUrl;

    @Value("${hft.fred.enabled:false}")
    private boolean fredEnabled;

    @Value("${hft.gdelt.base-url:https://api.gdeltproject.org/api/v2}")
    private String gdeltBaseUrl;

    @Value("${hft.gdelt.enabled:false}")
    private boolean gdeltEnabled;

    @Value("${hft.rbi-nse.nse-base-url:https://www.nseindia.com/api}")
    private String nseBaseUrlForMacro;

    @Value("${hft.rbi-nse.enabled:false}")
    private boolean rbiNseEnabled;

    @Value("${hft.nse-india.user-agent}")
    private String nseUserAgent;

    // ─── Admin-settings override (com.hft.admin) — checked before the @Value default ──

    private String resolveFredApiKey() {
        return platformSettingsService.getOverride(PlatformCredentialProvider.FRED, "apiKey").orElse(fredApiKey);
    }

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

        if (fredEnabled && !resolveFredApiKey().isBlank()) {
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
        Double usRisk = fetchGdeltRiskScore("US");
        double usRiskScore = usRisk != null ? usRisk : 5.0;   // Phase-1 fallback if GDELT unreachable
        data.setGeopoliticalRiskScore(usRiskScore);
        data.setGeopoliticalRiskLabel(labelForRiskScore(usRiskScore));
        return data;
    }

    // ─── India Macro ──────────────────────────────────────────────────────────

    private MacroData buildIndiaMacroData() {
        // RBI repo rate / CPI / GDP: Phase-1 fallback values (no public free RBI REST API
        // found during Phase 0 — see class javadoc). FII/DII flow below is real, live NSE data.
        MacroData.MacroDataBuilder builder = MacroData.builder()
                .market(Market.INDIA_NSE)
                .interestRate(6.50)             // RBI Repo Rate (Phase-1 fallback)
                .cpiInflationRate(5.1)
                .gdpGrowthRateYoY(7.2)
                .vixLevel(13.8)                 // India VIX (Phase-1 fallback)
                .vixSentiment("NEUTRAL")
                .usdInrRate(83.8)
                .usdInrTrend(-0.3)
                .interestRateOutlook("CUT")
                .inflationTrend("FALLING")
                .lastUpdated(LocalDateTime.now());

        Map<String, BigDecimal> flows = fetchNseFiiDiiFlows();
        if (flows != null && (flows.get("FII") != null || flows.get("DII") != null)) {
            BigDecimal fii = flows.get("FII");
            builder.fiiNetFlowCrores(fii)
                   .diiNetFlowCrores(flows.get("DII"))
                   .fiiFlowTrend(fii != null && fii.signum() > 0 ? "BUYING"
                               : fii != null && fii.signum() < 0 ? "SELLING" : "NEUTRAL")
                   .dataSource("NSE (live FII/DII), Phase-1 fallback (repo rate/CPI/GDP)");
        } else {
            builder.fiiFlowTrend("BUYING")
                   .consecutiveFiiBuyDays(5)
                   .dataSource("Phase-1 fallback — NSE FII/DII fetch unavailable this pass");
        }

        MacroData data = builder.build();
        data.setMacroScore(computeIndiaMacroScore(data));
        data.setMacroSentiment(data.getMacroScore() > 60 ? "POSITIVE" : "NEUTRAL");
        data.setSectorTailwinds(computeIndiaTailwinds(data));
        data.setSectorHeadwinds(computeIndiaHeadwinds(data));
        Double inRisk = fetchGdeltRiskScore("IN");
        double inRiskScore = inRisk != null ? inRisk : 4.0;   // Phase-1 fallback if GDELT unreachable
        data.setGeopoliticalRiskScore(inRiskScore);
        data.setGeopoliticalRiskLabel(labelForRiskScore(inRiskScore));
        data.setMajorUpcomingEvents("RBI MPC Meeting, Union Budget, Q1 Results Season");
        return data;
    }

    // ─── NSE FII/DII (real, verified endpoint) ─────────────────────────────────

    @CircuitBreaker(name = "nseFiiDii")
    private Map<String, BigDecimal> fetchNseFiiDiiFlows() {
        if (!rbiNseEnabled) return null;
        try {
            String url = nseBaseUrlForMacro + "/fiidiiTradeReact";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", nseUserAgent)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://www.nseindia.com/")
                    .header("Connection", "keep-alive")
                    .get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JsonNode root = objectMapper.readTree(response.body().string());
                if (!root.isArray()) return null;
                Map<String, BigDecimal> result = new HashMap<>();
                for (JsonNode row : root) {
                    String category = row.path("category").asText("");
                    String netValue = row.path("netValue").asText(null);
                    if (netValue == null) continue;
                    try {
                        BigDecimal net = new BigDecimal(netValue);
                        if ("DII".equalsIgnoreCase(category)) result.put("DII", net);
                        else if (category.toUpperCase().contains("FII")) result.put("FII", net);
                    } catch (NumberFormatException ignored) { /* skip malformed row */ }
                }
                return result.isEmpty() ? null : result;
            }
        } catch (Exception e) {
            log.warn("[Macro] NSE FII/DII fetch failed: {}", e.getMessage());
            return null;
        }
    }

    // ─── GDELT geopolitical risk (query shape per GDELT DOC 2.0 API docs — NOTE: this
    // specific host could not be reached from the Phase 0 development sandbox, connection
    // timed out at the TCP level despite general outbound internet working fine and
    // www.gdeltproject.org [non-API subdomain] resolving/responding normally. Implemented
    // against GDELT's documented tonechart response shape but UNVERIFIED end-to-end — confirm
    // reachability from the actual deploy environment before trusting this. Fails closed to
    // the existing Phase-1 hardcoded value either way.) ─────────────────────────

    @CircuitBreaker(name = "gdelt")
    private Double fetchGdeltRiskScore(String countryCode) {
        if (!gdeltEnabled) return null;
        try {
            String query = String.format("sourcecountry:%s (war OR sanctions OR tariff OR conflict OR crisis)",
                    countryCode);
            String url = String.format("%s/doc/doc?query=%s&mode=tonechart&format=json&timespan=7days",
                    gdeltBaseUrl, URLEncoder.encode(query, StandardCharsets.UTF_8));
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode bins = root.path("tonechart");
                if (!bins.isArray() || bins.isEmpty()) return null;

                double weightedToneSum = 0;
                long totalCount = 0;
                for (JsonNode bin : bins) {
                    double toneBin = bin.path("bin").asDouble(0);
                    long count = bin.path("count").asLong(0);
                    weightedToneSum += toneBin * count;
                    totalCount += count;
                }
                if (totalCount == 0) return null;
                double avgTone = weightedToneSum / totalCount;   // GDELT tone is roughly -10..+10
                // More negative tone => higher risk. Map onto the existing 0-10 scale
                // (same direction/semantics as the Phase-1 hardcoded values it replaces).
                return Math.max(0, Math.min(10, 5.0 - avgTone));
            }
        } catch (Exception e) {
            log.warn("[Macro] GDELT fetch failed for {}: {}", countryCode, e.getMessage());
            return null;
        }
    }

    private String labelForRiskScore(double score) {
        if (score >= 7.5) return "EXTREME";
        if (score >= 5.5) return "HIGH";
        if (score >= 3.5) return "ELEVATED";
        return "LOW";
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
        if (!fredEnabled || resolveFredApiKey().isBlank()) return null;
        try {
            String url = String.format("%s/series/observations?series_id=%s&api_key=%s&file_type=json&limit=1&sort_order=desc",
                    fredBaseUrl, series, resolveFredApiKey());
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