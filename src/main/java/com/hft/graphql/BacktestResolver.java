package com.hft.graphql;

import com.hft.backtest.*;
import com.hft.model.enums.Market;
import com.hft.streams.StreamSinkBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GraphQL resolver for Stage 6 — Backtesting & Strategy Validation.
 *
 * Mutations:
 *   runBacktest(input: BacktestInput!): BacktestRun!
 *       Starts an async backtest. Returns immediately with status=RUNNING.
 *       Subscribe to backtestProgress(runId) for live updates.
 *
 * Queries:
 *   backtestRun(runId: String!): BacktestRun
 *   listBacktestRuns(market: Market): [BacktestRun!]!
 *   walkForwardValidation(input: BacktestInput!, windows: Int): [WalkForwardResult!]!
 *
 * Subscriptions:
 *   backtestProgress(runId: String!): BacktestRun!
 *       Pushes BacktestRun updates as each symbol is processed.
 *       Final update has status=COMPLETE with metrics populated.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class BacktestResolver {

    private final BacktestRunner        runner;
    private final WalkForwardValidator  wfValidator;
    private final BacktestRunRepository runRepo;
    private final StreamSinkBridge      sinkBridge;

    @MutationMapping
    public BacktestRun runBacktest(@Argument Map<String, Object> input) {
        BacktestConfig config = parseInput(input);
        log.info("[BacktestResolver] Starting backtest: {} symbols on {} [{} → {}] model={}",
                 config.symbols().size(), config.market(), config.fromDate(), config.toDate(), config.modelVariant());
        runner.runAsync(config);
        // Return a PENDING stub immediately — subscription delivers real progress
        return BacktestRun.builder()
                .symbols(config.symbols())
                .market(config.market())
                .fromDate(config.fromDate())
                .toDate(config.toDate())
                .modelVariant(config.modelVariant())
                .initialCapital(config.initialCapital())
                .status("PENDING")
                .progressPercent(0)
                .build();
    }

    @QueryMapping
    public Optional<BacktestRun> backtestRun(@Argument String runId) {
        return runRepo.findById(runId);
    }

    @QueryMapping
    public List<BacktestRun> listBacktestRuns(@Argument Market market) {
        return market != null
                ? runRepo.findByMarketOrderByStartedAtDesc(market)
                : runRepo.findTop20ByOrderByStartedAtDesc();
    }

    @QueryMapping
    public List<WalkForwardResult> walkForwardValidation(@Argument Map<String, Object> input,
                                                          @Argument Integer windows) {
        BacktestConfig cfg = parseInput(input);
        BacktestConfig wfCfg = new BacktestConfig(
                cfg.symbols(), cfg.market(), cfg.fromDate(), cfg.toDate(),
                cfg.modelVariant(), cfg.initialCapital(), cfg.minConfidence(),
                cfg.maxHoldingDays(), true, windows != null ? windows : 5);
        return wfValidator.validate(wfCfg);
    }

    @SubscriptionMapping
    public Flux<BacktestRun> backtestProgress(@Argument String runId) {
        log.debug("[BacktestResolver] Client subscribed to backtestProgress({})", runId);
        return sinkBridge.backtestFlux(runId)
                .takeUntil(r -> "COMPLETE".equals(r.getStatus()) || "FAILED".equals(r.getStatus()));
    }

    // ─── Input parsing ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private BacktestConfig parseInput(Map<String, Object> input) {
        List<String> symbols = (List<String>) input.get("symbols");
        Market market = Market.valueOf((String) input.get("market"));
        LocalDate from = LocalDate.parse((String) input.get("fromDate"));
        LocalDate to   = LocalDate.parse((String) input.get("toDate"));
        String model   = input.getOrDefault("modelVariant", "A").toString();
        BigDecimal cap = input.containsKey("initialCapital")
                ? new BigDecimal(input.get("initialCapital").toString())
                : BigDecimal.valueOf(100_000);
        double minConf     = input.containsKey("minConfidence")
                ? Double.parseDouble(input.get("minConfidence").toString()) : 60.0;
        int maxHolding     = input.containsKey("maxHoldingDays")
                ? Integer.parseInt(input.get("maxHoldingDays").toString()) : 45;
        boolean wf         = Boolean.parseBoolean(input.getOrDefault("walkForward", "false").toString());
        int wfWindows      = input.containsKey("walkForwardWindows")
                ? Integer.parseInt(input.get("walkForwardWindows").toString()) : 5;

        return new BacktestConfig(symbols, market, from, to, model, cap, minConf, maxHolding, wf, wfWindows);
    }
}