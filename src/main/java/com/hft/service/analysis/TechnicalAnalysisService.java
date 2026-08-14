package com.hft.service.analysis;

import com.hft.config.CacheConfig;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.TechnicalIndicators;
import com.hft.model.enums.Market;
import com.hft.model.enums.TrendDirection;
import com.hft.repository.TechnicalIndicatorsRepository;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.util.TechnicalIndicatorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Technical Analysis Service — computes all indicators and composite technical score.
 *
 * Flow:
 *  1. Fetch 200-day OHLCV from MarketDataAggregatorService
 *  2. Compute all indicators using TechnicalIndicatorUtil
 *  3. Detect patterns
 *  4. Compute composite TechnicalScore (0–100)
 *  5. Persist + cache result
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalAnalysisService {

    private final MarketDataAggregatorService marketDataService;
    private final TechnicalIndicatorUtil taUtil;
    private final TechnicalIndicatorsRepository taRepository;

    private static final int HISTORY_DAYS = 250;  // ~1 trading year

    /**
     * Full technical analysis for a symbol.
     * Cached for 5 minutes (TTL defined in CacheConfig).
     */
    @Cacheable(value = CacheConfig.CACHE_TA, key = "#symbol + '_' + #market.name()")
    public Optional<TechnicalIndicators> analyze(String symbol, Market market) {
        log.debug("[TA] Analyzing: {} on {}", symbol, market);

        List<OHLCVData> bars = marketDataService.getRecentHistory(symbol, market, HISTORY_DAYS);
        if (bars.size() < 50) {
            log.warn("[TA] Insufficient data for {} — only {} bars available", symbol, bars.size());
            return Optional.empty();
        }

        TechnicalIndicators.TechnicalIndicatorsBuilder builder = TechnicalIndicators.builder()
                .symbol(symbol)
                .intervalType("1D")
                .computedAt(LocalDateTime.now())
                .priceAtCompute(String.valueOf(bars.get(bars.size() - 1).getClose()));

        // ─── Trend Indicators ──────────────────────────────────────────────────
        if (bars.size() >= 20)  builder.sma20(taUtil.sma(bars, 20));
        if (bars.size() >= 50)  builder.sma50(taUtil.sma(bars, 50));
        if (bars.size() >= 200) builder.sma200(taUtil.sma(bars, 200));

        if (bars.size() >= 9)  builder.ema9(taUtil.ema(bars, 9));
        if (bars.size() >= 21) builder.ema21(taUtil.ema(bars, 21));
        if (bars.size() >= 55) builder.ema55(taUtil.ema(bars, 55));

        // ─── Momentum Indicators ──────────────────────────────────────────────
        Double rsi = null;
        if (bars.size() >= 15) {
            rsi = taUtil.rsi(bars, 14);
            builder.rsi14(rsi);
        }

        double[] macdResult = null;
        if (bars.size() >= 35) {
            macdResult = taUtil.macd(bars, 12, 26, 9);
            if (macdResult != null) {
                builder.macdLine(macdResult[0])
                       .macdSignal(macdResult[1])
                       .macdHistogram(macdResult[2])
                       .macdBullishCrossover(macdResult[0] > macdResult[1]);
            }
        }

        double[] stoch = null;
        if (bars.size() >= 17) {
            stoch = taUtil.stochastic(bars, 14, 3);
            if (stoch != null) {
                builder.stochasticK(stoch[0])
                       .stochasticD(stoch[1])
                       .stochasticBullishCrossover(stoch[0] > stoch[1] && stoch[0] < 40);
            }
        }

        if (bars.size() >= 14) {
            builder.williamsR(taUtil.williamsR(bars, 14));
        }

        // ─── Volatility Indicators ────────────────────────────────────────────
        double[] bbResult = null;
        if (bars.size() >= 20) {
            bbResult = taUtil.bollingerBands(bars, 20, 2.0);
            if (bbResult != null) {
                double currentClose = bars.get(bars.size() - 1).getClose().doubleValue();
                builder.bollingerUpper(bbResult[0])
                       .bollingerMiddle(bbResult[1])
                       .bollingerLower(bbResult[2])
                       .bollingerBandWidth(bbResult[3])
                       .bollingerBreakoutUp(currentClose > bbResult[0])
                       .bollingerBreakoutDown(currentClose < bbResult[2]);
            }
        }

        Double atr = null;
        if (bars.size() >= 15) {
            atr = taUtil.atr(bars, 14);
            builder.atr14(atr);
        }

        if (bars.size() >= 21) {
            builder.historicalVolatility20(taUtil.historicalVolatility(bars, 20));
        }

        // ─── Volume Indicators ────────────────────────────────────────────────
        builder.obv(taUtil.obv(bars));
        builder.vwap(taUtil.vwap(bars.subList(Math.max(0, bars.size() - 20), bars.size())));
        builder.volumeRatio(taUtil.volumeRatio(bars, 20));

        // OBV trend: compare current OBV with 20-bar ago OBV
        Long currentObv = taUtil.obv(bars);
        Long prevObv = bars.size() > 20 ? taUtil.obv(bars.subList(0, bars.size() - 20)) : null;
        if (currentObv != null && prevObv != null) {
            builder.obvTrendingUp(currentObv > prevObv);
        }

        // ─── Support & Resistance ──────────────────────────────────────────────
        builder.supportLevel(taUtil.supportLevel(bars, 50));
        builder.resistanceLevel(taUtil.resistanceLevel(bars, 50));

        // ─── Fibonacci ────────────────────────────────────────────────────────
        Double resistance = taUtil.resistanceLevel(bars, 50);
        Double support    = taUtil.supportLevel(bars, 50);
        if (resistance != null && support != null) {
            double[] fibs = taUtil.fibonacciLevels(resistance, support);
            builder.fibonacci236(fibs[0])
                   .fibonacci382(fibs[1])
                   .fibonacci500(fibs[2])
                   .fibonacci618(fibs[3]);
        }

        // ─── Pattern Detection ─────────────────────────────────────────────────
        List<String> patterns = detectPatterns(bars,
                taUtil.sma(bars, 50), taUtil.sma(bars, 200));
        builder.detectedPatterns(patterns);

        // ─── Trend Direction ──────────────────────────────────────────────────
        builder.trendDirection(determineTrend(bars,
                taUtil.sma(bars, 20), taUtil.sma(bars, 50), taUtil.sma(bars, 200)));

        // ─── Technical Score (0–100) ──────────────────────────────────────────
        double currentPrice = bars.get(bars.size() - 1).getClose().doubleValue();
        Double sma20  = taUtil.sma(bars, 20);
        Double sma50  = taUtil.sma(bars, 50);
        Double sma200 = taUtil.sma(bars, 200);
        Double ema9   = taUtil.ema(bars, 9);
        Double ema21  = taUtil.ema(bars, 21);
        Double volumeRatio = taUtil.volumeRatio(bars, 20);

        double score = taUtil.computeTechnicalScore(
                currentPrice, sma20, sma50, sma200, ema9, ema21,
                rsi, macdResult, bbResult, atr,
                currentObv, currentObv != null && prevObv != null && currentObv > prevObv,
                volumeRatio, patterns);
        builder.technicalScore(score);

        TechnicalIndicators result = builder.build();
        taRepository.save(result);

        log.debug("[TA] {} → Score: {}, RSI: {}, MACD: {}, Trend: {}",
                symbol, score, rsi,
                macdResult != null ? String.format("%.2f", macdResult[0]) : "N/A",
                result.getTrendDirection());

        return Optional.of(result);
    }

    // ─── Pattern Detection ─────────────────────────────────────────────────────

    private List<String> detectPatterns(List<OHLCVData> bars, Double sma50, Double sma200) {
        List<String> patterns = new ArrayList<>();
        int n = bars.size();
        if (n < 2) return patterns;

        OHLCVData last = bars.get(n - 1);
        OHLCVData prev = bars.get(n - 2);

        // Golden / Death Cross
        if (taUtil.isGoldenCross(sma50, sma200))  patterns.add("GOLDEN_CROSS");
        if (taUtil.isDeathCross(sma50, sma200))   patterns.add("DEATH_CROSS");

        // Candlestick patterns
        if (taUtil.isHammer(last))                patterns.add("HAMMER");
        if (taUtil.isShootingStar(last))          patterns.add("SHOOTING_STAR");
        if (taUtil.isBullishEngulfing(prev, last))patterns.add("BULLISH_ENGULFING");
        if (taUtil.isBearishEngulfing(prev, last))patterns.add("BEARISH_ENGULFING");
        if (last.isDoji())                        patterns.add("DOJI");

        // Higher highs + higher lows (uptrend confirmation)
        if (n >= 5) {
            boolean higherHighs = isHigherHighs(bars, 5);
            boolean lowerLows   = isLowerLows(bars, 5);
            if (higherHighs)  patterns.add("HIGHER_HIGHS");
            if (lowerLows)    patterns.add("LOWER_LOWS");
        }

        return patterns;
    }

    private boolean isHigherHighs(List<OHLCVData> bars, int period) {
        List<OHLCVData> slice = bars.subList(bars.size() - period, bars.size());
        for (int i = 1; i < slice.size(); i++) {
            if (slice.get(i).getHigh().compareTo(slice.get(i - 1).getHigh()) <= 0) return false;
        }
        return true;
    }

    private boolean isLowerLows(List<OHLCVData> bars, int period) {
        List<OHLCVData> slice = bars.subList(bars.size() - period, bars.size());
        for (int i = 1; i < slice.size(); i++) {
            if (slice.get(i).getLow().compareTo(slice.get(i - 1).getLow()) >= 0) return false;
        }
        return true;
    }

    // ─── Trend Direction ─────────────────────────────────────────────────────

    private TrendDirection determineTrend(List<OHLCVData> bars,
                                          Double sma20, Double sma50, Double sma200) {
        if (bars.isEmpty()) return TrendDirection.SIDEWAYS;
        double price = bars.get(bars.size() - 1).getClose().doubleValue();

        int bullishSignals = 0;
        if (sma20  != null && price > sma20)  bullishSignals++;
        if (sma50  != null && price > sma50)  bullishSignals++;
        if (sma200 != null && price > sma200) bullishSignals++;
        if (sma50  != null && sma200 != null && sma50 > sma200) bullishSignals++;

        return switch (bullishSignals) {
            case 4    -> TrendDirection.STRONG_UPTREND;
            case 3    -> TrendDirection.UPTREND;
            case 2    -> TrendDirection.SIDEWAYS;
            case 1    -> TrendDirection.DOWNTREND;
            default   -> TrendDirection.STRONG_DOWNTREND;
        };
    }
}