package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.domain.*;
import com.hft.model.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Converts between domain objects and generated protobuf messages.
 * All conversions are null-safe — missing fields map to proto defaults (0 / empty string / false).
 */
public final class ProtoMapper {

    private ProtoMapper() {}

    // ─── Enum Converters ──────────────────────────────────────────────────────

    public static MarketProto toProto(Market m) {
        if (m == null) return MarketProto.MARKET_UNSPECIFIED;
        return switch (m) {
            case US_NYSE   -> MarketProto.US_NYSE;
            case US_NASDAQ -> MarketProto.US_NASDAQ;
            case US_AMEX   -> MarketProto.US_AMEX;
            case US_CBOE   -> MarketProto.US_CBOE;
            case US_COMEX  -> MarketProto.US_COMEX;
            case INDIA_NSE -> MarketProto.INDIA_NSE;
            case INDIA_BSE -> MarketProto.INDIA_BSE;
            case INDIA_MCX -> MarketProto.INDIA_MCX;
            case ALL       -> MarketProto.ALL;
        };
    }

    public static Market fromProto(MarketProto m) {
        if (m == null) return Market.ALL;
        return switch (m) {
            case US_NYSE   -> Market.US_NYSE;
            case US_NASDAQ -> Market.US_NASDAQ;
            case US_AMEX   -> Market.US_AMEX;
            case US_CBOE   -> Market.US_CBOE;
            case US_COMEX  -> Market.US_COMEX;
            case INDIA_NSE -> Market.INDIA_NSE;
            case INDIA_BSE -> Market.INDIA_BSE;
            case INDIA_MCX -> Market.INDIA_MCX;
            default        -> Market.ALL;
        };
    }

    public static AssetTypeProto toProto(AssetType a) {
        if (a == null) return AssetTypeProto.ASSET_TYPE_UNSPECIFIED;
        return switch (a) {
            case STOCK       -> AssetTypeProto.STOCK;
            case OPTION      -> AssetTypeProto.OPTION;
            case FUTURE      -> AssetTypeProto.FUTURE;
            case COMMODITY   -> AssetTypeProto.COMMODITY;
            case IPO         -> AssetTypeProto.IPO;
            case ETF         -> AssetTypeProto.ETF;
            case MUTUAL_FUND -> AssetTypeProto.MUTUAL_FUND;
            case BOND        -> AssetTypeProto.BOND;
            case CURRENCY    -> AssetTypeProto.CURRENCY;
            case CRYPTO      -> AssetTypeProto.CRYPTO;
        };
    }

    public static SignalTypeProto toProto(SignalType s) {
        if (s == null) return SignalTypeProto.SIGNAL_UNSPECIFIED;
        return switch (s) {
            case STRONG_BUY  -> SignalTypeProto.STRONG_BUY;
            case BUY         -> SignalTypeProto.BUY;
            case HOLD        -> SignalTypeProto.HOLD;
            case SELL        -> SignalTypeProto.SELL;
            case STRONG_SELL -> SignalTypeProto.STRONG_SELL;
            case WATCH       -> SignalTypeProto.WATCH;
        };
    }

    public static RiskLevelProto toProto(RiskLevel r) {
        if (r == null) return RiskLevelProto.RISK_UNSPECIFIED;
        return switch (r) {
            case VERY_LOW  -> RiskLevelProto.VERY_LOW;
            case LOW       -> RiskLevelProto.LOW;
            case MEDIUM    -> RiskLevelProto.MEDIUM;
            case HIGH      -> RiskLevelProto.HIGH;
            case VERY_HIGH -> RiskLevelProto.VERY_HIGH;
        };
    }

    public static TimeHorizonProto toProto(TimeHorizon t) {
        if (t == null) return TimeHorizonProto.HORIZON_UNSPECIFIED;
        return switch (t) {
            case INTRADAY    -> TimeHorizonProto.INTRADAY;
            case SHORT_TERM  -> TimeHorizonProto.SHORT_TERM;
            case MEDIUM_TERM -> TimeHorizonProto.MEDIUM_TERM;
            case LONG_TERM   -> TimeHorizonProto.LONG_TERM;
        };
    }

    // ─── Primitive Helpers ────────────────────────────────────────────────────

