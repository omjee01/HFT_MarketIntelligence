package com.hft.backtest;

import com.hft.ml.EnsembleModel;
import com.hft.ml.MLFeatureVector;
import com.hft.model.domain.OHLCVData;
import com.hft.model.domain.TechnicalIndicators;
import com.hft.model.enums.Market;
import com.hft.model.enums.SignalType;
import com.hft.repository.OHLCVDataRepository;
import com.hft.service.ml.MLPredictionService;
import com.hft.streams.StreamSinkBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates a full backtesting pass over historical OHLCV data.
 *
 * Algorithm per symbol:
 *   For each bar i in [fromDate, toDate]:
 *     1. Compute lightweight TechnicalIndicators from bars[0..i]
 *     2. Call MLPredictionService (or EnsembleModel) to score
 *     3. On BUY/STRONG_BUY with confidence ≥ minConfidence:
 *        a. Enter at bars[i+1].open (next bar's open)
 *        b. Scan bars until targetPrice or stopLossPrice is hit
 *        c. Fallback: close at bars[i+maxHolding].close (TIME_EXPIRY)
 *     4. Record BacktestTrade
 *   After all bars: compute BacktestMetrics via StrategyMetricsEngine
 *
 * No external API calls are made during simulation — all data comes from OHLCVData table.
 * A minimum of 20 bars of warmup history is required before signals are generated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestRunner {

    private static final int WARMUP_BARS = 20;

    private final OHLCVDataRepository    ohlcvRepo;
    private final MLPredictionService    mlService;
    private final StrategyMetricsEngine  metricsEngine;
    private final StreamSinkBridge       sinkBridge;
    private final BacktestRunRepository  runRepo;
    private final BacktestTradeRepository tradeRepo;

    @Autowired(required = false)
    private EnsembleModel ensembleModel;

    @Value("${hft.backtest.interval-type:1D}")
    private String intervalType;

    // ─── Async entry point (called by GraphQL mutation) ───────────────────────

    @Async("analysisExecutor")
    public CompletableFuture<BacktestRun> runAsync(BacktestConfig config) {
        return CompletableFuture.completedFuture(runSync(config, UUID.randomUUID().toString()));
    }

    // ─── Synchronous entry point (used by WalkForwardValidator) ──────────────

    @Transactional
    public BacktestRun runSync(BacktestConfig config, String runId) {
        BacktestRun run = BacktestRun.builder()
                .id(runId)
                .symbols(config.symbols())
                .market(config.market())
                .fromDate(config.fromDate())
                .toDate(config.toDate())
                .modelVariant(config.modelVariant())
                .initialCapital(config.initialCapital())
                .walkForward(config.walkForward())
                .walkForwardWindows(config.walkForwardWindows())
                .status("RUNNING")
                .progressPercent(0)
                .totalBarsProcessed(0)
                .totalTradesSimulated(0)
                .startedAt(LocalDateTime.now())
                .build();

        run = runRepo.save(run);
        sinkBridge.emitBacktestProgress(run);

        try {
            List<BacktestTrade> allTrades = new ArrayList<>();
            int symbolCount = config.symbols().size();

            for (int s = 0; s < symbolCount; s++) {
                String symbol = config.symbols().get(s);
                List<BacktestTrade> symbolTrades = simulateSymbol(symbol, config, run);
                allTrades.addAll(symbolTrades);

                run.setProgressPercent((s + 1) * 100 / symbolCount);
                run.setTotalBarsProcessed((run.getTotalBarsProcessed() != null ? run.getTotalBarsProcessed() : 0)
                        + symbolTrades.size());
                run = runRepo.save(run);
                sinkBridge.emitBacktestProgress(run);
            }

            BacktestMetrics metrics = metricsEngine.compute(
                    allTrades, config.initialCapital(), config.fromDate(), config.toDate());

            run.setMetrics(metrics);
            run.setTotalTradesSimulated(allTrades.size());
            run.setStatus("COMPLETE");
            run.setProgressPercent(100);
            run.setCompletedAt(LocalDateTime.now());
            run = runRepo.save(run);

            log.info("[Backtest] Run {} complete: {} trades | winRate={:.1f}% | sharpe={:.2f} | maxDD={:.1f}%",
                     runId, allTrades.size(),
                     metrics.getWinRatePct(), metrics.getSharpeRatio(), metrics.getMaxDrawdownPct());

        } catch (Exception e) {
            log.error("[Backtest] Run {} failed: {}", runId, e.getMessage(), e);
            run.setStatus("FAILED");
            run.setFailureReason(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            run = runRepo.save(run);
        }

        sinkBridge.emitBacktestProgress(run);
        return run;
    }

    // ─── Per-symbol simulation ────────────────────────────────────────────────

    private List<BacktestTrade> simulateSymbol(String symbol, BacktestConfig config, BacktestRun run) {
        List<OHLCVData> bars = ohlcvRepo.findBySymbolAndMarketAndIntervalTypeAndBarDateBetweenOrderByBarDate(
                symbol, config.market(), intervalType,
                config.fromDate().minusDays(200),  // extra warmup history before fromDate
                config.toDate());

        if (bars.size() < WARMUP_BARS + 1) {
            log.debug("[Backtest] {} has only {} bars — skipping (need {}+)", symbol, bars.size(), WARMUP_BARS);
            return List.of();
        }

        List<BacktestTrade> trades = new ArrayList<>();
        boolean inTrade = false;

        for (int i = WARMUP_BARS; i < bars.size() - 1; i++) {
            if (inTrade) continue;

            OHLCVData bar      = bars.get(i);
            if (bar.getBarDate().isBefore(config.fromDate())) continue;  // skip warmup bars

            List<OHLCVData> history = bars.subList(0, i + 1);
            TechnicalIndicators ta  = computeTA(history);
            BigDecimal price        = bar.getClose();

            MLPredictionService.MLPrediction prediction = score(
                    symbol, config.market(), price, ta, history, config.modelVariant());

            SignalType signal = SignalType.fromScore(prediction.getCompositeScore());

            if (!signal.isBullish() || prediction.getConfidencePercent() < config.minConfidence()) {
                continue;
            }

            // Entry at next bar's open
            OHLCVData entryBar = bars.get(i + 1);
            BigDecimal entryPrice = entryBar.getOpen();
            BigDecimal targetPrice = prediction.getPredictedTargetPrice();
            BigDecimal stopLoss    = prediction.getStopLossPrice();

            // Scan for exit
            String exitReason = "TIME_EXPIRY";
            BigDecimal exitPrice = null;
            int exitBarIdx = Math.min(i + 1 + config.maxHoldingDays(), bars.size() - 1);

            for (int j = i + 1; j <= exitBarIdx; j++) {
                OHLCVData scanBar = bars.get(j);

                if (targetPrice != null && scanBar.getHigh().compareTo(targetPrice) >= 0) {
                    exitPrice  = targetPrice;
                    exitReason = "TARGET_HIT";
                    exitBarIdx = j;
                    break;
                }
                if (stopLoss != null && scanBar.getLow().compareTo(stopLoss) <= 0) {
                    exitPrice  = stopLoss;
                    exitReason = "STOP_HIT";
                    exitBarIdx = j;
                    break;
                }
            }

            if (exitPrice == null) {
                exitPrice = bars.get(exitBarIdx).getClose();
            }

            double returnPct = (exitPrice.doubleValue() - entryPrice.doubleValue())
                    / entryPrice.doubleValue() * 100.0;
            int holdingDays  = exitBarIdx - (i + 1);

            BacktestTrade trade = BacktestTrade.builder()
                    .run(run)
                    .symbol(symbol)
                    .signal(signal)
                    .entryDate(entryBar.getBarDate())
                    .entryPrice(entryPrice)
                    .exitDate(bars.get(exitBarIdx).getBarDate())
                    .exitPrice(exitPrice)
                    .exitReason(exitReason)
                    .returnPercent(Math.round(returnPct * 100.0) / 100.0)
                    .profitable(returnPct > 0)
                    .holdingDays(holdingDays)
                    .compositeScore(prediction.getCompositeScore())
                    .confidencePercent(prediction.getConfidencePercent())
                    .targetPrice(targetPrice)
                    .stopLossPrice(stopLoss)
                    .build();

            tradeRepo.save(trade);
            trades.add(trade);
            inTrade = false;  // one-trade-at-a-time per symbol per bar window
            i = exitBarIdx;   // advance to after the closed trade
        }

        log.debug("[Backtest] {} — {} trades simulated from {} bars", symbol, trades.size(), bars.size());
        return trades;
    }

    // ─── Lightweight TA from raw OHLCV (no external service calls) ───────────

    private TechnicalIndicators computeTA(List<OHLCVData> bars) {
        int n = bars.size();
        if (n < 2) return TechnicalIndicators.builder().technicalScore(50.0).build();

        double[] closes = bars.stream().mapToDouble(b -> b.getClose().doubleValue()).toArray();

        double sma20   = n >= 20 ? mean(closes, n - 20, n) : mean(closes, 0, n);
        double sma50   = n >= 50 ? mean(closes, n - 50, n) : sma20;
        double sma200  = n >= 200 ? mean(closes, n - 200, n) : sma50;
        double ema9    = ema(closes, 9);
        double rsi14   = rsi(closes, 14);
        double atr14   = atr(bars, 14);
        double bbUpper = sma20 + 2 * stdDev(closes, Math.max(0, n - 20), n);
        double bbLower = sma20 - 2 * stdDev(closes, Math.max(0, n - 20), n);

        double current = closes[n - 1];
        boolean aboveSmas = current > ema9 && current > sma20 && current > sma50 && current > sma200;

        double techScore = 50.0;
        if (rsi14 < 30)  techScore += 15;
        else if (rsi14 > 70) techScore -= 10;
        if (aboveSmas)   techScore += 20;
        if (current > sma200) techScore += 10;
        techScore = Math.min(100, Math.max(0, techScore));

        return TechnicalIndicators.builder()
                .rsi14(rsi14).sma20(sma20).sma50(sma50).sma200(sma200).ema9(ema9)
                .bollingerUpper(bbUpper).bollingerMiddle(sma20).bollingerLower(bbLower)
                .atr14(atr14)
                .obvTrendingUp(bars.get(n - 1).isBullish())
                .technicalScore(techScore)
                .build();
    }

    private double mean(double[] arr, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += arr[i];
        return (to - from) > 0 ? sum / (to - from) : 0;
    }

    private double stdDev(double[] arr, int from, int to) {
        double m = mean(arr, from, to);
        double v = 0;
        for (int i = from; i < to; i++) v += Math.pow(arr[i] - m, 2);
        return (to - from) > 1 ? Math.sqrt(v / (to - from - 1)) : 0;
    }

    private double ema(double[] closes, int period) {
        if (closes.length < period) return closes[closes.length - 1];
        double k = 2.0 / (period + 1);
        double ema = closes[closes.length - period];
        for (int i = closes.length - period + 1; i < closes.length; i++) {
            ema = closes[i] * k + ema * (1 - k);
        }
        return ema;
    }

    private double rsi(double[] closes, int period) {
        if (closes.length <= period) return 50.0;
        double avgGain = 0, avgLoss = 0;
        int start = closes.length - period - 1;
        for (int i = start; i < start + period; i++) {
            double change = closes[i + 1] - closes[i];
            if (change > 0) avgGain += change; else avgLoss -= change;
        }
        avgGain /= period;
        avgLoss /= period;
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1 + rs));
    }

    private double atr(List<OHLCVData> bars, int period) {
        int n = bars.size();
        if (n < 2) return 0;
        double atr = 0;
        int start = Math.max(1, n - period);
        for (int i = start; i < n; i++) {
            OHLCVData b = bars.get(i);
            OHLCVData prev = bars.get(i - 1);
            double tr = Math.max(b.getHigh().doubleValue() - b.getLow().doubleValue(),
                        Math.max(Math.abs(b.getHigh().doubleValue() - prev.getClose().doubleValue()),
                                 Math.abs(b.getLow().doubleValue() - prev.getClose().doubleValue())));
            atr += tr;
        }
        return atr / Math.max(1, n - start);
    }

    // ─── Scoring dispatch ─────────────────────────────────────────────────────

    private MLPredictionService.MLPrediction score(String symbol, Market market,
                                                    BigDecimal price, TechnicalIndicators ta,
                                                    List<OHLCVData> history, String modelVariant) {
        if ("B".equalsIgnoreCase(modelVariant) && ensembleModel != null) {
            // Build proxy feature vector from TA for Model B
            double ts = ta.getTechnicalScore() != null ? ta.getTechnicalScore() : 50.0;
            MLFeatureVector fv = MLFeatureVector.builder()
                    .symbol(symbol).market(market.name())
                    .rsi14(ta.getRsi14() != null ? ta.getRsi14() : 50.0)
                    .macdLine(0.0).macdHistogram(0.0)
                    .bbPosition(0.5).bbWidth(0.04)
                    .sma20Distance(ta.getSma20() != null ? price.doubleValue() / ta.getSma20() - 1 : 0)
                    .sma50Distance(ta.getSma50() != null ? price.doubleValue() / ta.getSma50() - 1 : 0)
                    .sma200Distance(ta.getSma200() != null ? price.doubleValue() / ta.getSma200() - 1 : 0)
                    .ema9Distance(ta.getEma9() != null ? price.doubleValue() / ta.getEma9() - 1 : 0)
                    .atrNormalized(ta.getAtr14() != null ? ta.getAtr14() / price.doubleValue() : 0.02)
                    .volumeRatio(1.0).obvTrend(Boolean.TRUE.equals(ta.getObvTrendingUp()) ? 1.0 : -1.0)
                    .technicalScore(ts).smaAlignment(ts > 60 ? 1.0 : -1.0)
                    .fundamentalScore(50.0).peRatioNorm(25.0).pbRatio(2.0).roe(10.0)
                    .debtToEquity(1.0).revenueGrowthYoY(5.0).epsGrowthYoY(5.0).dividendYield(1.5)
                    .sentimentRaw(0.0).normalizedSentiment(50.0).bullishPercent(50.0)
                    .bearishPercent(25.0).newsCountLog(2.0).mentionsLog(4.0).sentimentMomentum(0.0)
                    .macroScore(50.0).gdpGrowthRate(2.5).inflationRate(3.0).centralBankRate(5.0)
                    .vixLevel(18.0).fiiFlowNorm(0.0).marketRegime(0.5)
                    .percentFrom52High(-0.05).percentFrom52Low(0.1)
                    .dayChangePct(0.0).volumeSpike(0.0).marketCapClass(2.0)
                    .build();

            double ensScore = ensembleModel.computeScore(fv);
            double ensConf  = ensembleModel.computeConfidence(fv, ensScore);
            // Still delegate price target / stop to Model A
            MLPredictionService.MLPrediction base = mlService.predict(
                    symbol, market, price, ta, null, null, null, history);
            return MLPredictionService.MLPrediction.builder()
                    .symbol(symbol).market(market).compositeScore(ensScore)
                    .confidencePercent(ensConf)
                    .predictedTargetPrice(base.getPredictedTargetPrice())
                    .stopLossPrice(base.getStopLossPrice())
                    .expectedProfitPercent(base.getExpectedProfitPercent())
                    .maxRiskPercent(base.getMaxRiskPercent())
                    .riskRewardRatio(base.getRiskRewardRatio())
                    .currentPrice(price)
                    .entryDate(base.getEntryDate()).exitDate(base.getExitDate())
                    .holdingPeriodDays(base.getHoldingPeriodDays())
                    .build();
        }
        return mlService.predict(symbol, market, price, ta, null, null, null, history);
    }
}