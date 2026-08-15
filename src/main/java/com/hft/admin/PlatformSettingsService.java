package com.hft.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin-managed overrides for platform API credentials (NewsAPI/FRED/Reddit), stored
 * encrypted in the DB. Consuming services (SentimentAnalysisService, MacroGeopoliticalService)
 * check here first and fall back to their env-var-backed @Value field when no override exists
 * — this lets an admin set/test a key through the UI without redeploying, while env vars still
 * work exactly as before for ops-managed deployments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformApiCredentialRepository repository;
    private final CredentialCipher cipher;

    public record CredentialStatus(PlatformCredentialProvider provider, boolean configured,
                                    LocalDateTime updatedAt, String updatedBy) {}

    public Optional<String> getOverride(PlatformCredentialProvider provider, String field) {
        return repository.findByProvider(provider)
                .map(cred -> switch (field) {
                    case "apiKey" -> cred.getEncryptedApiKey();
                    case "clientId" -> cred.getEncryptedClientId();
                    case "clientSecret" -> cred.getEncryptedClientSecret();
                    default -> null;
                })
                .filter(v -> v != null && !v.isBlank())
                .map(cipher::decrypt);
    }

    public List<CredentialStatus> listStatus() {
        return Arrays.stream(PlatformCredentialProvider.values())
                .map(provider -> repository.findByProvider(provider)
                        .map(c -> new CredentialStatus(provider, true, c.getUpdatedAt(), c.getUpdatedBy()))
                        .orElse(new CredentialStatus(provider, false, null, null)))
                .collect(Collectors.toList());
    }

    public CredentialStatus setApiKeyCredential(PlatformCredentialProvider provider, String apiKey, String updatedBy) {
        PlatformApiCredential cred = repository.findByProvider(provider)
                .orElse(PlatformApiCredential.builder().provider(provider).build());
        cred.setEncryptedApiKey(cipher.encrypt(apiKey));
        cred.setUpdatedAt(LocalDateTime.now());
        cred.setUpdatedBy(updatedBy);
        repository.save(cred);
        log.info("[PlatformSettings] {} API key updated by {}", provider, updatedBy);
        return new CredentialStatus(provider, true, cred.getUpdatedAt(), cred.getUpdatedBy());
    }

    public CredentialStatus setOAuthCredential(PlatformCredentialProvider provider, String clientId,
                                                String clientSecret, String updatedBy) {
        PlatformApiCredential cred = repository.findByProvider(provider)
                .orElse(PlatformApiCredential.builder().provider(provider).build());
        cred.setEncryptedClientId(cipher.encrypt(clientId));
        cred.setEncryptedClientSecret(cipher.encrypt(clientSecret));
        cred.setUpdatedAt(LocalDateTime.now());
        cred.setUpdatedBy(updatedBy);
        repository.save(cred);
        log.info("[PlatformSettings] {} OAuth credential updated by {}", provider, updatedBy);
        return new CredentialStatus(provider, true, cred.getUpdatedAt(), cred.getUpdatedBy());
    }

    public void clearCredential(PlatformCredentialProvider provider) {
        repository.findByProvider(provider).ifPresent(repository::delete);
        log.info("[PlatformSettings] {} credential override cleared", provider);
    }
}
