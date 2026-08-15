package com.hft.ml;

import com.hft.model.domain.*;
import com.hft.model.enums.Market;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Assembles an MLFeatureVector from raw domain objects.
 * All field accesses are null-safe; missing data falls back to neutral defaults
 * so the models always receive a complete, bounded vector.
 */
@Component
public class MLFeatureExtractor {

    public MLFeatureVector extract(String symbol, Market market,
                                    StockQuote quote,
                                    TechnicalIndicators ta,
                                    FundamentalData fd,
                                    SentimentData sentiment,
                                    MacroData macro) {
        double price = bd(quote != null ? quote.getCurrentPrice() : null, 1.0);

        return MLFeatureVector.builder()
                .symbol(symbol)
                .market(market.name())
                // ── Technical ────────────────────────────────────────────────
                .rsi14(d(ta != null ? ta.getRsi14() : null, 50.0))
                .macdLine(d(ta != null ? ta.getMacdLine() : null, 0.0))
                .macdHistogram(d(ta != null ? ta.getMacdHistogram() : null, 0.0))
                .bbPosition(bbPosition(price, ta))
                .bbWidth(bbWidth(ta))
                .sma20Distance(distance(price, d(ta != null ? ta.getSma20() : null, 0.0)))
                .sma50Distance(distance(price, d(ta != null ? ta.getSma50() : null, 0.0)))
                .sma200Distance(distance(price, d(ta != null ? ta.getSma200() : null, 0.0)))
                .ema9Distance(distance(price, d(ta != null ? ta.getEma9() : null, 0.0)))
                .atrNormalized(price > 0 && ta != null && ta.getAtr14() != null ? ta.getAtr14() / price : 0.0)
                .volumeRatio(d(ta != null ? ta.getVolumeRatio() : null, 1.0))
                .obvTrend(ta != null && ta.getObvTrendingUp() != null ? (ta.getObvTrendingUp() ? 1.0 : -1.0) : 0.0)
                .technicalScore(d(ta != null ? ta.getTechnicalScore() : null, 50.0))
                .smaAlignment(smaAlignment(price, ta))
                // ── Fundamental ───────────────────────────────────────────────
                .peRatioNorm(fd != null && fd.getPeRatio() != null ? Math.min(100, fd.getPeRatio()) : 25.0)
                .pbRatio(d(fd != null ? fd.getPbRatio() : null, 2.0))
                .roe(d(fd != null ? fd.getRoe() : null, 0.0))
                .debtToEquity(d(fd != null ? fd.getDebtToEquity() : null, 1.0))
                .revenueGrowthYoY(d(fd != null ? fd.getRevenueGrowthYoY() : null, 0.0))
                .epsGrowthYoY(d(fd != null ? fd.getEpsGrowthYoY() : null, 0.0))
                .dividendYield(d(fd != null ? fd.getDividendYield() : null, 0.0))
                .fundamentalScore(d(fd != null ? fd.getFundamentalScore() : null, 50.0))
                // ── Sentiment ─────────────────────────────────────────────────
                .sentimentRaw(d(sentiment != null ? sentiment.getOverallSentimentScore() : null, 0.0))
                .bullishPercent(bullishPct(sentiment))
                .bearishPercent(bearishPct(sentiment))
                .newsCountLog(sentiment != null && sentiment.getNewsArticles24h() != null
                              ? Math.log1p(sentiment.getNewsArticles24h()) : 0.0)
                .mentionsLog(sentiment != null && sentiment.getTotalMentions24h() != null
                             ? Math.log1p(sentiment.getTotalMentions24h()) : 0.0)
                .sentimentMomentum(sentimentMomentum(sentiment))
                .normalizedSentiment(sentiment != null ? sentiment.getNormalizedScore() : 50.0)
                // ── Macro ─────────────────────────────────────────────────────
                .gdpGrowthRate(d(macro != null ? macro.getGdpGrowthRateYoY() : null, 2.5))
                .inflationRate(d(macro != null ? macro.getCpiInflationRate() : null, 3.0))
                .centralBankRate(d(macro != null ? macro.getInterestRate() : null, 5.0))
                .vixLevel(d(macro != null ? macro.getVixLevel() : null, 18.0))
                .fiiFlowNorm(fiiFlowNorm(macro))
                .macroScore(d(macro != null ? macro.getMacroScore() : null, 50.0))
                .marketRegime(marketRegime(macro))
                // ── Price ─────────────────────────────────────────────────────
                .percentFrom52High(quote != null && quote.getPercentFrom52WeekHigh() != null
                                   ? bd(quote.getPercentFrom52WeekHigh(), 0.0) / 100.0 : 0.0)
                .percentFrom52Low(quote != null && quote.getPercentFrom52WeekLow() != null
                                  ? bd(quote.getPercentFrom52WeekLow(), 0.0) / 100.0 : 0.0)
                .dayChangePct(bd(quote != null ? quote.getDayChangePercent() : null, 0.0))
                .volumeSpike(d(ta != null ? ta.getVolumeRatio() : null, 1.0) - 1.0)
                .marketCapClass(marketCapClass(quote))
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double bbPosition(double price, TechnicalIndicators ta) {
        if (ta == null || ta.getBollingerLower() == null || ta.getBollingerUpper() == null) return 0.5;
        double range = ta.getBollingerUpper() - ta.getBollingerLower();
        return range <= 0 ? 0.5 : Math.min(1.0, Math.max(0.0, (price - ta.getBollingerLower()) / range));
    }

    private double bbWidth(TechnicalIndicators ta) {
        if (ta == null) return 0.04;
        if (ta.getBollingerBandWidth() != null) return ta.getBollingerBandWidth() / 100.0;
        if (ta.getBollingerMiddle() == null || ta.getBollingerMiddle() == 0) return 0.04;
        if (ta.getBollingerUpper() == null || ta.getBollingerLower() == null) return 0.04;
        return (ta.getBollingerUpper() - ta.getBollingerLower()) / ta.getBollingerMiddle();
    }

    private double distance(double price, double reference) {
        return (reference <= 0 || price <= 0) ? 0.0 : (price / reference) - 1.0;
    }

    private double smaAlignment(double price, TechnicalIndicators ta) {
        if (ta == null) return 0.0;
        Double s20 = ta.getSma20(), s50 = ta.getSma50(), s200 = ta.getSma200();
        if (s20 == null || s50 == null || s200 == null) return 0.0;
        return (price > s20 && s20 > s50 && s50 > s200) ? 1.0 : -1.0;
    }

    private double bullishPct(SentimentData s) {
        if (s == null || s.getNewsArticles24h() == null || s.getNewsArticles24h() == 0) return 50.0;
        int pos = s.getPositiveNewsCount() != null ? s.getPositiveNewsCount() : 0;
        return (double) pos / s.getNewsArticles24h() * 100.0;
    }

    private double bearishPct(SentimentData s) {
        if (s == null || s.getNewsArticles24h() == null || s.getNewsArticles24h() == 0) return 25.0;
        int neg = s.getNegativeNewsCount() != null ? s.getNegativeNewsCount() : 0;
        return (double) neg / s.getNewsArticles24h() * 100.0;
    }

    private double sentimentMomentum(SentimentData s) {
        if (s == null || s.getMentionTrend() == null) return 0.0;
        return switch (s.getMentionTrend().toUpperCase()) {
            case "RISING", "INCREASING" -> 1.0;
            case "FALLING", "DECREASING" -> -1.0;
            default -> 0.0;
        };
    }

    private double fiiFlowNorm(MacroData m) {
        if (m == null || m.getFiiNetFlowCrores() == null) return 0.0;
        double flow = m.getFiiNetFlowCrores().doubleValue();
        return Math.max(-1.0, Math.min(1.0, flow / 10_000.0));
    }

    private double marketRegime(MacroData m) {
        double vix = d(m != null ? m.getVixLevel() : null, 18.0);
        if (vix < 15) return 1.0;
        if (vix > 25) return 0.0;
        return 0.5;
    }

    private double marketCapClass(StockQuote q) {
        if (q == null || q.getMarketCap() == null) return 2.0;
        double mc = q.getMarketCap().doubleValue();
        if (mc < 300_000_000)       return 0.0;
        if (mc < 2_000_000_000)     return 1.0;
        if (mc < 10_000_000_000.0)  return 2.0;
        if (mc < 200_000_000_000.0) return 3.0;
        return 4.0;
    }

    private double d(Double v, double fallback) { return v != null ? v : fallback; }
    private double bd(BigDecimal v, double fallback) { return v != null ? v.doubleValue() : fallback; }
}
