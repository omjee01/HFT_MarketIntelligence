package com.hft.util;

import com.hft.model.domain.OHLCVData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure mathematical computations for all technical indicators.
 *
 * All methods are stateless and take List<OHLCVData> sorted OLDEST→NEWEST.
 * Returns null if insufficient data for the computation.
 */
@Component
public class TechnicalIndicatorUtil {

    // ─── Simple Moving Average ─────────────────────────────────────────────────

    /**
     * SMA(period) — arithmetic mean of last N closing prices.
     */
    public Double sma(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period) return null;
        List<OHLCVData> slice = bars.subList(bars.size() - period, bars.size());
        double sum = slice.stream()
                          .mapToDouble(b -> b.getClose().doubleValue())
                          .sum();
        return round(sum / period);
    }

    // ─── Exponential Moving Average ───────────────────────────────────────────

    /**
     * EMA(period) — weighted moving average giving more weight to recent prices.
     * Uses standard smoothing factor: k = 2 / (period + 1)
     */
    public Double ema(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period) return null;
        double k = 2.0 / (period + 1);
        // Seed with SMA of first `period` bars
        double ema = bars.subList(0, period).stream()
                         .mapToDouble(b -> b.getClose().doubleValue())
                         .average().orElse(0);
        for (int i = period; i < bars.size(); i++) {
            double price = bars.get(i).getClose().doubleValue();
            ema = price * k + ema * (1 - k);
        }
        return round(ema);
    }

    // ─── RSI ──────────────────────────────────────────────────────────────────

    /**
     * RSI(14) — measures speed and change of price movements.
     * RSI > 70 = overbought (possible sell), RSI < 30 = oversold (possible buy).
     */
    public Double rsi(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period + 1) return null;

        List<OHLCVData> slice = bars.subList(bars.size() - period - 1, bars.size());
        double avgGain = 0, avgLoss = 0;

        // Initial average gain/loss
        for (int i = 1; i <= period; i++) {
            double change = slice.get(i).getClose().doubleValue()
                          - slice.get(i - 1).getClose().doubleValue();
            if (change > 0) avgGain += change;
            else            avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // Smoothed RSI for remaining bars
        for (int i = period + 1; i < slice.size(); i++) {
            double change = slice.get(i).getClose().doubleValue()
                          - slice.get(i - 1).getClose().doubleValue();
            double gain = Math.max(change, 0);
            double loss = Math.abs(Math.min(change, 0));
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return avgGain == 0 ? 50.0 : 100.0;
        double rs = avgGain / avgLoss;
        return round(100 - (100 / (1 + rs)));
    }

    // ─── MACD ─────────────────────────────────────────────────────────────────

    /**
     * MACD(12, 26, 9) — trend-following momentum indicator.
     * Returns [macdLine, signalLine, histogram]
     */
    public double[] macd(List<OHLCVData> bars, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (bars == null || bars.size() < slowPeriod + signalPeriod) return null;

        // Build EMA series
        double[] fastEMAs = emaArray(bars, fastPeriod);
        double[] slowEMAs = emaArray(bars, slowPeriod);

        // MACD line = fast EMA - slow EMA (aligned to slow EMA length)
        int offset = fastPeriod - slowPeriod;  // typically negative = 0 since fast<slow
        int len = slowEMAs.length;
        double[] macdLine = new double[len];
        for (int i = 0; i < len; i++) {
            macdLine[i] = fastEMAs[i + (fastEMAs.length - len)] - slowEMAs[i];
        }

        // Signal line = EMA(macdLine, signalPeriod)
        double k = 2.0 / (signalPeriod + 1);
        double signal = 0;
        for (int i = 0; i < signalPeriod; i++) signal += macdLine[i];
        signal /= signalPeriod;
        for (int i = signalPeriod; i < macdLine.length; i++) {
            signal = macdLine[i] * k + signal * (1 - k);
        }

        double lastMacd = macdLine[macdLine.length - 1];
        double histogram = lastMacd - signal;
        return new double[]{ round(lastMacd), round(signal), round(histogram) };
    }

    /** Helper: compute EMA values as array for all available bars */
    private double[] emaArray(List<OHLCVData> bars, int period) {
        double k = 2.0 / (period + 1);
        double ema = bars.subList(0, period).stream()
                         .mapToDouble(b -> b.getClose().doubleValue())
                         .average().orElse(0);
        double[] result = new double[bars.size() - period + 1];
        result[0] = ema;
        for (int i = period; i < bars.size(); i++) {
            ema = bars.get(i).getClose().doubleValue() * k + ema * (1 - k);
            result[i - period + 1] = ema;
        }
        return result;
    }

    // ─── Bollinger Bands ──────────────────────────────────────────────────────

    /**
     * Bollinger Bands(20, 2) — volatility channels around a moving average.
     * Returns [upper, middle, lower, bandwidth%]
     */
    public double[] bollingerBands(List<OHLCVData> bars, int period, double stdDevMultiplier) {
        if (bars == null || bars.size() < period) return null;
        List<OHLCVData> slice = bars.subList(bars.size() - period, bars.size());
        double[] closes = slice.stream()
                               .mapToDouble(b -> b.getClose().doubleValue())
                               .toArray();

        double mean = Arrays.stream(closes).average().orElse(0);
        double variance = Arrays.stream(closes)
                                .map(c -> (c - mean) * (c - mean))
                                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        double upper    = round(mean + stdDevMultiplier * stdDev);
        double middle   = round(mean);
        double lower    = round(mean - stdDevMultiplier * stdDev);
        double bandwidth = mean != 0 ? round((upper - lower) / mean * 100) : 0;

        return new double[]{ upper, middle, lower, bandwidth };
    }

    // ─── ATR (Average True Range) ──────────────────────────────────────────────

    /**
     * ATR(14) — measures market volatility; used for stop-loss sizing.
     * Stop Loss = EntryPrice - (2 × ATR)
     */
    public Double atr(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period + 1) return null;
        List<OHLCVData> slice = bars.subList(bars.size() - period - 1, bars.size());

        double atr = 0;
        for (int i = 1; i <= period; i++) {
            OHLCVData curr = slice.get(i);
            OHLCVData prev = slice.get(i - 1);
            double tr = trueRange(curr, prev.getClose().doubleValue());
            atr += tr;
        }
        atr /= period;

        // Smooth for remaining (if any more bars)
        for (int i = period + 1; i < slice.size(); i++) {
            double tr = trueRange(slice.get(i), slice.get(i - 1).getClose().doubleValue());
            atr = (atr * (period - 1) + tr) / period;
        }
        return round(atr);
    }

    private double trueRange(OHLCVData bar, double prevClose) {
        double highLow  = bar.getHigh().doubleValue() - bar.getLow().doubleValue();
        double highPrev = Math.abs(bar.getHigh().doubleValue() - prevClose);
        double lowPrev  = Math.abs(bar.getLow().doubleValue()  - prevClose);
        return Math.max(highLow, Math.max(highPrev, lowPrev));
    }

    // ─── Stochastic Oscillator ────────────────────────────────────────────────

    /**
     * Stochastic Oscillator %K and %D.
     * %K < 20 = oversold (buy signal), %K > 80 = overbought (sell signal).
     */
    public double[] stochastic(List<OHLCVData> bars, int kPeriod, int dPeriod) {
        if (bars == null || bars.size() < kPeriod + dPeriod) return null;

        // Compute raw %K series
        List<Double> kValues = new ArrayList<>();
        for (int i = kPeriod - 1; i < bars.size(); i++) {
            List<OHLCVData> window = bars.subList(i - kPeriod + 1, i + 1);
            double highest = window.stream().mapToDouble(b -> b.getHigh().doubleValue()).max().orElse(0);
            double lowest  = window.stream().mapToDouble(b -> b.getLow().doubleValue()).min().orElse(0);
            double close   = bars.get(i).getClose().doubleValue();
            double kRaw = (highest == lowest) ? 50 : (close - lowest) / (highest - lowest) * 100;
            kValues.add(kRaw);
        }

        double kLast = kValues.get(kValues.size() - 1);
        // %D = SMA(kPeriod of last dPeriod %K values)
        double dLast = kValues.subList(Math.max(0, kValues.size() - dPeriod), kValues.size())
                              .stream().mapToDouble(Double::doubleValue).average().orElse(50);
        return new double[]{ round(kLast), round(dLast) };
    }

    // ─── OBV (On-Balance Volume) ───────────────────────────────────────────────

    /**
     * OBV adds volume on up-days, subtracts on down-days.
     * Rising OBV with rising price = strong uptrend confirmation.
     */
    public Long obv(List<OHLCVData> bars) {
        if (bars == null || bars.size() < 2) return null;
        long obv = 0;
        for (int i = 1; i < bars.size(); i++) {
            double curr = bars.get(i).getClose().doubleValue();
            double prev = bars.get(i - 1).getClose().doubleValue();
            long vol = bars.get(i).getVolume();
            if      (curr > prev) obv += vol;
            else if (curr < prev) obv -= vol;
        }
        return obv;
    }

    // ─── VWAP ─────────────────────────────────────────────────────────────────

    /**
     * VWAP — Volume Weighted Average Price for the current session.
     * Price > VWAP = bullish, Price < VWAP = bearish.
     */
    public Double vwap(List<OHLCVData> bars) {
        if (bars == null || bars.isEmpty()) return null;
        double cumTypicalPriceVolume = 0;
        long   cumVolume = 0;
        for (OHLCVData bar : bars) {
            double typicalPrice = (bar.getHigh().doubleValue()
                                 + bar.getLow().doubleValue()
                                 + bar.getClose().doubleValue()) / 3.0;
            long vol = bar.getVolume();
            cumTypicalPriceVolume += typicalPrice * vol;
            cumVolume += vol;
        }
        return cumVolume > 0 ? round(cumTypicalPriceVolume / cumVolume) : null;
    }

    // ─── Historical Volatility ────────────────────────────────────────────────

    /**
     * Annualized Historical Volatility = std_dev(log_returns) × sqrt(252).
     * Useful for options pricing and risk assessment.
     */
    public Double historicalVolatility(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period + 1) return null;
        List<OHLCVData> slice = bars.subList(bars.size() - period - 1, bars.size());

        double[] logReturns = new double[period];
        for (int i = 0; i < period; i++) {
            double curr = slice.get(i + 1).getClose().doubleValue();
            double prev = slice.get(i).getClose().doubleValue();
            logReturns[i] = Math.log(curr / prev);
        }

        double mean = Arrays.stream(logReturns).average().orElse(0);
        double variance = Arrays.stream(logReturns)
                                .map(r -> (r - mean) * (r - mean))
                                .sum() / (period - 1);
        return round(Math.sqrt(variance) * Math.sqrt(252) * 100); // annualized %
    }

    // ─── Williams %R ──────────────────────────────────────────────────────────

    /** Williams %R(14) — similar to Stochastic, range -100 to 0. */
    public Double williamsR(List<OHLCVData> bars, int period) {
        if (bars == null || bars.size() < period) return null;
        List<OHLCVData> slice = bars.subList(bars.size() - period, bars.size());
        double highest = slice.stream().mapToDouble(b -> b.getHigh().doubleValue()).max().orElse(0);
        double lowest  = slice.stream().mapToDouble(b -> b.getLow().doubleValue()).min().orElse(0);
        double close   = bars.get(bars.size() - 1).getClose().doubleValue();
        if (highest == lowest) return -50.0;
        return round((highest - close) / (highest - lowest) * -100);
    }

    // ─── Support & Resistance ─────────────────────────────────────────────────

    /** Detect swing low (support) from recent N bars. */
    public Double supportLevel(List<OHLCVData> bars, int lookback) {
        if (bars == null || bars.size() < lookback) return null;
        return bars.subList(bars.size() - lookback, bars.size()).stream()
                   .mapToDouble(b -> b.getLow().doubleValue())
                   .min().orElse(0);
    }

    /** Detect swing high (resistance) from recent N bars. */
    public Double resistanceLevel(List<OHLCVData> bars, int lookback) {
        if (bars == null || bars.size() < lookback) return null;
        return bars.subList(bars.size() - lookback, bars.size()).stream()
                   .mapToDouble(b -> b.getHigh().doubleValue())
                   .max().orElse(0);
    }

    // ─── Pattern Detection ────────────────────────────────────────────────────

    /** Detect Golden Cross: SMA50 crosses above SMA200 (strong bullish) */
    public boolean isGoldenCross(Double sma50, Double sma200) {
        return sma50 != null && sma200 != null && sma50 > sma200;
    }

    /** Detect Death Cross: SMA50 crosses below SMA200 (strong bearish) */
    public boolean isDeathCross(Double sma50, Double sma200) {
        return sma50 != null && sma200 != null && sma50 < sma200;
    }

    /** Detect Hammer candle — bullish reversal from downtrend */
    public boolean isHammer(OHLCVData bar) {
        if (bar == null) return false;
        double body = bar.bodySize().doubleValue();
        double lower = bar.lowerWick().doubleValue();
        double upper = bar.upperWick().doubleValue();
        double range = bar.range().doubleValue();
        if (range == 0) return false;
        // Hammer: small body at top, long lower wick (>2x body), tiny upper wick
        return lower > body * 2 && upper < body * 0.5 && body / range < 0.35;
    }

    /** Detect Shooting Star — bearish reversal from uptrend */
    public boolean isShootingStar(OHLCVData bar) {
        if (bar == null) return false;
        double body  = bar.bodySize().doubleValue();
        double lower = bar.lowerWick().doubleValue();
        double upper = bar.upperWick().doubleValue();
        double range = bar.range().doubleValue();
        if (range == 0) return false;
        return upper > body * 2 && lower < body * 0.5 && body / range < 0.35;
    }

    /** Detect Bullish Engulfing — current bullish candle engulfs previous bearish */
    public boolean isBullishEngulfing(OHLCVData prev, OHLCVData curr) {
        if (prev == null || curr == null) return false;
        return prev.isBearish() && curr.isBullish()
            && curr.getOpen().compareTo(prev.getClose()) <= 0
            && curr.getClose().compareTo(prev.getOpen()) >= 0;
    }

    /** Detect Bearish Engulfing — current bearish candle engulfs previous bullish */
    public boolean isBearishEngulfing(OHLCVData prev, OHLCVData curr) {
        if (prev == null || curr == null) return false;
        return prev.isBullish() && curr.isBearish()
            && curr.getOpen().compareTo(prev.getClose()) >= 0
            && curr.getClose().compareTo(prev.getOpen()) <= 0;
    }

    // ─── Technical Score (0–100) ──────────────────────────────────────────────

    /**
     * Composite technical score based on all available indicators.
     * Used as the "technicalScore" input to the ML composite scoring model.
     */
    public double computeTechnicalScore(
            double currentPrice,
            Double sma20, Double sma50, Double sma200,
            Double ema9, Double ema21,
            Double rsi14,
            double[] macdResult,
            double[] bbResult,
            Double atr14,
            Long obv, Boolean obvTrendingUp,
            Double volumeRatio,
            List<String> patterns) {

        double score = 50.0; // neutral baseline

        // ── TREND SIGNALS (max ±35 points) ───────────────────────────────────
        if (sma200 != null) {
            if (currentPrice > sma200) score += 10;   // above long-term MA = bullish
            else                       score -= 10;
        }
        if (sma50 != null && sma200 != null) {
            if (sma50 > sma200) score += 8;   // golden cross
            else                score -= 8;   // death cross
        }
        if (sma50 != null) {
            if (currentPrice > sma50) score += 7;
            else                      score -= 7;
        }
        if (ema21 != null && ema9 != null) {
            if (ema9 > ema21) score += 5;    // fast EMA above slow = bullish
            else              score -= 5;
        }

        // ── MOMENTUM SIGNALS (max ±30 points) ────────────────────────────────
        if (rsi14 != null) {
            if      (rsi14 < 30) score += 10;  // oversold bounce opportunity
            else if (rsi14 < 40) score += 5;
            else if (rsi14 > 80) score -= 8;   // severely overbought
            else if (rsi14 > 70) score -= 5;   // overbought
        }
        if (macdResult != null) {
            double macdLine = macdResult[0];
            double signalLine = macdResult[1];
            double histogram = macdResult[2];
            if (macdLine > signalLine)  score += 10;  // MACD above signal = bullish
            else                        score -= 10;
            if (histogram > 0)          score += 5;   // growing histogram
            else                        score -= 5;
        }

        // ── VOLATILITY SIGNALS (max ±15 points) ──────────────────────────────
        if (bbResult != null) {
            double upper = bbResult[0], middle = bbResult[1], lower = bbResult[2];
            if (currentPrice < lower)              score += 8;  // below lower BB = oversold
            else if (currentPrice > upper)         score -= 5;  // above upper BB = overbought
            else if (currentPrice > middle)        score += 3;  // above midline = mild bullish
        }

        // ── VOLUME SIGNALS (max ±10 points) ──────────────────────────────────
        if (obvTrendingUp != null) {
            score += obvTrendingUp ? 5 : -5;
        }
        if (volumeRatio != null) {
            if (volumeRatio > 1.5) score += 5;  // high volume confirms move
        }

        // ── PATTERN BONUSES (max ±10 points) ─────────────────────────────────
        if (patterns != null) {
            if (patterns.contains("GOLDEN_CROSS"))       score += 5;
            if (patterns.contains("HAMMER"))             score += 4;
            if (patterns.contains("BULLISH_ENGULFING"))  score += 4;
            if (patterns.contains("CUP_AND_HANDLE"))     score += 3;
            if (patterns.contains("DEATH_CROSS"))        score -= 5;
            if (patterns.contains("SHOOTING_STAR"))      score -= 4;
            if (patterns.contains("BEARISH_ENGULFING"))  score -= 4;
        }

        return Math.min(100, Math.max(0, round(score)));
    }

    // ─── Fibonacci Retracement ────────────────────────────────────────────────

    /** Returns Fibonacci retracement levels [23.6%, 38.2%, 50%, 61.8%] */
    public double[] fibonacciLevels(double swingHigh, double swingLow) {
        double diff = swingHigh - swingLow;
        return new double[]{
            round(swingHigh - 0.236 * diff),
            round(swingHigh - 0.382 * diff),
            round(swingHigh - 0.500 * diff),
            round(swingHigh - 0.618 * diff)
        };
    }

    // ─── Volume Ratio ─────────────────────────────────────────────────────────

    public Double volumeRatio(List<OHLCVData> bars, int avgPeriod) {
        if (bars == null || bars.size() < avgPeriod + 1) return null;
        long currentVol = bars.get(bars.size() - 1).getVolume();
        double avgVol = bars.subList(bars.size() - avgPeriod - 1, bars.size() - 1)
                            .stream().mapToLong(OHLCVData::getVolume).average().orElse(0);
        return avgVol > 0 ? round(currentVol / avgVol) : null;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}