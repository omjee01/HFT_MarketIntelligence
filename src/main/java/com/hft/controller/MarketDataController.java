package com.hft.controller;

import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.dto.ApiResponse;
import com.hft.model.dto.QuoteResponse;
import com.hft.model.enums.Market;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.util.MarketHoursUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Market data REST endpoints — real-time quotes, OHLCV history, market status.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Real-time quotes, OHLCV history, and market status")
public class MarketDataController {

    private final MarketDataAggregatorService marketData;
    private final MarketHoursUtil marketHoursUtil;

    // ─── Live Quote ───────────────────────────────────────────────────────────

    @GetMapping("/quote/{symbol}")
    @Operation(summary = "Get real-time quote for a symbol",
               description = "Supports US (AAPL, MSFT) and India (RELIANCE, TCS) symbols")
    public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        Optional<StockQuote> quoteOpt = marketData.getQuote(symbol.toUpperCase(), market);

        if (quoteOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(
                    "Quote not available for " + symbol, "QUOTE_NOT_FOUND"));
        }

        return ResponseEntity.ok(ApiResponse.success(toQuoteResponse(quoteOpt.get())));
    }

    // ─── Historical OHLCV ─────────────────────────────────────────────────────

    @GetMapping("/history/{symbol}")
    @Operation(summary = "Get historical OHLCV data",
               description = "Returns candlestick data for technical analysis")
    public ResponseEntity<ApiResponse<List<OHLCVData>>> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1D") String interval) {

        LocalDate endDate   = to   != null ? to   : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(365);

        List<OHLCVData> bars = marketData.getHistoricalData(
                symbol.toUpperCase(), market, startDate, endDate, interval);

        return ResponseEntity.ok(ApiResponse.success(bars,
                String.format("Retrieved %d bars for %s from %s to %s",
                               bars.size(), symbol, startDate, endDate)));
    }

    // ─── Batch Quotes ─────────────────────────────────────────────────────────

    @GetMapping("/batch")
    @Operation(summary = "Get quotes for multiple symbols at once")
    public ResponseEntity<ApiResponse<List<StockQuote>>> getBatchQuotes(
            @RequestParam List<String> symbols,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        List<StockQuote> quotes = marketData.getBatchQuotes(symbols, market);
        return ResponseEntity.ok(ApiResponse.success(quotes));
    }

    // ─── Watchlist ────────────────────────────────────────────────────────────

    @GetMapping("/watchlist/us")
    @Operation(summary = "Get quotes for the platform's US watchlist")
    public ResponseEntity<ApiResponse<List<String>>> getUSWatchlist() {
        return ResponseEntity.ok(ApiResponse.success(marketData.getUSWatchlist()));
    }

    @GetMapping("/watchlist/india")
    @Operation(summary = "Get quotes for the platform's India watchlist")
    public ResponseEntity<ApiResponse<List<String>>> getIndiaWatchlist() {
        return ResponseEntity.ok(ApiResponse.success(marketData.getIndiaWatchlist()));
    }

    // ─── Market Status ────────────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Check if US and Indian markets are currently open")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMarketStatus() {
        Map<String, Object> status = Map.of(
            "usMarketOpen", marketHoursUtil.isUSMarketOpen(),
            "indianMarketOpen", marketHoursUtil.isIndianMarketOpen(),
            "usMarketTime", marketHoursUtil.currentMarketTime(Market.US_NYSE),
            "indiaMarketTime", marketHoursUtil.currentMarketTime(Market.INDIA_NSE),
            "message", marketHoursUtil.isUSMarketOpen() ? "US market is OPEN 🟢"
                      : marketHoursUtil.isIndianMarketOpen() ? "Indian market is OPEN 🟢"
                      : "All markets are CLOSED 🔴"
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ─── Market Snapshot ──────────────────────────────────────────────────────

    @GetMapping("/snapshot/{market}")
    @Operation(summary = "Get market metadata and snapshot info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSnapshot(
            @PathVariable Market market) {
        return ResponseEntity.ok(ApiResponse.success(marketData.getMarketSnapshot(market)));
    }

    // ─── DTO Mapper ───────────────────────────────────────────────────────────

    private QuoteResponse toQuoteResponse(StockQuote q) {
        return QuoteResponse.builder()
                .symbol(q.getSymbol())
                .companyName(q.getCompanyName())
                .market(q.getMarket())
                .assetType(q.getAssetType())
                .currency(q.getCurrency())
                .price(q.getCurrentPrice())
                .open(q.getOpenPrice())
                .high(q.getHighPrice())
                .low(q.getLowPrice())
                .previousClose(q.getPreviousClose())
                .change(q.getDayChange())
                .changePercent(q.getDayChangePercent() != null ? q.getDayChangePercent().doubleValue() : null)
                .volume(q.getVolume())
                .avgVolume(q.getAvgVolume20Day())
                .marketCap(q.getMarketCap())
                .peRatio(q.getPeRatio() != null ? q.getPeRatio().doubleValue() : null)
                .fiftyTwoWeekHigh(q.getFiftyTwoWeekHigh())
                .fiftyTwoWeekLow(q.getFiftyTwoWeekLow())
                .sector(q.getSector())
                .timestamp(q.getTimestamp())
                .isMarketOpen(q.getIsMarketOpen())
                .dataSource(q.getDataSource())
                .build();
    }
}