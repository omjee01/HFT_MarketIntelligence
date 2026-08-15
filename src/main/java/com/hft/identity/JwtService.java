package com.hft.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issues and verifies JWTs via com.auth0:java-jwt (NOT the JJWT Jwts.builder() API —
 * this project uses Auth0's library, see build.gradle.kts).
 */
@Slf4j
@Service
public class JwtService {

    @Value("${hft.jwt.secret}")
    private String secret;

    @Value("${hft.jwt.expiration-ms}")
    private long accessExpirationMs;

    @Value("${hft.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private Algorithm algorithm() {
        return Algorithm.HMAC256(secret);
    }

    public String generateAccessToken(String username, Set<Role> roles) {
        return JWT.create()
                .withSubject(username)
                .withClaim("roles", roles.stream().map(Enum::name).collect(Collectors.toList()))
                .withClaim("type", "access")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExpirationMs))
                .sign(algorithm());
    }

    public String generateRefreshToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withClaim("type", "refresh")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .sign(algorithm());
    }

    public Optional<DecodedJWT> verify(String token) {
        try {
            return Optional.of(JWT.require(algorithm()).build().verify(token));
        } catch (JWTVerificationException e) {
            log.debug("[JWT] Verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAccessToken(DecodedJWT jwt) {
        return "access".equals(jwt.getClaim("type").asString());
    }

    public boolean isRefreshToken(DecodedJWT jwt) {
        return "refresh".equals(jwt.getClaim("type").asString());
    }

    public List<String> getRoles(DecodedJWT jwt) {
        List<String> roles = jwt.getClaim("roles").asList(String.class);
        return roles != null ? roles : List.of();
    }

    public String getUsername(DecodedJWT jwt) {
        return jwt.getSubject();
    }
}
