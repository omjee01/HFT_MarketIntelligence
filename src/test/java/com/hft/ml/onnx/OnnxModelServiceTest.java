package com.hft.ml.onnx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the real, shipped default state: no model configured -> every consumer gets a
 * clean, honest "unavailable" signal rather than a crash or a fabricated score. Stage 11
 * deliberately ships with no bundled .onnx model — see docs/STAGE11_ONNX_SERVING.md.
 */
@DisplayName("OnnxModelService Tests")
class OnnxModelServiceTest {

    @Test
    @DisplayName("No model-path configured -> unavailable, does not throw")
    void afterPropertiesSet_blankModelPath_staysUnavailable() {
        OnnxModelService service = new OnnxModelService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "modelPath", "");

        assertThatCode(service::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Disabled via hft.onnx.enabled=false -> unavailable regardless of model-path")
    void afterPropertiesSet_disabled_staysUnavailable() {
        OnnxModelService service = new OnnxModelService();
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "modelPath", "/some/path.onnx");

        service.afterPropertiesSet();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Configured path that doesn't exist -> unavailable, does not throw")
    void afterPropertiesSet_missingFile_staysUnavailable() {
        OnnxModelService service = new OnnxModelService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "modelPath", "/nonexistent/path/model.onnx");

        assertThatCode(service::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("predict() on an unavailable service returns empty, never throws")
    void predict_whenUnavailable_returnsEmpty() {
        OnnxModelService service = new OnnxModelService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "modelPath", "");
        service.afterPropertiesSet();

        assertThat(service.predict(new double[41])).isEmpty();
    }

    @Test
    @DisplayName("shutdown() on an unavailable (never-loaded) service is a safe no-op")
    void shutdown_whenNeverLoaded_doesNotThrow() {
        OnnxModelService service = new OnnxModelService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "modelPath", "");
        service.afterPropertiesSet();

        assertThatCode(service::shutdown).doesNotThrowAnyException();
    }
}
