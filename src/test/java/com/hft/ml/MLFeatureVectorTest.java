package com.hft.ml;

import com.hft.intelligence.SourceSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MLFeatureVector.toContextArray() Tests")
class MLFeatureVectorTest {

    // Every field set to its own distinct value (field index) so a field-order mistake in
    // toContextArray() shows up as a value landing at the wrong index, not just a length
    // mismatch — this is the exact kind of bug that would silently corrupt every ASRB source's
    // learned reliability posterior (Stage 10) without ever throwing or crashing.
    private MLFeatureVector fullVector() {
        return MLFeatureVector.builder()
                .symbol("AAPL").market("US_NASDAQ")
                .rsi14(0).macdLine(1).macdHistogram(2).bbPosition(3).bbWidth(4)
                .sma20Distance(5).sma50Distance(6).sma200Distance(7).ema9Distance(8)
                .atrNormalized(9).volumeRatio(10).obvTrend(11).technicalScore(12).smaAlignment(13)
                .peRatioNorm(14).pbRatio(15).roe(16).debtToEquity(17).revenueGrowthYoY(18)
                .epsGrowthYoY(19).dividendYield(20).fundamentalScore(21)
                .sentimentRaw(22).bullishPercent(23).bearishPercent(24).newsCountLog(25)
                .mentionsLog(26).sentimentMomentum(27).normalizedSentiment(28)
                .gdpGrowthRate(29).inflationRate(30).centralBankRate(31).vixLevel(32)
                .fiiFlowNorm(33).macroScore(34).marketRegime(35)
                .percentFrom52High(36).percentFrom52Low(37).dayChangePct(38)
                .volumeSpike(39).marketCapClass(40)
                .build();
    }

    @Test
    @DisplayName("toContextArray() is exactly 41-dimensional — matches SourceSignal.CONTEXT_DIM")
    void toContextArray_length_matchesSourceSignalContextDim() {
        assertThat(fullVector().toContextArray()).hasSize(SourceSignal.CONTEXT_DIM);
    }

    @Test
    @DisplayName("toContextArray() preserves declaration order — index i holds field value i")
    void toContextArray_order_matchesFieldDeclarationOrder() {
        double[] context = fullVector().toContextArray();
        for (int i = 0; i < 41; i++) {
            assertThat(context[i]).as("context[%d]", i).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("toContextArray() output is accepted by SourceSignal's dimension validation")
    void toContextArray_isValidSourceSignalContext() {
        double[] context = fullVector().toContextArray();
        assertThatCode(() -> new SourceSignal("test-source", 50.0, "AAPL", java.time.Instant.now(), context))
                .doesNotThrowAnyException();
    }
}
