package com.hft.service.data;

import com.hft.config.CacheConfig;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;
import com.hft.repository.StockQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multiple market data providers with priority-based failover:
 *
 *   US Markets:      Alpha Vantage → Yahoo Finance → Cached data
 *   Indian Markets:  NSE India     → BSE India    → Cached data
 *
 * Acts as the single point of entry for all market data needs in the platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataAggregatorService {

    private final List<MarketDataService> providers;    // All implementations injected
    private final StockQuoteRepository quoteRepository;

    // ─── Watchlist of actively monitored tickers ──────────────────────────────
    private static final List<String> US_WATCHLIST = List.of(
        "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA",
        "JPM", "BAC", "GS", "V", "MA",
        "XOM", "CVX", "COP",
        "JNJ", "PFE", "MRK", "ABBV",
        "SPY", "QQQ", "GLD", "SLV", "USO"   // ETFs & Commodities
    );

    private static final List<String> INDIA_WATCHLIST = List.of(
        "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY",
        "KOTAKBANK", "HINDUNILVR", "AXISBANK", "SBIN", "BAJFINANCE",
        "WIPRO", "LTIM", "HCLTECH", "TECHM",
        "ADANIPORTS", "TATASTEEL", "JSWSTEEL",
        "SUNPHARMA", "DRREDDY", "CIPLA",
        "TITAN", "ASIANPAINT", "NESTLEIND",
        "NIFTY50", "SENSEX", "BANKNIFTY"    // Indices
    );

    // ─── Live Quote ───────────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.CACHE_QUOTES, key = "#symbol + '_' + #market.name()")
    public Optional<StockQuote> getQuote(String symbol, Market market) {
        log.debug("[Aggregator] Fetching quote: {} on {}", symbol, market);
        List<MarketDataService> eligible = getSupportingProviders(market);
        for (MarketDataService provider : eligible) {
            try {
                Optional<StockQuote> quote = provider.getQuote(symbol, market);
                if (quote.isPresent()) {
                    quoteRepository.save(quote.get());  // persist for audit
                    return quote;
                }
            } catch (Exception e) {
                log.warn("[Aggregator] Provider {} failed for {}: {}",
                         provider.getProviderName(), symbol, e.getMessage());
            }
        }
        // Last resort: return most recent from DB
        return quoteRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    // ─── Historical Data ──────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.CACHE_TA,
               key = "#symbol + '_' + #market.name() + '_hist_' + #from + '_' + #to")
    public List<OHLCVData> getHistoricalData(String symbol, Market market,
                                              LocalDate from, LocalDate to,
                                              String interval) {
        List<MarketDataService> eligible = getSupportingProviders(market);
        for (MarketDataService provider : eligible) {
            try {
                List<OHLCVData> bars = provider.getHistoricalData(symbol, market, from, to, interval);
                if (!bars.isEmpty()) return bars;
            } catch (Exception e) {
                log.warn("[Aggregator] History provider {} failed for {}: {}",
                         provider.getProviderName(), symbol, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /** Convenience: last N days OHLCV for TA computation */
    public List<OHLCVData> getRecentHistory(String symbol, Market market, int days) {
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days + 20);  // buffer for holidays/weekends
        return getHistoricalData(symbol, market, from, to, "1D");
    }

    // ─── Batch Quotes ─────────────────────────────────────────────────────────

    public List<StockQuote> getBatchQuotes(List<String> symbols, Market market) {
        return symbols.parallelStream()
                      .map(s -> getQuote(s, market))
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .collect(Collectors.toList());
    }

    // ─── Scheduled Polling ────────────────────────────────────────────────────

    /**
     * Poll US market watchlist every 5 seconds during market hours.
     * Adjust to 500ms for real HFT with a premium API key.
     */
    @Scheduled(fixedDelayString = "${hft.scheduler.market-data-poll-ms:5000}")
    @Async("dataIngestionExecutor")
    @CacheEvict(value = CacheConfig.CACHE_QUOTES, allEntries = true)
    public void pollUSMarket() {
        log.debug("[Poller] Refreshing US market quotes ({} symbols)", US_WATCHLIST.size());
        US_WATCHLIST.forEach(symbol -> {
            try {
                getQuote(symbol, Market.US_NASDAQ);
            } catch (Exception e) {
                log.warn("[Poller] US poll failed for {}", symbol);
            }
        });
    }

    /**
     * Poll Indian market watchlist — runs during IST market hours.
     * 9:15 AM – 3:30 PM IST (Mon–Fri)
     */
    @Scheduled(cron = "0/30 * 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    @Async("dataIngestionExecutor")
    public void pollIndianMarket() {
        log.debug("[Poller] Refreshing Indian market quotes ({} symbols)", INDIA_WATCHLIST.size());
        INDIA_WATCHLIST.forEach(symbol -> {
            try {
                getQuote(symbol, Market.INDIA_NSE);
            } catch (Exception e) {
                log.warn("[Poller] India poll failed for {}", symbol);
            }
        });
    }

    // ─── Market Info ──────────────────────────────────────────────────────────

    public List<String> getUSWatchlist()     { return US_WATCHLIST; }
    public List<String> getIndiaWatchlist()  { return INDIA_WATCHLIST; }

    public Map<String, Object> getMarketSnapshot(Market market) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("market", market.getDisplayName());
        snapshot.put("currency", market.getCurrency());
        snapshot.put("timezone", market.getTimezone());
        snapshot.put("openTime", market.getMarketOpenTime());
        snapshot.put("closeTime", market.getMarketCloseTime());
        snapshot.put("lastRefreshed", LocalDateTime.now().toString());
        return snapshot;
    }

    // ─── Provider Resolution ──────────────────────────────────────────────────

    private List<MarketDataService> getSupportingProviders(Market market) {
        return providers.stream()
                        .filter(p -> p.supports(market))
                        .collect(Collectors.toList());
    }
}