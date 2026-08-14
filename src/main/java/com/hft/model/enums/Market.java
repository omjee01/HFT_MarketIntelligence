package com.hft.model.enums;

/**
 * Represents the exchange / market where the asset is traded.
 */
public enum Market {
    // ─── US Markets ───────────────────────────────────────────────────────────
    US_NYSE("New York Stock Exchange", "USD", "America/New_York", "09:30", "16:00"),
    US_NASDAQ("NASDAQ", "USD", "America/New_York", "09:30", "16:00"),
    US_AMEX("American Stock Exchange", "USD", "America/New_York", "09:30", "16:00"),
    US_CBOE("Chicago Board Options Exchange", "USD", "America/Chicago", "08:30", "15:00"),
    US_COMEX("COMEX Commodities", "USD", "America/New_York", "18:00", "17:00"),

    // ─── Indian Markets ───────────────────────────────────────────────────────
    INDIA_NSE("National Stock Exchange of India", "INR", "Asia/Kolkata", "09:15", "15:30"),
    INDIA_BSE("Bombay Stock Exchange", "INR", "Asia/Kolkata", "09:15", "15:30"),
    INDIA_MCX("Multi Commodity Exchange of India", "INR", "Asia/Kolkata", "09:00", "23:30"),

    // ─── Generic ──────────────────────────────────────────────────────────────
    ALL("All Markets", "MIXED", "UTC", "00:00", "23:59");

    private final String displayName;
    private final String currency;
    private final String timezone;
    private final String marketOpenTime;   // HH:mm
    private final String marketCloseTime;  // HH:mm

    Market(String displayName, String currency, String timezone,
           String marketOpenTime, String marketCloseTime) {
        this.displayName = displayName;
        this.currency = currency;
        this.timezone = timezone;
        this.marketOpenTime = marketOpenTime;
        this.marketCloseTime = marketCloseTime;
    }

    public String getDisplayName()    { return displayName; }
    public String getCurrency()       { return currency; }
    public String getTimezone()       { return timezone; }
    public String getMarketOpenTime() { return marketOpenTime; }
    public String getMarketCloseTime(){ return marketCloseTime; }

    public boolean isUS()     { return name().startsWith("US_"); }
    public boolean isIndian() { return name().startsWith("INDIA_"); }
}