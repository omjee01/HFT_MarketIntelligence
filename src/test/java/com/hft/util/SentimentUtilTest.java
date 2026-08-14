package com.hft.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Sentiment Utility Tests")
class SentimentUtilTest {

    private SentimentUtil util;

    @BeforeEach
    void setUp() { util = new SentimentUtil(); }

    @Test
    @DisplayName("Bullish headline → positive score")
    void score_bullishHeadline_positive() {
        double score = util.score("AAPL beats earnings expectations with record revenue surge");
        assertThat(score).isGreaterThan(0);
    }

    @Test
    @DisplayName("Bearish headline → negative score")
    void score_bearishHeadline_negative() {
        double score = util.score("TSLA plunges as earnings miss estimates; analysts downgrade");
        assertThat(score).isLessThan(0);
    }

    @Test
    @DisplayName("Neutral headline → near zero score")
    void score_neutralHeadline_nearZero() {
        double score = util.score("Company reports quarterly results in line with expectations");
        assertThat(score).isBetween(-0.3, 0.3);
    }

    @Test
    @DisplayName("Null text → zero score")
    void score_null_returnsZero() {
        assertThat(util.score(null)).isEqualTo(0.0);
        assertThat(util.score("")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Score is clamped to [-1, 1]")
    void score_clampedToRange() {
        double score = util.score("record record record beat surge bullish outperform strong growth");
        assertThat(score).isBetween(-1.0, 1.0);
    }

    @Test
    @DisplayName("Negation: 'not strong' → should not be fully positive")
    void score_negation_reducesPositivity() {
        double withNegation    = util.score("company is not strong this quarter");
        double withoutNegation = util.score("company is strong this quarter");
        assertThat(withNegation).isLessThan(withoutNegation);
    }

    @Test
    @DisplayName("Extract ticker from $AAPL cashtag")
    void extractTickers_cashtagFormat() {
        List<String> tickers = util.extractTickers("$AAPL is surging today! $MSFT also moving up.");
        assertThat(tickers).contains("AAPL", "MSFT");
    }

    @Test
    @DisplayName("Aggregate score of bullish texts → positive")
    void aggregateScore_bullishTexts_positive() {
        List<String> texts = List.of("record earnings beat", "strong growth outlook",
                                      "analyst upgrade outperform");
        assertThat(util.aggregateScore(texts)).isGreaterThan(0);
    }
}