    private static double d(Double v)      { return v != null ? v : 0.0; }
    private static double d(BigDecimal v)  { return v != null ? v.doubleValue() : 0.0; }
    private static int    i(Integer v)     { return v != null ? v : 0; }
    private static long   l(Long v)        { return v != null ? v : 0L; }
    private static boolean b(Boolean v)    { return Boolean.TRUE.equals(v); }
    private static String s(String v)      { return v != null ? v : ""; }
    private static List<String> ls(List<String> v) { return v != null ? v : List.of(); }

    private static long epochMillis(LocalDateTime dt) {
        return dt != null ? dt.toInstant(ZoneOffset.UTC).toEpochMilli() : 0L;
    }

    private static long epochMillisDate(LocalDate d) {
        return d != null ? d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() : 0L;
    }

    // ─── Domain → Proto ───────────────────────────────────────────────────────

    public static StockQuoteProto toProto(StockQuote q) {
        if (q == null) return StockQuoteProto.getDefaultInstance();
        return StockQuoteProto.newBuilder()
                .setSymbol(s(q.getSymbol()))
                .setCompanyName(s(q.getCompanyName()))
                .setMarket(toProto(q.getMarket()))
                .setAssetType(toProto(q.getAssetType()))
                .setCurrentPrice(d(q.getCurrentPrice()))
                .setOpenPrice(d(q.getOpenPrice()))
                .setHighPrice(d(q.getHighPrice()))
                .setLowPrice(d(q.getLowPrice()))
                .setPreviousClose(d(q.getPreviousClose()))
                .setDayChange(d(q.getDayChange()))
                .setDayChangePercent(d(q.getDayChangePercent()))
                .setWeekChangePercent(d(q.getWeekChangePercent()))
                .setMonthChangePercent(d(q.getMonthChangePercent()))
                .setVolume(l(q.getVolume()))
                .setAvgVolume20Day(l(q.getAvgVolume20Day()))
                .setMarketCap(d(q.getMarketCap()))
                .setCurrency(s(q.getCurrency()))
                .setFiftyTwoWeekHigh(d(q.getFiftyTwoWeekHigh()))
                .setFiftyTwoWeekLow(d(q.getFiftyTwoWeekLow()))
                .setPeRatio(d(q.getPeRatio()))
                .setDividendYield(d(q.getDividendYield()))
                .setBeta(d(q.getBeta()))
                .setSector(s(q.getSector()))
                .setIndustry(s(q.getIndustry()))
                .setTimestampMs(epochMillis(q.getTimestamp()))
                .setDataSource(s(q.getDataSource()))
                .setIsMarketOpen(b(q.getIsMarketOpen()))
                .build();
    }

    public static TechnicalIndicatorsProto toProto(TechnicalIndicators t) {
        if (t == null) return TechnicalIndicatorsProto.getDefaultInstance();
        return TechnicalIndicatorsProto.newBuilder()
                .setSymbol(s(t.getSymbol()))
                .setIntervalType(s(t.getIntervalType()))
                .setSma20(d(t.getSma20()))
                .setSma50(d(t.getSma50()))
                .setSma200(d(t.getSma200()))
                .setEma9(d(t.getEma9()))
                .setEma21(d(t.getEma21()))
                .setEma55(d(t.getEma55()))
                .setSupertrend(d(t.getSupertrend()))
                .setSupertrendBullish(b(t.getSupertrendBullish()))
                .setRsi14(d(t.getRsi14()))
                .setMacdLine(d(t.getMacdLine()))
                .setMacdSignal(d(t.getMacdSignal()))
                .setMacdHistogram(d(t.getMacdHistogram()))
                .setMacdBullishCrossover(b(t.getMacdBullishCrossover()))
                .setStochasticK(d(t.getStochasticK()))
                .setStochasticD(d(t.getStochasticD()))
                .setStochasticBullishCrossover(b(t.getStochasticBullishCrossover()))
                .setWilliamsR(d(t.getWilliamsR()))
                .setCci20(d(t.getCci20()))
                .setBollingerUpper(d(t.getBollingerUpper()))
                .setBollingerMiddle(d(t.getBollingerMiddle()))
                .setBollingerLower(d(t.getBollingerLower()))
                .setBollingerBandWidth(d(t.getBollingerBandWidth()))
                .setBollingerBreakoutUp(b(t.getBollingerBreakoutUp()))
                .setBollingerBreakoutDown(b(t.getBollingerBreakoutDown()))
                .setAtr14(d(t.getAtr14()))
                .setHistoricalVolatility20(d(t.getHistoricalVolatility20()))
                .setImpliedVolatility(d(t.getImpliedVolatility()))
                .setObv(t.getObv() != null ? t.getObv() : 0L)
                .setObvTrendingUp(b(t.getObvTrendingUp()))
                .setVwap(d(t.getVwap()))
                .setMfi14(d(t.getMfi14()))
                .setVolumeRatio(d(t.getVolumeRatio()))
                .setSupportLevel(d(t.getSupportLevel()))
                .setResistanceLevel(d(t.getResistanceLevel()))
                .setFibonacci236(d(t.getFibonacci236()))
                .setFibonacci382(d(t.getFibonacci382()))
                .setFibonacci500(d(t.getFibonacci500()))
                .build();
    }

