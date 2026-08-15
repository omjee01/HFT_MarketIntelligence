package com.hft.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Seeds one USER+ADMIN test account so local/dev testing doesn't require a chicken-and-egg
 * registration flow to get an admin. Idempotent (skips if the username already exists) and
 * config-gated off in prod — see hft.identity.seed-test-user.enabled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestUserSeeder implements ApplicationRunner {

    private static final String SEED_USERNAME = "PTD2315";
    private static final String SEED_EMAIL = "omanu01@gmail.com";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${hft.identity.seed-test-user.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) return;
        if (userRepository.existsByUsername(SEED_USERNAME)) {
            log.info("[TestUserSeeder] Seed user {} already exists, skipping", SEED_USERNAME);
            return;
        }
        String password = generatePassword(16);
        User user = User.builder()
                .username(SEED_USERNAME)
                .email(SEED_EMAIL)
                .passwordHash(passwordEncoder.encode(password))
                .roles(new HashSet<>(Set.of(Role.USER, Role.ADMIN)))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);
        log.warn("[TestUserSeeder] Seeded test user '{}' (USER+ADMIN) with generated password: {} " +
                 "-- log in once and change it. Controlled by hft.identity.seed-test-user.enabled " +
                 "(false in application-prod.yml).", SEED_USERNAME, password);
    }

    private String generatePassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
