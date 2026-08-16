package com.hft.ml.onnx;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * Converts a raw 41-dim feature vector (com.hft.ml.MLFeatureVector.toContextArray() shape —
 * same convention com.hft.intelligence.SourceSignal uses) into model input, and the model's
 * scalar output back into a plain double.
 *
 * Contract for any .onnx file placed at hft.onnx.model-path: input is a single [1, 41] float
 * tensor (batch size 1 — this service scores one symbol at a time, no batching), output is a
 * single scalar float, expected on a 0-100 scale (same convention as every other composite
 * score in this codebase — technicalScore, sentimentScore, etc.) so it's directly comparable/
 * pluggable wherever those are used.
 */
class OnnxFeatureTranslator implements Translator<double[], Double> {

    @Override
    public NDList processInput(TranslatorContext ctx, double[] input) {
        NDArray array = ctx.getNDManager().create(input, new ai.djl.ndarray.types.Shape(1, input.length));
        return new NDList(array.toType(ai.djl.ndarray.types.DataType.FLOAT32, false));
    }

    @Override
    public Double processOutput(TranslatorContext ctx, NDList list) {
        NDArray output = list.singletonOrThrow();
        return (double) output.toFloatArray()[0];
    }

    @Override
    public Batchifier getBatchifier() {
        return null;   // batch dimension already added explicitly in processInput
    }
}