    public static SentimentDataProto toProto(SentimentData s) {
        if (s == null) return SentimentDataProto.getDefaultInstance();
        return SentimentDataProto.newBuilder()
                .setSymbol(ProtoMapper.s(s.getSymbol()))
                .setMarket(toProto(s.getMarket()))
                .setOverallSentimentScore(d(s.getOverallSentimentScore()))
                .setNewsSentimentScore(d(s.getNewsSentimentScore()))
                .setSocialSentimentScore(d(s.getSocialSentimentScore()))
                .setSentimentLabel(s.getSentimentLabel() != null ? s.getSentimentLabel().name() : "")
                .setTotalMentions24H(i(s.getTotalMentions24h()))
                .setTwitterMentions24H(i(s.getTwitterMentions24h()))
                .setRedditMentions24H(i(s.getRedditMentions24h()))
                .setNewsArticles24H(i(s.getNewsArticles24h()))
                .setMentionTrend(ProtoMapper.s(s.getMentionTrend()))
                .setIsTrending(b(s.getIsTrending()))
                .setPositiveNewsCount(i(s.getPositiveNewsCount()))
                .setNegativeNewsCount(i(s.getNegativeNewsCount()))
                .setNeutralNewsCount(i(s.getNeutralNewsCount()))
                .setHasFedMention(b(s.getHasFedMention()))
                .setHasRbiMention(b(s.getHasRbiMention()))
                .setHasPoliticalRisk(b(s.getHasPoliticalRisk()))
                .setSpecialAlert(ProtoMapper.s(s.getSpecialAlert()))
                .setWindowPeriod(ProtoMapper.s(s.getWindowPeriod()))
                .setComputedAtMs(epochMillis(s.getComputedAt()))
                .build();
    }

    public static FundamentalDataProto toProto(FundamentalData f) {
        if (f == null) return FundamentalDataProto.getDefaultInstance();
        return FundamentalDataProto.newBuilder()
                .setSymbol(s(f.getSymbol()))
                .setMarket(toProto(f.getMarket()))
                .setPeRatio(d(f.getPeRatio()))
                .setPbRatio(d(f.getPbRatio()))
                .setPsRatio(d(f.getPsRatio()))
                .setEvToEbitda(d(f.getEvToEbitda()))
                .setRoe(d(f.getRoe()))
                .setRoa(d(f.getRoa()))
                .setDebtToEquity(d(f.getDebtToEquity()))
                .setCurrentRatio(d(f.getCurrentRatio()))
                .setQuickRatio(d(f.getQuickRatio()))
                .setEpsTtm(d(f.getEpsCurrentYear()))
                .setEpsGrowthYoy(d(f.getEpsGrowthYoY()))
                .setRevenueGrowthYoy(d(f.getRevenueGrowthYoY()))
                .setGrossMargin(d(f.getGrossProfitMargin()))
                .setOperatingMargin(d(f.getOperatingMargin()))
                .setNetMargin(d(f.getNetProfitMargin()))
                .setDividendYield(d(f.getDividendYield()))
                .setDividendPayoutRatio(d(f.getDividendPayoutRatio()))
                .build();
    }

