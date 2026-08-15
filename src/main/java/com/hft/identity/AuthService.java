package com.hft.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record LoginResult(String accessToken, String refreshToken, String username, Set<Role> roles) {}

    public User register(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("Username already taken: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("Email already registered: " + email);
        }
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .roles(new HashSet<>(Set.of(Role.USER)))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        User saved = userRepository.save(user);
        log.info("[Auth] Registered new user: {}", username);
        return saved;
    }

    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Account disabled");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRoles());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());
        return new LoginResult(accessToken, refreshToken, user.getUsername(), user.getRoles());
    }

    public String refresh(String refreshToken) {
        DecodedJWT decoded = jwtService.verify(refreshToken)
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));
        String username = jwtService.getUsername(decoded);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        return jwtService.generateAccessToken(user.getUsername(), user.getRoles());
    }

    public User loadByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }
}
