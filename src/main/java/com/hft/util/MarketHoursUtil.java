package com.hft.util;

import com.hft.model.enums.Market;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Utility for checking market hours, holidays, and session timings.
 */
@Component
public class MarketHoursUtil {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // US market holidays 2026 (add more years as needed)
    private static final Set<LocalDate> US_HOLIDAYS_2026 = Set.of(
        LocalDate.of(2026, 1, 1),   // New Year's Day
        LocalDate.of(2026, 1, 19),  // MLK Day
        LocalDate.of(2026, 2, 16),  // Presidents Day
        LocalDate.of(2026, 4, 3),   // Good Friday
        LocalDate.of(2026, 5, 25),  // Memorial Day
        LocalDate.of(2026, 6, 19),  // Juneteenth
        LocalDate.of(2026, 7, 3),   // Independence Day (observed)
        LocalDate.of(2026, 9, 7),   // Labor Day
        LocalDate.of(2026, 11, 26), // Thanksgiving
        LocalDate.of(2026, 12, 25)  // Christmas
    );

    // India NSE holidays 2026 (Muhurat trading still happens on some)
    private static final Set<LocalDate> NSE_HOLIDAYS_2026 = Set.of(
        LocalDate.of(2026, 1, 26),  // Republic Day
        LocalDate.of(2026, 3, 25),  // Holi
        LocalDate.of(2026, 4, 2),   // Ram Navami
        LocalDate.of(2026, 4, 3),   // Good Friday
        LocalDate.of(2026, 4, 14),  // Ambedkar Jayanti / Baisakhi
        LocalDate.of(2026, 5, 1),   // Maharashtra Day
        LocalDate.of(2026, 8, 15),  // Independence Day
        LocalDate.of(2026, 10, 2),  // Gandhi Jayanti
        LocalDate.of(2026, 10, 21), // Dussehra (approximate)
        LocalDate.of(2026, 11, 4),  // Diwali Balipratipada
        LocalDate.of(2026, 11, 5),  // Diwali (Laxmi Pujan – Muhurat only)
        LocalDate.of(2026, 12, 25)  // Christmas
    );

    public boolean isUSMarketOpen() {
        return isMarketOpen(ZoneId.of("America/New_York"),
                            LocalTime.of(9, 30),
                            LocalTime.of(16, 0),
                            US_HOLIDAYS_2026);
    }

    public boolean isIndianMarketOpen() {
        return isMarketOpen(ZoneId.of("Asia/Kolkata"),
                            LocalTime.of(9, 15),
                            LocalTime.of(15, 30),
                            NSE_HOLIDAYS_2026);
    }

    public boolean isMarketOpen(Market market) {
        return switch (market) {
            case US_NYSE, US_NASDAQ, US_AMEX, US_CBOE, US_COMEX -> isUSMarketOpen();
            case INDIA_NSE, INDIA_BSE, INDIA_MCX                -> isIndianMarketOpen();
            default -> false;
        };
    }

    /** Time (in target market timezone) until next market open. */
    public Duration timeUntilMarketOpen(Market market) {
        ZoneId zone = ZoneId.of(market.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime openTime = LocalTime.parse(market.getMarketOpenTime(), TIME_FMT);
        ZonedDateTime nextOpen = now.with(openTime);
        if (nextOpen.isBefore(now)) {
            nextOpen = nextOpen.plusDays(1); // move to next day
        }
        return Duration.between(now, nextOpen);
    }

    /** Returns whether today is a trading day (not weekend, not holiday). */
    public boolean isTradingDay(LocalDate date, Market market) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        Set<LocalDate> holidays = market.isUS() ? US_HOLIDAYS_2026 : NSE_HOLIDAYS_2026;
        return !holidays.contains(date);
    }

    /** Get current time as string in the given market's timezone. */
    public String currentMarketTime(Market market) {
        return ZonedDateTime.now(ZoneId.of(market.getTimezone()))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private boolean isMarketOpen(ZoneId zone, LocalTime open, LocalTime close,
                                  Set<LocalDate> holidays) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        DayOfWeek day = today.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        if (holidays.contains(today)) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(open) && time.isBefore(close);
    }
}