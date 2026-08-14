package com.hft.controller;

import com.hft.model.domain.FundamentalData;
import com.hft.model.domain.MacroData;
import com.hft.model.domain.SentimentData;
import com.hft.model.domain.TechnicalIndicators;
import com.hft.model.dto.ApiResponse;
import com.hft.model.enums.Market;
import com.hft.service.analysis.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for individual analysis engines — TA, Sentiment, Fundamental, Macro.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "Individual analysis engines — Technical, Sentiment, Fundamental, Macro")
public class AnalysisController {

    private final TechnicalAnalysisService  taService;
    private final SentimentAnalysisService  sentimentService;
    private final FundamentalAnalysisService fundService;
    private final MacroGeopoliticalService  macroService;

    // ─── Technical Analysis ───────────────────────────────────────────────────

    @GetMapping("/technical/{symbol}")
    @Operation(summary = "Full technical analysis — all 40+ indicators + composite score",
               description = "RSI, MACD, Bollinger Bands, ATR, OBV, patterns, trend direction, TechnicalScore(0-100)")
    public ResponseEntity<ApiResponse<TechnicalIndicators>> getTechnicalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        Optional<TechnicalIndicators> ta = taService.analyze(symbol.toUpperCase(), market);
        return ta.map(indicators -> ResponseEntity.ok(ApiResponse.success(indicators)))
                 .orElse(ResponseEntity.ok(ApiResponse.error(
                         "Insufficient data for technical analysis of " + symbol, "NO_DATA")));
    }

    // ─── Sentiment Analysis ───────────────────────────────────────────────────

    @GetMapping("/sentiment/{symbol}")
    @Operation(summary = "Sentiment analysis from news + social media",
               description = "NLP scoring of news headlines and social media. Score: -1 (bearish) to +1 (bullish)")
    public ResponseEntity<ApiResponse<SentimentData>> getSentimentAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        SentimentData sentiment = sentimentService.analyzeSentiment(symbol.toUpperCase(), market);
        return ResponseEntity.ok(ApiResponse.success(sentiment));
    }

    // ─── Fundamental Analysis ─────────────────────────────────────────────────

    @GetMapping("/fundamental/{symbol}")
    @Operation(summary = "Fundamental analysis — P/E, EPS growth, ROE, debt ratios, score",
               description = "Financial ratios sourced from Alpha Vantage OVERVIEW endpoint")
    public ResponseEntity<ApiResponse<FundamentalData>> getFundamentalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        Optional<FundamentalData> fd = fundService.analyze(symbol.toUpperCase(), market);
        return fd.map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                 .orElse(ResponseEntity.ok(ApiResponse.error(
                         "Fundamental data not available for " + symbol, "NO_DATA")));
    }

    // ─── Macro / Geopolitical ─────────────────────────────────────────────────

    @GetMapping("/macro/{market}")
    @Operation(summary = "Macroeconomic and geopolitical data for US or India",
               description = "Interest rates, inflation, GDP, VIX, FII/DII flows, geopolitical risk score")
    public ResponseEntity<ApiResponse<MacroData>> getMacroAnalysis(@PathVariable Market market) {
        MacroData macro = macroService.getMacroData(market);
        return ResponseEntity.ok(ApiResponse.success(macro));
    }

    @GetMapping("/macro/{market}/snapshot")
    @Operation(summary = "Quick macro snapshot as key-value map")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMacroSnapshot(@PathVariable Market market) {
        return ResponseEntity.ok(ApiResponse.success(macroService.getMacroSnapshot(market)));
    }

    // ─── Screener ─────────────────────────────────────────────────────────────

    @GetMapping("/screener")
    @Operation(summary = "Screen stocks by technical + fundamental criteria",
               description = "Filter by RSI, MACD, P/E, ROE, Golden Cross, etc.")
    public ResponseEntity<ApiResponse<String>> screener(
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) Double maxRsi,
            @RequestParam(required = false) Double maxPe,
            @RequestParam(required = false) Boolean goldenCross) {

        // Phase-1 stub — full screener implemented in Phase-2 Sprint 3
        return ResponseEntity.ok(ApiResponse.success(
                "Screener endpoint ready — full implementation in Sprint 3",
                "Coming Soon"));
    }
}