    public static MacroDataProto toProto(MacroData m) {
        if (m == null) return MacroDataProto.getDefaultInstance();
        return MacroDataProto.newBuilder()
                .setMarket(toProto(m.getMarket()))
                .setInterestRate(d(m.getInterestRate()))
                .setInterestRatePrev(d(m.getInterestRatePreviousMeeting()))
                .setInterestRateOutlook(s(m.getInterestRateOutlook()))
                .setCpiInflationRate(d(m.getCpiInflationRate()))
                .setWpiInflationRate(d(m.getWpiInflationRate()))
                .setInflationTrend(s(m.getInflationTrend()))
                .setGdpGrowthRateQoq(d(m.getGdpGrowthRateQoQ()))
                .setGdpGrowthRateYoy(d(m.getGdpGrowthRateYoY()))
                .setGdpGrowthForecast(d(m.getGdpGrowthForecast()))
                .setUnemploymentRate(d(m.getUnemploymentRate()))
                .setVixLevel(d(m.getVixLevel()))
                .setVixSentiment(s(m.getVixSentiment()))
                .setTenYearYield(d(m.getTenYearYield()))
                .setTwoYearYield(d(m.getTwoYearYield()))
                .setIsYieldCurveInverted(b(m.getIsYieldCurveInverted()))
                .setUsdInrRate(d(m.getUsdInrRate()))
                .setDxyIndex(d(m.getDxyIndex()))
                .setFiiNetFlowCrores(d(m.getFiiNetFlowCrores()))
                .setDiiNetFlowCrores(d(m.getDiiNetFlowCrores()))
                .setCrudeBrentPrice(d(m.getCrudeBrentPrice()))
                .setCrudeBrentChangePct(d(m.getCrudeBrentChangePercent()))
                .setGoldPriceUsd(d(m.getGoldPriceUsd()))
                .setGoldPriceInr(d(m.getGoldPriceInr()))
                .setGeopoliticalRiskScore(d(m.getGeopoliticalRiskScore()))
                .setGeopoliticalRiskLabel(s(m.getGeopoliticalRiskLabel()))
                .setMacroScore(d(m.getMacroScore()))
                .setMacroSentiment(s(m.getMacroSentiment()))
                .setSectorTailwinds(s(m.getSectorTailwinds()))
                .setSectorHeadwinds(s(m.getSectorHeadwinds()))
                .setMajorUpcomingEvents(s(m.getMajorUpcomingEvents()))
                .setLastUpdatedMs(epochMillis(m.getLastUpdated()))
                .build();
    }

    public static TradeRecommendationProto toProto(TradeRecommendation r) {
        if (r == null) return TradeRecommendationProto.getDefaultInstance();
        TradeRecommendationProto.Builder b = TradeRecommendationProto.newBuilder()
                .setId(s(r.getId()))
                .setSymbol(s(r.getSymbol()))
                .setCompanyName(s(r.getCompanyName()))
                .setMarket(toProto(r.getMarket()))
                .setAssetType(toProto(r.getAssetType()))
                .setSector(s(r.getSector()))
                .setSectorOutlook(s(r.getSectorOutlook()))
                .setSignal(toProto(r.getSignal()))
                .setTimeHorizon(toProto(r.getTimeHorizon()))
                .setRiskLevel(toProto(r.getRiskLevel()))
                .setCurrentPrice(d(r.getCurrentPrice()))
                .setEntryPrice(d(r.getEntryPrice()))
                .setEntryPriceRange(s(r.getEntryPriceRange()))
                .setTargetPrice(d(r.getTargetPrice()))
                .setStopLossPrice(d(r.getStopLossPrice()))
                .setExpectedProfitPct(d(r.getExpectedProfitPercent()))
                .setMaxRiskPct(d(r.getMaxRiskPercent()))
                .setRiskRewardRatio(d(r.getRiskRewardRatio()))
                .setHoldingPeriodDays(i(r.getHoldingPeriodDays()))
                .setCompositeScore(d(r.getCompositeScore()))
                .setConfidencePct(d(r.getConfidencePercent()))
                .setTechnicalScore(d(r.getTechnicalScore()))
                .setFundamentalScore(d(r.getFundamentalScore()))
                .setSentimentScore(d(r.getSentimentScore()))
                .setMacroScore(d(r.getMacroScore()))
                .setMlScore(d(r.getMlScore()))
                .addAllKeyReasons(ls(r.getKeyReasons()))
                .addAllKeyRisks(ls(r.getKeyRisks()))
                .addAllRelatedNews(ls(r.getRelatedNews()))
                .addAllDataSources(ls(r.getDataSources()))
                .setGeneratedAtMs(epochMillis(r.getGeneratedAt()))
                .setValidUntilMs(epochMillis(r.getValidUntil()))
                .setStatus(s(r.getStatus()))
                .setRank(s(r.getRank()));
        return b.build();
    }
}
