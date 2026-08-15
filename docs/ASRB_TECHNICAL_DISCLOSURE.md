# Adaptive Source Reliability Bandit (ASRB)
## Technical Disclosure — Draft for Counsel / Research Review

> **Status:** Engineering design draft. Not filed, not reviewed by patent counsel, not a
> legal opinion on novelty or patentability. Prepared as structured technical input for
> whoever performs that review — background/prior-art, proposed method, and claimed
> differences are separated deliberately so a reviewer can assess novelty without having to
> reverse-engineer it from prose. Author has not run a formal prior-art search against patent
> databases; "novel" below means "not found in the literature surveyed for this draft," not
> "confirmed novel."
>
> **Project:** HFT Market Intelligence Platform (HMIP) | **Date:** 2026-08-16 | **Author:** PTD2315

---

## 1. Background / Problem Statement

HMIP scores tradeable instruments (stocks, IPOs) by fusing signal from multiple independent
information sources — news APIs, social platforms, regulatory filings, macro data feeds, and
optionally user-connected read-only social/news accounts (see `HFT_ARCHITECTURE.md` §24–25).

This fusion problem has three properties that make naive approaches (fixed weighted average,
or a standard independent-arm multi-armed bandit) fail in practice:

```
1. NON-STATIONARY RELIABILITY  — a source's trustworthiness drifts. A feed can silently
   degrade (a scraper target redesigns its page, an API starts truncating results), or a
   community's predictive value can shift (a subreddit's user base changes character).

2. CORRELATED SOURCES          — sources are not independent. A wire story and three outlets
   that reprint it are not four confirmations of one fact; they are one fact reported four
   times. Treating them as independent evidence inflates confidence without adding
   information.

3. ADVERSARIAL / LOW-QUALITY EVIDENCE — some evidence is actively wrong (rumor, deliberate
   misinformation, satire mistaken for news) and can go viral faster than it can be
   corroborated. Critically, a false narrative can still move price and cause real business
   harm even after being debunked (§6) — so the right response isn't "detect and delete,"
   it's "discount as a truth signal while still tracking it as a real risk."
```

Standard multi-armed bandit source-weighting (e.g. plain Thompson Sampling over sources)
handles none of these three by default: it assumes stationary, independent, honestly-reported
arms. ASRB is a proposed extension that addresses all three within one fusion pipeline while
staying computationally light enough to run inline in a trading-signal pipeline (no training
loop, no GPU, no replay buffer).

---

## 2. Prior Art / Related Techniques

The following are established, published techniques ASRB builds on. None of them are claimed
as novel here.

