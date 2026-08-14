package com.hft.util;

import com.hft.model.domain.OHLCVData;
import com.hft.model.enums.Market;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TechnicalIndicatorUtil — the math engine.
 * Tests with synthetic price series for deterministic results.
 */
@DisplayName("Technical Indicator Utility Tests")
class TechnicalIndicatorUtilTest {

    private TechnicalIndicatorUtil util;
    private List<OHLCVData> trendingUpBars;    // steadily rising price
    private List<OHLCVData> trendingDownBars;  // steadily falling price
    private List<OHLCVData> flatBars;          // sideways price

    @BeforeEach
    void setUp() {
        util = new TechnicalIndicatorUtil();
        trendingUpBars   = generateBars(100.0, 0.5, 250);   // +0.5/day for 250 days
        trendingDownBars = generateBars(200.0, -0.5, 250);  // -0.5/day for 250 days
        flatBars         = generateBars(150.0, 0.0, 250);   // flat for 250 days
    }

    // ─── SMA Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SMA(20) should return null if insufficient data")
    void sma_insufficientData_returnsNull() {
        List<OHLCVData> shortList = generateBars(100.0, 1.0, 5);
        assertThat(util.sma(shortList, 20)).isNull();
    }

    @Test
    @DisplayName("SMA(20) for flat price should equal that price")
    void sma_flatPriceSeries_equalsPrice() {
        Double sma = util.sma(flatBars, 20);
        assertThat(sma).isNotNull().isCloseTo(150.0, offset(0.01));
    }

    @Test
    @DisplayName("SMA(50) < SMA(200) in a downtrend (death cross territory)")
    void sma_downtrend_sma50BelowSma200() {
        Double sma50  = util.sma(trendingDownBars, 50);
        Double sma200 = util.sma(trendingDownBars, 200);
        assertThat(sma50).isNotNull();
        assertThat(sma200).isNotNull();
        assertThat(sma50).isLessThan(sma200);
    }

    // ─── EMA Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EMA(9) > EMA(21) in an uptrend (bullish momentum)")
    void ema_uptrend_fastEmaAboveSlowEma() {
        Double ema9  = util.ema(trendingUpBars, 9);
        Double ema21 = util.ema(trendingUpBars, 21);
        assertThat(ema9).isNotNull();
        assertThat(ema21).isNotNull();
        assertThat(ema9).isGreaterThan(ema21);
    }

    // ─── RSI Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RSI should be between 0 and 100")
    void rsi_alwaysInValidRange() {
        Double rsi = util.rsi(trendingUpBars, 14);
        assertThat(rsi).isNotNull().isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("RSI in a perfect uptrend should be high (>70)")
    void rsi_uptrend_isHigh() {
        Double rsi = util.rsi(trendingUpBars, 14);
        assertThat(rsi).isNotNull().isGreaterThan(70.0);
    }

    @Test
    @DisplayName("RSI in a perfect downtrend should be low (<30)")
    void rsi_downtrend_isLow() {
        Double rsi = util.rsi(trendingDownBars, 14);
        assertThat(rsi).isNotNull().isLessThan(30.0);
    }

    @Test
    @DisplayName("RSI flat price = 50")
    void rsi_flatPrice_isNeutral() {
        Double rsi = util.rsi(flatBars, 14);
        assertThat(rsi).isNotNull().isCloseTo(50.0, offset(5.0));
    }

    // ─── Bollinger Bands Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Upper band > Middle band > Lower band always")
    void bollingerBands_bandOrder() {
        double[] bb = util.bollingerBands(trendingUpBars, 20, 2.0);
        assertThat(bb).isNotNull();
        assertThat(bb[0]).isGreaterThan(bb[1]);  // upper > middle
        assertThat(bb[1]).isGreaterThan(bb[2]);  // middle > lower
    }

    @Test
    @DisplayName("Flat price → narrow Bollinger Bands (low bandwidth)")
    void bollingerBands_flatPrice_narrowBands() {
        double[] bb = util.bollingerBands(flatBars, 20, 2.0);
        assertThat(bb).isNotNull();
        assertThat(bb[3]).isLessThan(1.0);  // bandwidth% should be near 0 for flat price
    }

    // ─── ATR Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ATR should be positive")
    void atr_isPositive() {
        Double atr = util.atr(trendingUpBars, 14);
        assertThat(atr).isNotNull().isGreaterThan(0.0);
    }

    // ─── OBV Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OBV should be non-null for sufficient data")
    void obv_nonNull() {
        assertThat(util.obv(trendingUpBars)).isNotNull();
    }

    // ─── Fibonacci Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Fibonacci levels should be between swing low and swing high")
    void fibonacci_levelsWithinRange() {
        double[] fibs = util.fibonacciLevels(200.0, 100.0);
        assertThat(fibs).hasSize(4);
        for (double level : fibs) {
            assertThat(level).isBetween(100.0, 200.0);
        }
    }

    // ─── Technical Score Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Technical score in uptrend should be > 60")
    void technicalScore_uptrend_isHigh() {
        double price = trendingUpBars.get(trendingUpBars.size() - 1).getClose().doubleValue();
        Double sma20 = util.sma(trendingUpBars, 20);
        Double sma50 = util.sma(trendingUpBars, 50);
        Double sma200 = util.sma(trendingUpBars, 200);
        Double ema9 = util.ema(trendingUpBars, 9);
        Double ema21 = util.ema(trendingUpBars, 21);
        Double rsi = util.rsi(trendingUpBars, 14);
        double[] macd = util.macd(trendingUpBars, 12, 26, 9);

        double score = util.computeTechnicalScore(price, sma20, sma50, sma200,
                ema9, ema21, rsi, macd, null, null, null, true, null, List.of());

        assertThat(score).isBetween(0.0, 100.0);
        assertThat(score).isGreaterThan(60.0);
    }

    @Test
    @DisplayName("Technical score in downtrend should be < 40")
    void technicalScore_downtrend_isLow() {
        double price = trendingDownBars.get(trendingDownBars.size() - 1).getClose().doubleValue();
        Double sma20 = util.sma(trendingDownBars, 20);
        Double sma50 = util.sma(trendingDownBars, 50);
        Double sma200 = util.sma(trendingDownBars, 200);

        double score = util.computeTechnicalScore(price, sma20, sma50, sma200,
                null, null, util.rsi(trendingDownBars, 14),
                util.macd(trendingDownBars, 12, 26, 9),
                null, null, null, false, null, List.of());

        assertThat(score).isBetween(0.0, 100.0);
        assertThat(score).isLessThan(40.0);
    }

    // ─── Helper: Generate Synthetic Price Bars ────────────────────────────────

    private List<OHLCVData> generateBars(double startPrice, double dailyChange, int count) {
        List<OHLCVData> bars = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < count; i++) {
            price = Math.max(0.01, price + dailyChange);
            double high  = price * 1.005;
            double low   = price * 0.995;
            double open  = price - dailyChange * 0.3;
            long vol = 1_000_000L + (long)(Math.random() * 500_000);

            OHLCVData bar = OHLCVData.builder()
                    .symbol("TEST")
                    .market(Market.US_NASDAQ)
                    .barDate(date.plusDays(i))
                    .intervalType("1D")
                    .open(BigDecimal.valueOf(Math.max(0.01, open)))
                    .high(BigDecimal.valueOf(high))
                    .low(BigDecimal.valueOf(low))
                    .close(BigDecimal.valueOf(price))
                    .adjustedClose(BigDecimal.valueOf(price))
                    .volume(vol)
                    .build();
            bars.add(bar);
        }
        return bars;
    }
}