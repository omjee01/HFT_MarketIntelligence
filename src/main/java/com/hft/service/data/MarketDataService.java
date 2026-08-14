package com.hft.service.data;

import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contract for all market data providers (Alpha Vantage, Yahoo Finance, NSE, BSE).
 * Each provider implements this interface; MarketDataAggregatorService routes to the right one.
 */
public interface MarketDataService {

    /** Fetch the latest real-time (or delayed) quote for a symbol */
    Optional<StockQuote> getQuote(String symbol, Market market);

    /** Fetch historical OHLCV bars for a date range */
    List<OHLCVData> getHistoricalData(String symbol, Market market,
                                       LocalDate from, LocalDate to,
                                       String interval);

    /** Fetch multiple quotes in batch (e.g. a watchlist) */
    List<StockQuote> getBatchQuotes(List<String> symbols, Market market);

    /** Returns true if this service can handle the given market */
    boolean supports(Market market);

    /** Human-readable name of this data provider */
    String getProviderName();
}