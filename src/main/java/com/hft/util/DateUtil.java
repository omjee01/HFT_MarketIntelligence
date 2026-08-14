package com.hft.util;

import com.hft.model.enums.Market;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Date/time helper utilities for multi-timezone market data handling. */
@Component
public class DateUtil {

    public static final ZoneId UTC       = ZoneId.of("UTC");
    public static final ZoneId IST       = ZoneId.of("Asia/Kolkata");
    public static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    public static final DateTimeFormatter ISO_DATE     = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter ALPHA_VANTAGE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Current time in market's timezone */
    public LocalDateTime nowInMarketTimezone(Market market) {
        return LocalDateTime.now(ZoneId.of(market.getTimezone()));
    }

    /** Convert UTC instant to market-local time */
    public LocalDateTime utcToMarket(LocalDateTime utcTime, Market market) {
        return utcTime.atZone(UTC)
                      .withZoneSameInstant(ZoneId.of(market.getTimezone()))
                      .toLocalDateTime();
    }

    /** Convert market-local time to UTC */
    public LocalDateTime marketToUtc(LocalDateTime marketTime, Market market) {
        return marketTime.atZone(ZoneId.of(market.getTimezone()))
                         .withZoneSameInstant(UTC)
                         .toLocalDateTime();
    }

    /** Get the most recent trading day (skip weekends) */
    public LocalDate lastTradingDay(LocalDate from) {
        LocalDate date = from;
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY
            || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    /** Format LocalDate for Alpha Vantage / Yahoo Finance API calls */
    public String toApiDateString(LocalDate date) {
        return date.format(ALPHA_VANTAGE_FMT);
    }

    /** How many trading days between two dates (rough estimate, ignores holidays) */
    public int tradingDaysBetween(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
            d = d.plusDays(1);
        }
        return count;
    }

    /** Add N trading days to a date */
    public LocalDate addTradingDays(LocalDate from, int days) {
        LocalDate d = from;
        int count = 0;
        while (count < days) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return d;
    }

    public boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
            || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public LocalDate today()     { return LocalDate.now(); }
    public LocalDateTime nowUtc(){ return LocalDateTime.now(UTC); }
}