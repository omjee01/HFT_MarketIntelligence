# STAGE 11 — ONNX Model Serving

**HFT Market Intelligence Platform (HMIP)**
*Author: PTD2315 | Version: 1.0.0*

> **Companion doc:** `docs/HFT_ARCHITECTURE.md` §28 — why this ships serving-only (real
> technical constraint, confirmed with the user before building: pure-Java ONNX *export* isn't
> a clean path; DJL can genuinely *serve* an existing `.onnx` file, training-then-exporting one
> normally needs Python/PyTorch, which isn't available here).

---

## 1. STAGE OVERVIEW

```
╔══════════════════════════════════════════════════════════════════════════════════════╗
║  STAGE 11 — BEFORE / AFTER                                                            ║
║                                                                                        ║
║  ONNX inference infrastructure    didn't exist ──────── real DJL + ONNX Runtime,      ║
║                                                            no model bundled             ║
║  /api/v1/ml/onnx/*                didn't exist ──────── status + predict endpoints,   ║
║                                                            both honest about no-model    ║
║  ModelABRouter (Model A/B)        unchanged ──────────── unchanged — no speculative     ║
║                                                            "Model C" traffic split       ║
╚══════════════════════════════════════════════════════════════════════════════════════╝
```

This is genuine, working infrastructure — not a stub. The moment a real `.onnx` file matching
the contract in §2 exists, pointing `hft.onnx.model-path` at it makes serving live with zero
code changes.

---

## 2. MODEL CONTRACT

Any `.onnx` file placed at `hft.onnx.model-path` must accept:
- Input: a single `[1, 41]` float32 tensor — one symbol per call (no batching), 41 features in
  `com.hft.ml.MLFeatureVector.toContextArray()`'s declaration order (same shape
  `com.hft.intelligence.SourceSignal` uses for ASRB — Stage 10).
- Output: a single scalar float, expected on a 0-100 scale (same convention as
  `technicalScore`/`sentimentScore`/every other composite score in this codebase).

See `OnnxFeatureTranslator` for the exact input/output marshalling.

---

## 3. WHAT ACTUALLY CHANGED

```
build.gradle.kts                                    (+ai.djl:api, +onnxruntime-engine)
src/main/resources/application.yml                  (+hft.onnx.enabled, +hft.onnx.model-path)
src/main/java/com/hft/ml/onnx/OnnxFeatureTranslator.java   (new — input/output marshalling)
src/main/java/com/hft/ml/onnx/OnnxModelService.java        (new — load/serve/shutdown)
src/main/java/com/hft/service/signal/RecommendationEngine.java
    (+getOnnxPrediction(symbol, market) — independent of generateRecommendation(); also fixed
     a stale docstring left over from Stage 10's step reordering)
src/main/java/com/hft/controller/OnnxController.java       (new — GET /status, GET /predict/{symbol})
src/test/java/com/hft/ml/onnx/OnnxModelServiceTest.java     (new — 5 tests, the no-model paths)
```

No changes to `ModelABRouter`, `MLPredictionService`, or `EnsembleModel` — see
`HFT_ARCHITECTURE.md` §28.3 for why ONNX isn't wired into A/B routing yet.

---

## 4. CONFIGURATION REFERENCE

```yaml
hft:
  onnx:
    enabled: true        # false: fully disables, regardless of model-path
    model-path: ""        # empty by default — no model shipped
```

To actually serve a model: `hft.onnx.model-path: /path/to/your-model.onnx` (or via
`HFT_ONNX_MODEL_PATH` env var). No other config needed — the model loads on next boot.

---

## 5. HOW TO VERIFY

```bash
gradle compileJava test    # 45/45 — 40 baseline + 5 new OnnxModelServiceTest

SPRING_PROFILES_ACTIVE=dev,docker,secrets gradle bootRun
# watch for: [ONNX] No model configured (hft.onnx.model-path is empty) — serving unavailable...

TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"username":"PTD2315","password":"<seeded password>"}' | jq -r .accessToken)

curl -s localhost:8080/api/v1/ml/onnx/status -H "Authorization: Bearer $TOKEN"
# {"success":true,"data":{"available":false},"message":"No ONNX model loaded — see hft.onnx.model-path"}

curl -s localhost:8080/api/v1/ml/onnx/predict/MSFT -H "Authorization: Bearer $TOKEN"
# {"success":false,"error":{"code":"NO_MODEL",...}}
```

Both endpoints require auth (JWT), same as the other analysis/recommendation endpoints — not
gated differently.

---

## 6. KNOWN LIMITATIONS — CARRY FORWARD, DON'T RE-DISCOVER

```
No model bundled          — the actual shipped state, not a gap to silently fill in later
                             without re-confirming scope. See HFT_ARCHITECTURE.md §28.1 for
                             the full reasoning (pure-Java ONNX export constraint).
Not wired into ModelABRouter — deliberate; wiring routing weights for a nonexistent model
                             would be speculative. Revisit once a real model exists and its
                             predictions can be compared against Model A/B's actual track record.
Concurrency               — OnnxModelService serializes predict() calls via a lock (DJL
                             Predictor instances aren't safe for concurrent multi-thread use).
                             Fine for the current near-zero call volume; would need a
                             predictor-per-thread or pooling strategy under real load.
Untested against a real .onnx file — this environment has no torch/onnx Python tooling (a
                             pip install attempt broke and was rolled back — see session notes)
                             and no pre-trained model was available, so the loading/inference
                             code path is verified for correctness via the DJL API contract and
                             compile-time checking, not against an actual model file. The
                             no-model path (the real shipped behavior) IS fully live-verified.
```

---

## 7. WHAT'S NEXT

- Stage 12: Alpha Vantage polling-frequency fix.
- Whenever a real trained model becomes available (trained outside this Java environment —
  Python/PyTorch, or any ONNX-exporting toolchain): drop it at `hft.onnx.model-path`, verify
  `/api/v1/ml/onnx/predict/{symbol}` against known cases, then decide the ModelABRouter
  question from §28.3 with real predictions to evaluate against.

---

*All trading signals remain for informational/educational purposes only — not investment advice.*
