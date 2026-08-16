package com.hft.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MarketCapTier.fromMarketCap() Tests")
class MarketCapTierTest {

    @Test
    @DisplayName("Null market cap defaults to MID, not an extreme tier")
    void fromMarketCap_null_defaultsToMid() {
        assertThat(MarketCapTier.fromMarketCap(null)).isEqualTo(MarketCapTier.MID);
    }

    @Test
    @DisplayName("Boundary values land in the correct tier, not the adjacent one")
    void fromMarketCap_boundaries_correctTier() {
        assertThat(MarketCapTier.fromMarketCap(bd(299_999_999L))).isEqualTo(MarketCapTier.MICRO);
        assertThat(MarketCapTier.fromMarketCap(bd(300_000_000L))).isEqualTo(MarketCapTier.SMALL);
        assertThat(MarketCapTier.fromMarketCap(bd(1_999_999_999L))).isEqualTo(MarketCapTier.SMALL);
        assertThat(MarketCapTier.fromMarketCap(bd(2_000_000_000L))).isEqualTo(MarketCapTier.MID);
        assertThat(MarketCapTier.fromMarketCap(bd(9_999_999_999L))).isEqualTo(MarketCapTier.MID);
        assertThat(MarketCapTier.fromMarketCap(bd(10_000_000_000L))).isEqualTo(MarketCapTier.LARGE);
        assertThat(MarketCapTier.fromMarketCap(bd(199_999_999_999L))).isEqualTo(MarketCapTier.LARGE);
        assertThat(MarketCapTier.fromMarketCap(bd(200_000_000_000L))).isEqualTo(MarketCapTier.MEGA);
    }

    @Test
    @DisplayName("Tier ordinals run largest-first: MEGA=0 .. MICRO=4")
    void ordinals_largestFirst() {
        assertThat(MarketCapTier.MEGA.ordinal()).isEqualTo(0);
        assertThat(MarketCapTier.MICRO.ordinal()).isEqualTo(4);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
