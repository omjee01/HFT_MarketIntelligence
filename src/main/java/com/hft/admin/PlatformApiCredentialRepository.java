package com.hft.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformApiCredentialRepository extends JpaRepository<PlatformApiCredential, Long> {
    Optional<PlatformApiCredential> findByProvider(PlatformCredentialProvider provider);
}
