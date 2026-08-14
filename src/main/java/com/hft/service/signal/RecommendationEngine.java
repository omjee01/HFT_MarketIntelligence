package com.hft.service.signal;

import com.hft.model.domain.*;
import com.hft.model.enums.*;
import com.hft.service.analysis.*;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.service.ml.MLPredictionService;
import com.hft.service.risk.RiskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The core brain of the HFT platform — the Recommendation Engine.
 *
 * Orchestrates ALL analysis services and produces the final
 * TradeRecommendation with full data backing.
 *
 * Flow for each symbol:
 *  1. Fetch latest quote (MarketDataAggregatorService)
 *  2. Technical Analysis (TechnicalAnalysisService)
 *  3. Sentiment Analysis (SentimentAnalysisService)
 *  4. Fundamental Analysis (FundamentalAnalysisService)
 *  5. Macro/Geopolitical Analysis (MacroGeopoliticalService)
 *  6. ML Prediction (MLPredictionService)
 *  7. Risk filters (RiskManagementService)
 *  8. Assemble TradeRecommendation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationEngine {

    private final MarketDataAggregatorService marketData;
    private final TechnicalAnalysisService    taService;
    private final SentimentAnalysisService    sentimentService;
    private final FundamentalAnalysisService  fundamentalService;
    private final MacroGeopoliticalService    macroService;
    private final MLPredictionService         mlService;
    private final RiskManagementService       riskService;

    @Value("${hft.recommendation.min-confidence-for-buy:65.0}")
    private double minConfidenceForBuy;

    @Value("${hft.recommendation.min-risk-reward-ratio:2.0}")
    private double minRiskRewardRatio;

    @Value("${hft.recommendation.min-liquidity-avg-volume:100000}")
    private long minAvgVolume;

    /**
     * Generate a full recommendation for a single symbol.
     * Returns empty if signal is HOLD or filters eliminate it.
     */
    @Async("analysisExecutor")
    public Optional<TradeRecommendation> generateRecommendation(String symbol, Market market) {
        log.debug("[Engine] Processing: {} on {}", symbol, market);

        try {
            // ─── Step 1: Get Quote ────────────────────────────────────────────
            Optional<StockQuote> quoteOpt = marketData.getQuote(symbol, market);
            if (quoteOpt.isEmpty()) {
                log.debug("[Engine] No quote for {}", symbol);
                return Optional.empty();
            }
            StockQuote quote = quoteOpt.get();
            BigDecimal currentPrice = quote.getCurrentPrice();

            // ─── Step 2: Technical Analysis ───────────────────────────────────
            Optional<TechnicalIndicators> taOpt = taService.analyze(symbol, market);
            TechnicalIndicators ta = taOpt.orElse(null);

            // ─── Step 3: Sentiment ────────────────────────────────────────────
            SentimentData sentiment = sentimentService.analyzeSentiment(symbol, market);

            // ─── Step 4: Fundamental ──────────────────────────────────────────
            Optional<FundamentalData> fdOpt = fundamentalService.analyze(symbol, market);
            FundamentalData fd = fdOpt.orElse(null);

            // ─── Step 5: Macro ────────────────────────────────────────────────
            MacroData macro = macroService.getMacroData(market);

            // ─── Step 6: ML Prediction ────────────────────────────────────────
            List<OHLCVData> recentBars = marketData.getRecentHistory(symbol, market, 60);
            MLPredictionService.MLPrediction prediction = mlService.predict(
                    symbol, market, currentPrice, ta, fd, sentiment, macro, recentBars);

            // ─── Step 7: Liquidity Filter ─────────────────────────────────────
            if (quote.getAvgVolume20Day() != null && quote.getAvgVolume20Day() < minAvgVolume) {
                log.debug("[Engine] {} filtered out: insufficient liquidity ({})", symbol, quote.getAvgVolume20Day());
                return Optional.empty();
            }

            // ─── Step 8: Risk Filter ──────────────────────────────────────────
            if (prediction.getRiskRewardRatio() < minRiskRewardRatio) {
                log.debug("[Engine] {} filtered out: R:R ratio {:.2f} < minimum {:.2f}",
                          symbol, prediction.getRiskRewardRatio(), minRiskRewardRatio);
                return Optional.empty();
            }

            // ─── Step 9: Determine Signal ─────────────────────────────────────
            SignalType signal = SignalType.fromScore(prediction.getCompositeScore());

            // BUY signals need minimum confidence
            if (signal.isBullish() && prediction.getConfidencePercent() < minConfidenceForBuy) {
                signal = SignalType.WATCH;
            }

            // ─── Step 10: Build Recommendation ───────────────────────────────
            TradeRecommendation reco = buildRecommendation(
                    symbol, quote, ta, fd, sentiment, macro, prediction, signal, recentBars);

            log.info("[Engine] ✅ {} → {} | Score:{:.0f} | Profit:{:.1f}% | Conf:{:.0f}%",
                     symbol, signal, prediction.getCompositeScore(),
                     prediction.getExpectedProfitPercent(), prediction.getConfidencePercent());

            return Optional.of(reco);

        } catch (Exception e) {
            log.error("[Engine] Failed to process {}: {}", symbol, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Generate recommendations for a full watchlist (US or India).
     * Returns top N sorted by composite score.
     */
    public List<TradeRecommendation> generateTopRecommendations(Market market, int topN) {
        List<String> watchlist = market.isUS()
                ? marketData.getUSWatchlist()
                : marketData.getIndiaWatchlist();

        log.info("[Engine] Generating recommendations for {} symbols on {}", watchlist.size(), market);

        List<TradeRecommendation> all = watchlist.parallelStream()
                .map(symbol -> generateRecommendation(symbol, market))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> r.getSignal().isBullish() || r.getSignal() == SignalType.WATCH)
                .sorted(Comparator.comparingDouble(TradeRecommendation::getCompositeScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());

        // Assign ranks
        for (int i = 0; i < all.size(); i++) {
            all.get(i).setRank("TOP_" + (i + 1));
        }

        log.info("[Engine] Generated {} recommendations for {}", all.size(), market);
        return all;
    }

    // ─── Recommendation Builder ───────────────────────────────────────────────

    private TradeRecommendation buildRecommendation(
            String symbol, StockQuote quote,
            TechnicalIndicators ta, FundamentalData fd,
            SentimentData sentiment, MacroData macro,
            MLPredictionService.MLPrediction pred,
            SignalType signal,
            List<OHLCVData> bars) {

        String sector = quote.getSector() != null ? quote.getSector()
                      : fd != null ? fd.getSector() : "Unknown";

        List<String> reasons = buildReasons(ta, fd, sentiment, macro, pred, signal);
        List<String> risks    = buildRisks(ta, fd, sentiment, macro);
        List<String> sources  = buildDataSources(market(quote), ta, fd, sentiment);
        List<String> news     = sentiment != null ? sentiment.getKeyHeadlines()
                              : Collections.emptyList();

        // Entry price range (±0.5% around current)
        double price = quote.getCurrentPrice().doubleValue();
        String priceRange = market(quote).getCurrency().equals("INR")
                ? String.format("₹%.0f – ₹%.0f", price * 0.995, price * 1.005)
                : String.format("$%.2f – $%.2f", price * 0.995, price * 1.005);

        return TradeRecommendation.builder()
                .symbol(symbol)
                .companyName(quote.getCompanyName())
                .market(market(quote))
                .assetType(AssetType.STOCK)
                .sector(sector)
                .sectorOutlook(macro != null ? macro.getMacroSentiment() : "NEUTRAL")
                .signal(signal)
                .timeHorizon(TimeHorizon.fromDays(pred.getHoldingPeriodDays()))
                .riskLevel(RiskLevel.fromRiskRewardAndConfidence(
                        pred.getRiskRewardRatio(), pred.getConfidencePercent()))
                .currentPrice(quote.getCurrentPrice())
                .entryPrice(quote.getCurrentPrice())
                .entryPriceRange(priceRange)
                .targetPrice(pred.getPredictedTargetPrice())
                .stopLossPrice(pred.getStopLossPrice())
                .expectedProfitPercent(pred.getExpectedProfitPercent())
                .maxRiskPercent(pred.getMaxRiskPercent())
                .riskRewardRatio(pred.getRiskRewardRatio())
                .entryDate(pred.getEntryDate())
                .exitDate(pred.getExitDate())
                .holdingPeriodDays(pred.getHoldingPeriodDays())
                .compositeScore(pred.getCompositeScore())
                .confidencePercent(pred.getConfidencePercent())
                .technicalScore(pred.getTechnicalScore())
                .fundamentalScore(pred.getFundamentalScore())
                .sentimentScore(pred.getSentimentScore())
                .macroScore(pred.getMacroScore())
                .mlScore(pred.getMlModelScore())
                .keyReasons(reasons)
                .keyRisks(risks)
                .relatedNews(news)
                .dataSources(sources)
                .generatedAt(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusHours(4))
                .status("ACTIVE")
                .build();
    }

    // ─── Reasoning Builder ────────────────────────────────────────────────────

    private List<String> buildReasons(TechnicalIndicators ta, FundamentalData fd,
                                       SentimentData sentiment, MacroData macro,
                                       MLPredictionService.MLPrediction pred,
                                       SignalType signal) {
        List<String> reasons = new ArrayList<>();

        if (ta != null) {
            if (ta.isRsiOversold()) reasons.add(String.format(
                    "RSI(14) at %.1f — emerging from oversold zone (strong bounce candidate)", ta.getRsi14()));
            if (Boolean.TRUE.equals(ta.getMacdBullishCrossover())) reasons.add(
                    "MACD bullish crossover confirmed — momentum turning positive");
            if (ta.isGoldenCross()) reasons.add(
                    "Golden Cross: SMA50 above SMA200 — long-term bullish trend intact");
            if (ta.getDetectedPatterns() != null && ta.getDetectedPatterns().contains("HAMMER")) reasons.add(
                    "Hammer candlestick detected — potential reversal signal");
            if (ta.getDetectedPatterns() != null && ta.getDetectedPatterns().contains("BULLISH_ENGULFING")) reasons.add(
                    "Bullish Engulfing pattern — buyers overpowering sellers");
            if (Boolean.TRUE.equals(ta.getObvTrendingUp())) reasons.add(
                    "OBV rising — volume confirming bullish price action");
        }

        if (fd != null) {
            if (fd.hasSolidROE()) reasons.add(
                    String.format("Strong ROE of %.1f%% — above-average capital efficiency", fd.getRoe()));
            if (fd.isGrowthStock()) reasons.add(
                    String.format("Growth stock: EPS +%.1f%% YoY, Revenue +%.1f%% YoY",
                                   fd.getEpsGrowthYoY(), fd.getRevenueGrowthYoY()));
            if ("UNDERVALUED".equals(fd.getValuationStatus())) reasons.add(
                    "Valuation screen: UNDERVALUED vs sector peers (P/E below industry avg)");
        }

        if (sentiment != null && sentiment.isBullish()) reasons.add(
                String.format("Positive market sentiment: %.0f%% bullish news in last 24h",
                               (double) sentiment.getPositiveNewsCount()
                             / Math.max(1, sentiment.getNewsArticles24h()) * 100));

        if (macro != null && "POSITIVE".equals(macro.getMacroSentiment())) reasons.add(
                "Favorable macro environment: " + macro.getSectorTailwinds());

        if (reasons.isEmpty()) reasons.add(
                String.format("Composite score %.1f — combination of technical and fundamental signals", pred.getCompositeScore()));

        return reasons;
    }

    private List<String> buildRisks(TechnicalIndicators ta, FundamentalData fd,
                                     SentimentData sentiment, MacroData macro) {
        List<String> risks = new ArrayList<>();
        if (ta != null && ta.isRsiOverbought()) risks.add("RSI overbought — momentum may fade short-term");
        if (ta != null && ta.isDeathCross()) risks.add("Death Cross (SMA50 < SMA200) — underlying trend remains bearish");
        if (fd != null && fd.getDebtToEquity() != null && fd.getDebtToEquity() > 2.0) risks.add(
                String.format("High debt/equity ratio: %.1f — interest rate sensitivity", fd.getDebtToEquity()));
        if (sentiment != null && sentiment.getHasPoliticalRisk() != null && sentiment.getHasPoliticalRisk())
            risks.add("Political/geopolitical risk event detected in news");
        if (macro != null && macro.getVixLevel() != null && macro.getVixLevel() > 20)
            risks.add(String.format("VIX at %.1f — elevated market volatility", macro.getVixLevel()));
        if (macro != null && "SELLING".equals(macro.getFiiFlowTrend()))
            risks.add("FII net selling — foreign institutional outflow pressure");
        if (risks.isEmpty()) risks.add("Broad market correction could impact all positions");
        return risks;
    }

    private List<String> buildDataSources(Market market, TechnicalIndicators ta,
                                           FundamentalData fd, SentimentData sentiment) {
        List<String> sources = new ArrayList<>();
        sources.add(market.isUS() ? "Alpha Vantage (US Quotes)" : "NSE India (Live Data)");
        if (ta != null) sources.add("200-day OHLCV Technical Analysis");
        if (fd != null) sources.add(fd.getDataSource() != null ? fd.getDataSource() : "Fundamentals API");
        if (sentiment != null && sentiment.getNewsArticles24h() != null && sentiment.getNewsArticles24h() > 0)
            sources.add(String.format("NewsAPI (%d articles analyzed)", sentiment.getNewsArticles24h()));
        sources.add("FRED Macro Data (US) / RBI Data (India)");
        return sources;
    }

    private Market market(StockQuote quote) {
        return quote.getMarket() != null ? quote.getMarket() : Market.US_NYSE;
    }
}