| Technique | What it provides | Known limitation for this problem |
|---|---|---|
| **Thompson Sampling** (Thompson, 1933; modern treatment: Russo et al., *A Tutorial on Thompson Sampling*, 2018) | Randomized posterior sampling for explore/exploit balance in multi-armed bandits | Standard form assumes independent, stationary arms; no native context-sensitivity |
| **Gittins Index** (Gittins, 1979) | Provably optimal arm-selection policy for the *classic* (stationary, context-free, geometrically-discounted, independent-arms) bandit | Optimality guarantee does not hold once arms are contextual or non-stationary; exact indices for those settings are generally intractable |
| **Neural-linear contextual bandits** (Riquelme et al., *Deep Bayesian Bandits Showdown*, 2018) | A learned feature representation feeding a closed-form Bayesian-linear posterior head — found to match or beat full Bayesian neural-network bandits at much lower cost | Doesn't itself address non-stationarity or inter-arm correlation |
| **Discounted / sliding-window bandits** (Garivier & Moulines, 2011; Raj & Kalyani, 2017) | Exponential forgetting or windowing of posterior statistics to track non-stationary reward distributions | Addresses drift, not correlation or evidence trustworthiness |
| **Rumor/misinformation detection literature** (general survey: Zubiaga et al., *Detection and Resolution of Rumours in Social Media*, 2018) | Establishes corroboration-count and abnormal-velocity as standard rumor-signature features | Typically framed as a standalone classification task, not as an inline evidence-weighting term inside a financial signal-fusion bandit |
| **Financial sentiment fusion** (industry practice; e.g. this codebase's pre-ASRB `SentimentAnalysisService`) | Combines multiple sentiment sources into one score | In practice, typically a fixed/hand-tuned weighted average with no reliability learning, no correlation-awareness, no misinformation-awareness — this is literally what HMIP had before this design (see `HFT_ARCHITECTURE.md` §24.1 audit) |

---

## 3. Why Combining These Directly Is Insufficient

Simply running Thompson Sampling per source, discounted for non-stationarity, with a
neural-linear context layer, does not solve problems 2 and 3 from §1 — none of the prior-art
techniques above model inter-arm correlation or evidence trustworthiness at all. A system that
stacked only these four techniques would still:

- Let three correlated outlets reporting one wire story count as three independent votes.
- Weight a viral, uncorroborated rumor the same as a slow-building, well-corroborated,
  credible-source narrative, as long as both scored the same raw sentiment.

This is the gap the two proposed mechanisms below close.

---

## 4. Proposed Method — ASRB

### 4.1 Notation

```
i = 1..N            index over active information sources
t                    current scoring pass (discrete time)
x(t)                 context vector (HMIP's existing 41-feature MLFeatureVector +
                     source/sector context)
s_i(t)               raw score reported by source i this pass, ∈ [0,100]
c_i(t)               claim-cluster id for source i's evidence this pass (topic+assertion,
                     grouped via embedding similarity — see §10 re: encoder choice)
Ω(t)                 N×N exponentially-weighted correlation matrix of standardized source
                     score residuals, decay λ_corr
(Λ_i, θ_i)           Bayesian-linear posterior sufficient statistics (precision matrix, mean)
                     for source i's reliability model over context x(t)
λ_time               temporal forgetting factor for non-stationarity (distinct from λ_corr)
```

### 4.2 Pipeline (per source, per scoring pass)

**Step 1 — Correlation discount.** For source i, against every source j already processed
this pass:

```
ρ_ij(t) = Ω_ij(t) / sqrt(Ω_ii(t) · Ω_jj(t))
effective_weight_i ← raw_weight_i × (1 − κ · max(0, max_j ρ_ij(t)))
```
κ ∈ (0,1] is a sensitivity constant. Discounting against the single most-correlated
already-counted source (rather than compounding across all of them) keeps the penalty
interpretable and avoids runaway compounding when many sources are mutually correlated.

**Step 2 — Misinformation-risk discount.** Reuses source i's own reliability posterior
(no separate classifier needed for the credibility term):

```
κ_corr(c_i(t)) = count of distinct sources j reporting claim-cluster c_i(t) this window
                 with ρ_ij(t) < ρ_threshold (i.e., genuinely independent corroboration,
                 not correlated re-reporting of the same wire story)
V(c_i(t))       = z-score of claim-cluster c_i(t)'s current mention velocity against its
                 own historical baseline (spike detector)
CR_i(t)         = posterior mean reliability of source i (from Step 3, reused directly)

MisinfoRisk_i(t) = σ( w1·(1 − CR_i(t)) + w2·max(0, V(c_i(t)) − v0) − w3·κ_corr(c_i(t)) )

effective_weight_i ← effective_weight_i × (1 − risk_aversion · MisinfoRisk_i(t))
```
σ is a logistic squash to [0,1]; w1..w3 and v0 are calibrated, not guessed (§10).

**Narrative-risk flag (dual use of the same computation):** if
`MisinfoRisk_i(t) > τ_risk AND V(c_i(t)) > τ_velocity`, emit a `RiskLevel` escalation on the
affected symbol — independent of, and in addition to, the discount applied above. The
discount protects the *prediction* from being fooled by an unverified viral claim; the flag
protects the *user* from a real risk that exists regardless of whether the claim is true
(§6 worked example).

**Step 3 — Posterior update** (discounted Bayesian linear regression, recursive form):

```
Λ_i(t)  ← λ_time · Λ_i(t−1) + effective_weight_i · x(t) x(t)ᵀ
θ_i(t)  ← Λ_i(t)⁻¹ [ λ_time · Λ_i(t−1) θ_i(t−1) + effective_weight_i · x(t) y_i(t) ]
```
`y_i(t)` is the realized-outcome label, sourced from the existing `recordSignalOutcome`
mutation or `BacktestTrade` result — this reuses Stage 5/6 infrastructure unchanged.

**Step 4 — Stability index** (population-relative, not a fixed constant):

```
drift_i(t)        = ‖θ_i(t) − θ_i(t−Δ)‖ / Δ
var_i(t)          = tr(Λ_i(t)⁻¹)
raw_stability_i(t) = 1 / (1 + drift_i(t) + var_i(t))
S_i(t)             = percentile_rank(raw_stability_i(t) among all active sources at time t)
```
Ranking by percentile rather than the raw value is what makes the exploit/explore switch
self-calibrating across regimes: in a broadly chaotic period every source's raw stability
drops together, but the *relative* ranking still separates the least-unreliable sources from
the worst ones, instead of a fixed threshold suddenly classifying every source as "unstable."

**Step 5 — Policy selection:**

```
if S_i(t) ≥ τ_stability:
    # source is well-characterized and relatively stationary — use the provably
    # optimal branch
    (α_i, β_i) ← moment-match (Λ_i, θ_i) to an equivalent Beta-Bernoulli pair
    policy_weight_i(t) ← Gittins_index(α_i, β_i)
else:
    # new, sparse, or actively drifting — Gittins optimality doesn't hold here;
    # fall back to sampling
    policy_weight_i(t) ← draw from N(θ_i(t), Λ_i(t)⁻¹)
```

**Step 6 — Aggregate:**

```
CompositeScore(symbol, t) = Σ_i [ policy_weight_i(t) × s_i(t) ] / Σ_i policy_weight_i(t)
```
Feeds `RecommendationEngine` / `EnsembleModel` / `IPOAnalysisService` with no interface
change — only better inputs than today's random/hardcoded values.

---

## 5. Claimed Novel Contributions

Explicitly separating what's asserted as new from the prior art in §2:

```
(A) POPULATION-RELATIVE STABILITY-GATED POLICY SELECTION (§4.2 Steps 4–5)
    The Gittins-index/Thompson-Sampling choice is not fixed and not gated by a fixed
    observation-count threshold; it's gated by a stability index computed from posterior
    drift-velocity and variance-shrinkage, evaluated RELATIVE TO THE CURRENT CROSS-SOURCE
    POPULATION rather than an absolute constant. This is the mechanism that makes the
    exploit/explore boundary self-calibrate across market regimes.

(B) CORRELATION-DISCOUNTED EVIDENCE WEIGHTING (§4.2 Step 1)
    Evidence weight is discounted proportionally to the source's correlation with
    already-counted sources in the same pass, computed from an online exponentially-weighted
    inter-source correlation matrix — applied as a pre-processing step on the evidence
    itself, before it reaches the reliability posterior, rather than as a post-hoc ensemble
    adjustment.

(C) MISINFORMATION-RISK-DISCOUNTED EVIDENCE WEIGHTING WITH DUAL-USE RISK FLAGGING (§4.2 Step 2)
    A composite risk score built from the source's OWN tracked reliability posterior
    (no separate/external classifier required), independent corroboration count filtered by
    the correlation matrix from (B) (so correlated re-reports don't count as corroboration),
    and claim-velocity anomaly — used to discount evidence weight AND, independently, to
    raise a narrative/reputational risk flag when a claim is both high-risk and high-velocity.
    The dual use (same computation feeds both a discount and a distinct risk signal) is the
    specific claimed mechanism, motivated by the observation that a false narrative can cause
    real business harm independent of its truth value (§6).
```

(A), (B), and (C) composed together, applied specifically to multi-source financial signal
fusion and validated against realized trading performance rather than synthetic bandit
regret alone, is the overall system claim.

---

## 6. Worked Example

Grounding the misinformation mechanism (§4.2 Step 2, §5(C)) in a concrete historical pattern,
not hypothetically: in 2016, a false rumor attributing an unrelated public figure's comment to
a boycott of an Indian e-commerce company (Snapdeal) spread rapidly on social media. The
company's association with that public figure caused a real, sustained business impact
(brand damage, reported sales decline) — regardless of the claim's factual accuracy or how
quickly it was debunked.

Walked through the ASRB pipeline:

```
t0: A small number of social posts assert the claim. Low corroboration (κ_corr low),
    velocity not yet anomalous (V below v0) → MisinfoRisk low, evidence weighted ~normally,
    no risk flag yet. The system is not expected to "detect fake news" pre-emptively —
    it isn't claiming that capability.

t1: Mention velocity spikes sharply (V(c(t)) far above baseline) while independent
    corroboration stays low relative to the spike (few genuinely uncorrelated sources,
    mostly re-shares/re-reports of the same original claim, which the correlation matrix
    from §4.2 Step 1 correctly identifies as low-diversity, not independent confirmation)
    → MisinfoRisk rises sharply on the underlying claim cluster.
    → Evidence weight for that claim cluster is discounted in the prediction fusion
      (protects CompositeScore from over-reacting to an unverified spike).
    → SEPARATELY, the high-risk + high-velocity combination raises a narrative/reputational
      RiskLevel flag on the affected symbol — surfacing "a fast-moving, poorly-corroborated
      narrative is forming around this company" as decision-relevant information in its own
      right, because that pattern itself has historically preceded real, lasting business
      impact (as in the source event), independent of whether the claim holds up.

t2+: As real news outlets and company statements (genuinely independent sources, low ρ_ij
    with the original cluster) begin corroborating or refuting the claim, κ_corr updates
    and MisinfoRisk adjusts accordingly on subsequent passes.
```

This illustrates the design intent precisely: discount unverified virality as a *prediction*
input while still tracking it as a *risk* input — the two are different questions and ASRB
answers them separately from the same underlying computation.

---

## 7. Evaluation Methodology

**No results are reported here.** This section specifies the methodology; it is not yet
executed. Real numbers require implementing ASRB and running it against HMIP's existing
Stage 6 backtesting infrastructure (`BacktestRunner`, `WalkForwardValidator`,
`StrategyMetricsEngine`) — a further engineering step.

### 7.1 Baselines ("latest counterpart" comparisons)

```
B1  Naive fixed-weight fusion       — HMIP's actual pre-ASRB behavior (hardcoded weights,
                                      no learning at all)
B2  Plain Thompson Sampling         — Beta-Bernoulli, no context, no discount, no correlation-
                                      or misinformation-awareness
B3  Gittins-index-only              — static index lookup, no adaptation to drift or context
B4  Discounted Thompson Sampling    — non-stationary-aware, still no context/correlation/
                                      misinformation-awareness
B5  Neural-linear bandit alone      — contextual, no Gittins gating, no correlation or
                                      misinformation discount
B6  ASRB minus mechanism (B)         — ablation: correlation discount removed
B7  ASRB minus mechanism (C)         — ablation: misinformation discount removed
B8  Full ASRB                        — proposed system
```
B6/B7 are the ablations that isolate whether each claimed novel mechanism actually
contributes, not just the full system vs. naive baseline.

### 7.2 Metrics

```
Cumulative regret            — standard bandit metric, vs. an oracle source-weighting
Hit-rate                     — existing ModelPerformanceTracker concept, reused directly
Sharpe ratio, max drawdown,  — via existing StrategyMetricsEngine (Stage 6), no new
  win rate, profit factor      evaluation infra needed
Statistical significance     — paired comparison across WalkForwardValidator's rolling
                                windows (Wilcoxon signed-rank), not a single train/test split
```

### 7.3 Robustness Stress Tests

```
Injected regime shift   — synthetically flip a source's reliability mid-backtest; measure
                           adaptation lag (B4/B8 should recover faster than B1/B2/B3)
Injected correlation    — inject duplicate/near-duplicate sources (same underlying signal,
                           independent noise added); measure whether CompositeScore
                           overreacts (B1/B2/B3/B4/B5/B7 should overreact; B6/B8 should not)
Injected misinformation — inject a synthetic high-velocity, low-corroboration false signal;
                           measure prediction impact (should be small for B8) and confirm the
                           narrative-risk flag fires (§5(C))
```

---

## 8. Scope If This Becomes a Research Paper

Realistic, honestly-scoped contribution for a workshop/applied-ML venue (e.g. ICAIF, a
quantitative-finance-adjacent KDD/NeurIPS workshop) or a quantitative finance journal:

```
IN SCOPE (the actual claimed contribution):
  - The fusion/bandit mechanism itself: stability-gated policy selection (§5A),
    correlation-discounted weighting (§5B), misinformation-risk-discounted weighting
    with dual-use risk flagging (§5C) — applied specifically to multi-source financial
    signal fusion
  - Empirical validation against REALIZED TRADING PERFORMANCE (Sharpe, drawdown, walk-
    forward significance) rather than synthetic bandit regret alone — this is the
    differentiated empirical angle; most bandit papers evaluate on synthetic or
    non-financial data
  - Ablation study isolating each mechanism's individual contribution (§7.1 B6/B7)

OUT OF SCOPE (do not overclaim these):
  - NOT a new misinformation/fake-news classifier — the corroboration/velocity heuristic
    (§4.2 Step 2) is applied as an input feature; building state-of-the-art rumor detection
    from scratch is itself a substantial, separate research problem
  - NOT a new base NLP/embedding model — claim-clustering (§4.2, c_i(t)) uses an existing
    pretrained encoder (choice open, §10), not a newly trained one
  - NOT full deep RL — the bandit family IS the RL-adjacent component being used (multi-armed
    bandits are a recognized RL subfield); a full state/action/reward training loop with a
    replay buffer was considered and deliberately deferred as disproportionate to the
    problem (see prior design discussion) — revisit only if bandit-level adaptation proves
    insufficient in practice
```

Requirements to actually reach paper-submission quality: a full implementation, a real
multi-symbol/multi-year backtest run (not a handful of dev symbols), and — the hardest part
honestly — either labeled misinformation-event ground truth or a defensible synthetic-injection
methodology (§7.3) to evaluate mechanism (C) specifically, since real labeled financial-rumor
datasets are scarce.

---

## 9. Legal & Compliance Considerations

Full treatment lives in `HFT_ARCHITECTURE.md` §24.6 (kept in sync with this section since this
document may circulate independently to counsel). Summary:

```
Not legal advice. Two independent axes, do not conflate them:

1. Platform developer ToS (contract law) — the sharper, more universal risk. A connecting
   user's consent does NOT waive our contractual obligations to Twitter/X, Reddit, etc.
   Must be checked per-source before enabling pooled use of that source's data.

2. Data protection law (GDPR / India's DPDP Act 2023, given this platform's NSE/BSE scope /
   CCPA) — consent-based processing is workable for an opt-in feature, but requires accurate
   disclosure (not "we store zero personal data" — the account-linkage record itself is
   personal data), a defined retention period, and a working deletion path.

Recommend counsel review before pooled BYOC (HFT_ARCHITECTURE.md §24.3) ships to real users.
```

---

## 10. Open Engineering Decisions

```
Claim-clustering / context encoder: transformer-based sentence encoder recommended over
  LSTM for both the neural-linear context layer and claim-similarity clustering, per current
  practice; LSTM remains viable if lower compute/complexity is preferred. Not yet decided.
w1, w2, w3, v0, τ_risk, τ_velocity, τ_stability, κ, λ_time, λ_corr, risk_aversion:
  all require calibration against real backtest data — values above are structural, not
  final. No calibrated constants should be treated as claims until §7 is actually executed.
Moment-matching method for the Beta-Bernoulli approximation used in Gittins lookup (§4.2
  Step 5): standard method-of-moments proposed, not yet validated against the closed-form
  linear posterior it's approximating.
```

---

*This document should be re-synced with `HFT_ARCHITECTURE.md` §24 whenever either changes —
the architecture doc carries the practical/operational view, this one carries the algorithm's
formal specification and novelty argument.*
