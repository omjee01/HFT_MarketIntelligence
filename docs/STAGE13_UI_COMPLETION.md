# STAGE 13 — UI Completion & Virtual Portfolio

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Companion doc:** `docs/HFT_ARCHITECTURE.md` §30 — the five design decisions this stage had
> to make (why tabs are US/India/IPOs not exchange-exact, what "success rate" and "probable
> loss" actually map to, why the broker buttons are hand-off-only, the IPO pre-listing
> monitoring gap, and the live-found modal CSS bug).

---

## 1. STAGE OVERVIEW

Five gaps in the Stage 8 UI, addressed together since they're one user journey (see a
recommendation → understand it → buy it elsewhere → track it → get told when to act again):

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 13 — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  Dashboard        flat top-10 US list ──── 3 tabs (US/India/IPO), cap-tier/category   ║
║                                              grouped, sorted by confidence within tier  ║
║  Detail            none — list only ──────── click any card → full analysis modal      ║
║  Buy/Sell          no path at all ──────────── Zerodha Kite / INDmoney hand-off link   ║
║  Purchase tracking  entity existed, unwired ── real CRUD, owned per-user               ║
║  Notifications      none ────────────────────── scheduled monitor + alert banners      ║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

---

## 2. WHAT ACTUALLY CHANGED

```
Backend:
src/main/java/com/hft/model/enums/MarketCapTier.java         (new)
src/main/java/com/hft/model/domain/TradeRecommendation.java  (+marketCapTier)
src/main/java/com/hft/model/domain/PortfolioPosition.java    (+username)
src/main/java/com/hft/model/domain/PortfolioAlert.java       (new)
src/main/java/com/hft/model/dto/OpenPositionRequest.java     (new)
src/main/java/com/hft/model/dto/ClosePositionRequest.java    (new)
src/main/java/com/hft/repository/PortfolioPositionRepository.java  (+username-scoped queries)
src/main/java/com/hft/repository/PortfolioAlertRepository.java     (new)
src/main/java/com/hft/service/portfolio/PortfolioService.java      (new)
src/main/java/com/hft/service/portfolio/PortfolioMonitorService.java (new)
src/main/java/com/hft/controller/PortfolioController.java     (new)
src/main/java/com/hft/controller/RecommendationController.java (+GET /board)
src/main/java/com/hft/service/signal/RecommendationEngine.java (+generateBoard())
src/main/java/com/hft/ml/MLFeatureExtractor.java               (marketCapClass() delegates to MarketCapTier)
src/main/resources/application.yml                             (+hft.portfolio.*)
src/test/java/com/hft/model/enums/MarketCapTierTest.java       (new — 3 tests)

Frontend:
src/main/resources/static/js/modal.js           (new — shared open/close modal utility)
src/main/resources/static/js/broker-links.js    (new — Zerodha/INDmoney hand-off links)
src/main/resources/static/js/views/detail.js    (new — stock + IPO detail rendering, "I bought this" form)
src/main/resources/static/js/views/portfolio.js (new — position list, alerts, close/remove)
src/main/resources/static/js/views/dashboard.js (rewritten — tabs, cap-tier board, IPO board)
src/main/resources/static/js/app.js             (+portfolio route)
src/main/resources/static/index.html            (+Portfolio nav link)
src/main/resources/static/css/layout.css        (tabs, modal, cards, score bars, alert banners)
```

No changes to `ModelABRouter`, existing controllers' auth rules (`/api/v1/portfolio/**` was
already reserved `.authenticated()` before this stage — just never had a controller behind it),
or any Stage 1-12 backend logic beyond what's listed.

---

## 3. CONFIGURATION REFERENCE

```yaml
hft:
  portfolio:
    monitor-enabled: true
    monitor-poll-ms: 900000   # 15 min — real cost tradeoff, see §30.6
```

---

## 4. HOW TO VERIFY

```bash
gradle compileJava test    # 52/52 — 49 baseline + 3 new MarketCapTierTest

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```

Log in, open Dashboard → IPOs tab (works with zero setup — real seeded sample data, no external
API dependency) → click any card → detail modal → "I already got an allotment / bought this" →
fill quantity/price → Save. Check Portfolio tab: position appears under Open. To see an alert
fire without waiting 15 minutes, override the poll interval for one boot:
```bash
HFT_PORTFOLIO_MONITOR_POLL_MS=15000 SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
```
and set a position's `stop_loss_price` above its `current_price` directly in the DB (simulates
a breach) — watch for `[PortfolioMonitor] STOP_LOSS_HIT alert for ...` in the log, then reload
the Portfolio tab.

US/India tabs need a real quote to show anything — both were empty during this stage's own
verification, for reasons already documented and unrelated to this stage (Alpha Vantage's
account-level daily quota exhausted from repeated testing earlier the same session; NSE
returning a bot-detection 403 — see §26.6/§29). Confirmed via curl that the empty case itself
renders correctly (`{"success":true,"data":{}}` → the dashboard shows a clean "no
recommendations" message, not a broken layout) — full interactive verification (cards, modal,
buy link, "I bought this," Portfolio, alerts, dismiss, close-position with correct P&L math)
was done against the IPO tab instead, exercising the exact same shared rendering code.

---

## 5. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
IPO pre-listing positions aren't monitored — no live quote route exists for a symbol before
    it's actually trading. See §30.6. Not solved; needs IPO-lifecycle-aware monitoring.
Exchange-level tabs (NYSE vs NASDAQ vs AMEX) not built — the backend watchlists don't actually
    partition that finely. Tabs are US / India / IPOs, the real partitions. See §30.2.
No symbol-embedding broker deep links — intentional (§30.1), not a gap to "improve" without
    first confirming Zerodha/INDmoney actually publish a stable pre-fill URL scheme.
Signal-deterioration monitoring cost scales with open-position count (§30.6) — fine today,
    would need staggering/batching for a large portfolio.
"Model confidence" and "probable loss" are explicitly NOT a backtested win rate or a loss
    probability model (§30.4) — the UI labels them for what they are. Don't quietly upgrade
    the labels to imply more rigor than what's actually computed without building that rigor.
```

---

## 6. WHAT'S NEXT

- No further planned stage from the current backlog.
- Candidate follow-ups, not scheduled: IPO-lifecycle-aware position monitoring; a real
  historical per-symbol win-rate (once enough `recordSignalOutcome` volume exists to compute
  one honestly); exchange-level dashboard tabs if the watchlist model is ever restructured to
  actually distinguish NYSE/NASDAQ/AMEX; GraphQL subscription-based alerts instead of
  poll-on-page-load, if real-time push becomes worth the added frontend complexity.

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
