package com.hft.service.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alpha Vantage's free tier is 25 requests/day, TOTAL, shared across every function
 * (GLOBAL_QUOTE, NEWS_SENTIMENT, TIME_SERIES_DAILY_ADJUSTED — confirmed directly against the
 * API's own rate-limit response, see docs/STAGE9_INFRASTRUCTURE.md §6). Before this guard,
 * every one of those call sites fired independently with no shared awareness of that budget,
 * so a handful of symbols on one poll cycle could exhaust the whole day's allowance in seconds
 * — every call after that still went out over the network, just to get back the same rate-limit
 * "Information" message. This tracks a shared daily counter and lets each call site check
 * BEFORE making the HTTP request, so an exhausted day fails closed locally instead of
 * round-tripping to Alpha Vantage for nothing.
 *
 * Single-instance, in-memory counter — correct for one running instance (the deployment shape
 * this platform actually has today), not coordinated across multiple nodes sharing one API key.
 * Multi-node coordination would need a shared store (Redis, matching the pattern already used
 * elsewhere in this codebase) — not built here since there's only ever been one instance to
 * coordinate. Worth revisiting if that changes.
 */
@Slf4j
@Component
public class AlphaVantageBudgetGuard {

    @Value("${hft.alpha-vantage.daily-call-budget:20}")
    private int dailyBudget;

    private final Clock clock;
    private final AtomicInteger callsToday = new AtomicInteger(0);
    private volatile LocalDate budgetDay;
    private volatile boolean exhaustionLogged = false;

    public AlphaVantageBudgetGuard() {
        this(Clock.systemUTC());
    }

    AlphaVantageBudgetGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return true if this call may proceed (and is now counted against today's budget),
     *         false if today's budget is already spent — caller should skip the HTTP request
     *         entirely and fall back the same way an empty/failed response would.
     */
    public synchronized boolean tryConsume() {
        rolloverIfNewDay();
        if (callsToday.get() >= dailyBudget) {
            if (!exhaustionLogged) {
                log.warn("[AlphaVantage] Daily call budget ({}) exhausted for {} — further calls "
                        + "skipped locally until the next day, rather than round-tripping for a "
                        + "guaranteed rate-limit response.", dailyBudget, budgetDay);
                exhaustionLogged = true;
            }
            return false;
        }
        callsToday.incrementAndGet();
        return true;
    }

    public int remainingToday() {
        rolloverIfNewDay();
        return Math.max(0, dailyBudget - callsToday.get());
    }

    private void rolloverIfNewDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(budgetDay)) {
            budgetDay = today;
            callsToday.set(0);
            exhaustionLogged = false;
        }
    }
}
