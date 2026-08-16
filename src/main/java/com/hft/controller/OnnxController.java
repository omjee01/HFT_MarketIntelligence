package com.hft.controller;

import com.hft.ml.onnx.OnnxModelService;
import com.hft.model.dto.ApiResponse;
import com.hft.model.enums.Market;
import com.hft.service.signal.RecommendationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * ONNX model serving (Stage 11, HFT_ARCHITECTURE.md §28). No model is bundled by default —
 * every response here is honest about whether a real model is actually loaded, never a
 * fabricated score. See docs/STAGE11_ONNX_SERVING.md.
 */
@RestController
@RequestMapping("/api/v1/ml/onnx")
@RequiredArgsConstructor
@Tag(name = "ONNX", description = "ONNX model serving status and prediction (Stage 11 — infra only, no model bundled)")
public class OnnxController {

    private final OnnxModelService onnxModelService;
    private final RecommendationEngine engine;

    @GetMapping("/status")
    @Operation(summary = "Whether a real ONNX model is currently loaded and serving")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        boolean available = onnxModelService.isAvailable();
        return ResponseEntity.ok(ApiResponse.success(Map.of("available", available),
                available ? "ONNX model loaded and serving" : "No ONNX model loaded — see hft.onnx.model-path"));
    }

    @GetMapping("/predict/{symbol}")
    @Operation(summary = "ONNX model prediction for a symbol",
               description = "Returns NO_MODEL if no .onnx file is configured (the default), or NO_DATA if a quote isn't available.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> predict(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US_NASDAQ") Market market) {

        if (!onnxModelService.isAvailable()) {
            return ResponseEntity.ok(ApiResponse.error(
                    "No ONNX model loaded — set hft.onnx.model-path to a real .onnx file", "NO_MODEL"));
        }

        Optional<Double> prediction = engine.getOnnxPrediction(symbol.toUpperCase(), market);
        if (prediction.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("No quote available for " + symbol, "NO_DATA"));
        }
        Map<String, Object> body = Map.of(
                "symbol", symbol.toUpperCase(), "market", market.name(), "score", prediction.get());
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
