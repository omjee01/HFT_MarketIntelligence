package com.hft.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ADMIN-only (gated at the SecurityConfig filter-chain level via /api/v1/admin/**).
 * Credential values are write-only through this API: GET never returns raw keys/secrets,
 * only whether one is configured plus audit metadata.
 */
@RestController
@RequestMapping("/api/v1/admin/settings/credentials")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    public record CredentialStatusResponse(String provider, boolean configured, String updatedAt, String updatedBy) {}

    @GetMapping
    public ResponseEntity<List<CredentialStatusResponse>> list() {
        List<CredentialStatusResponse> body = settingsService.listStatus().stream()
                .map(s -> new CredentialStatusResponse(
                        s.provider().name(), s.configured(),
                        s.updatedAt() != null ? s.updatedAt().toString() : null,
                        s.updatedBy()))
                .toList();
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{provider}")
    public ResponseEntity<CredentialStatusResponse> set(@PathVariable PlatformCredentialProvider provider,
                                                          @RequestBody Map<String, String> body,
                                                          Authentication authentication) {
        PlatformSettingsService.CredentialStatus status;
        if (provider == PlatformCredentialProvider.REDDIT) {
            String clientId = body.get("clientId");
            String clientSecret = body.get("clientSecret");
            if (isBlank(clientId) || isBlank(clientSecret)) {
                throw new IllegalArgumentException("REDDIT requires clientId and clientSecret");
            }
            status = settingsService.setOAuthCredential(provider, clientId, clientSecret, authentication.getName());
        } else {
            String apiKey = body.get("apiKey");
            if (isBlank(apiKey)) {
                throw new IllegalArgumentException(provider + " requires apiKey");
            }
            status = settingsService.setApiKeyCredential(provider, apiKey, authentication.getName());
        }
        return ResponseEntity.ok(new CredentialStatusResponse(
                status.provider().name(), status.configured(), status.updatedAt().toString(), status.updatedBy()));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> clear(@PathVariable PlatformCredentialProvider provider) {
        settingsService.clearCredential(provider);
        return ResponseEntity.noContent().build();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
