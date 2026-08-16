package com.hft.service.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AlphaVantageBudgetGuard Tests")
class AlphaVantageBudgetGuardTest {

    private AlphaVantageBudgetGuard guardWithBudget(int budget, Clock clock) {
        AlphaVantageBudgetGuard guard = new AlphaVantageBudgetGuard(clock);
        ReflectionTestUtils.setField(guard, "dailyBudget", budget);
        return guard;
    }

    @Test
    @DisplayName("Allows calls up to the budget, then blocks")
    void tryConsume_upToBudget_thenBlocks() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        AlphaVantageBudgetGuard guard = guardWithBudget(3, fixed);

        assertThat(guard.tryConsume()).isTrue();
        assertThat(guard.tryConsume()).isTrue();
        assertThat(guard.tryConsume()).isTrue();
        assertThat(guard.tryConsume()).isFalse();   // 4th call, budget was 3
        assertThat(guard.tryConsume()).isFalse();   // stays blocked, doesn't wrap/reset
    }

    @Test
    @DisplayName("remainingToday() reflects consumption accurately")
    void remainingToday_decreasesWithEachConsume() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        AlphaVantageBudgetGuard guard = guardWithBudget(5, fixed);

        assertThat(guard.remainingToday()).isEqualTo(5);
        guard.tryConsume();
        guard.tryConsume();
        assertThat(guard.remainingToday()).isEqualTo(3);
    }

    @Test
    @DisplayName("Budget resets when the day rolls over (UTC)")
    void tryConsume_newDay_resetsBudget() {
        java.util.concurrent.atomic.AtomicReference<Instant> now =
                new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-08-16T23:59:00Z"));
        Clock movable = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        AlphaVantageBudgetGuard guard = guardWithBudget(1, movable);

        assertThat(guard.tryConsume()).isTrue();
        assertThat(guard.tryConsume()).isFalse();   // exhausted for 2026-08-16

        now.set(Instant.parse("2026-08-17T00:01:00Z"));   // next day
        assertThat(guard.tryConsume()).isTrue();          // budget reset
    }

    @Test
    @DisplayName("Never throws — pure allow/deny signal")
    void tryConsume_manyCalls_neverThrows() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        AlphaVantageBudgetGuard guard = guardWithBudget(2, fixed);

        assertThatCode(() -> {
            for (int i = 0; i < 50; i++) guard.tryConsume();
        }).doesNotThrowAnyException();
    }
}
