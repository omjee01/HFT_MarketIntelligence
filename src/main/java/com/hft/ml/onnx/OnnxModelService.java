package com.hft.ml.onnx;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * ONNX model serving (Stage 11, HFT_ARCHITECTURE.md §28) — DJL + ONNX Runtime, real inference
 * infrastructure with deliberately NO bundled model.
 *
 * True ONNX *export* from a model trained in pure Java isn't a clean, well-trodden path (DJL's
 * training API is real, but exporting a DJL-trained model to .onnx format normally goes through
 * PyTorch's own Python-side torch.onnx.export, which isn't available here) — see the Stage 11
 * doc for the full reasoning. So this ships the serving HALF only: point hft.onnx.model-path
 * at any valid .onnx file (batch-1, 41-float input, single scalar 0-100 output — see
 * OnnxFeatureTranslator) and it loads and serves it. With no file configured (the default),
 * this is a no-op everywhere it's consulted — isAvailable() returns false, predict() returns
 * Optional.empty(), and every existing caller (Model A/B routing, etc.) is completely
 * unaffected. Not wired into ModelABRouter in this stage — there is no real model to route to
 * yet, and wiring routing weights for a model that doesn't exist would be speculative.
 */
@Slf4j
@Service
public class OnnxModelService implements InitializingBean {

    @Value("${hft.onnx.enabled:true}")
    private boolean enabled;

    @Value("${hft.onnx.model-path:}")
    private String modelPath;

    private ZooModel<double[], Double> model;
    private Predictor<double[], Double> predictor;
    private final Object predictLock = new Object();

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            log.info("[ONNX] Disabled (hft.onnx.enabled=false) — serving unavailable, Model A/B unaffected.");
            return;
        }
        if (modelPath == null || modelPath.isBlank()) {
            log.info("[ONNX] No model configured (hft.onnx.model-path is empty) — serving unavailable, "
                    + "Model A/B unaffected. This is the default/shipped state; see docs/STAGE11_ONNX_SERVING.md.");
            return;
        }
        Path path = Paths.get(modelPath);
        if (!Files.exists(path)) {
            log.warn("[ONNX] hft.onnx.model-path={} does not exist — serving unavailable, Model A/B unaffected.",
                    modelPath);
            return;
        }
        try {
            Criteria<double[], Double> criteria = Criteria.builder()
                    .setTypes(double[].class, Double.class)
                    .optModelPath(path)
                    .optEngine("OnnxRuntime")
                    .optTranslator(new OnnxFeatureTranslator())
                    .build();
            model = criteria.loadModel();
            predictor = model.newPredictor();
            log.info("[ONNX] Model loaded from {} — serving available.", modelPath);
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            log.error("[ONNX] Failed to load model from {}: {} — serving unavailable, Model A/B unaffected.",
                    modelPath, e.getMessage());
            model = null;
            predictor = null;
        }
    }

    public boolean isAvailable() {
        return predictor != null;
    }

    /**
     * @param context41 41-dim feature vector, com.hft.ml.MLFeatureVector.toContextArray() shape.
     * @return the model's prediction (0-100 scale, see OnnxFeatureTranslator's contract),
     *         or empty when no model is loaded.
     */
    public Optional<Double> predict(double[] context41) {
        if (!isAvailable()) return Optional.empty();
        if (context41 == null || context41.length != 41) {
            throw new IllegalArgumentException("context41 must be 41-dimensional, got "
                    + (context41 == null ? "null" : context41.length));
        }
        try {
            // DJL Predictor instances are not safe for concurrent use from multiple threads;
            // call volume here is inherently low (ad-hoc REST calls, not a hot path), so a
            // simple lock is the correct tradeoff over per-call Predictor churn.
            synchronized (predictLock) {
                return Optional.of(predictor.predict(context41));
            }
        } catch (Exception e) {
            log.warn("[ONNX] Prediction failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }
}
