package com.hft.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads Authorization: Bearer <token>, verifies it, and populates SecurityContext on success.
 * Never rejects here — an invalid/missing token just leaves the request unauthenticated, and
 * SecurityConfig's authorizeHttpRequests rules decide whether that's allowed for this path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<DecodedJWT> decoded = jwtService.verify(token);
            if (decoded.isPresent() && jwtService.isAccessToken(decoded.get())) {
                DecodedJWT jwt = decoded.get();
                String username = jwtService.getUsername(jwt);
                List<GrantedAuthority> authorities = jwtService.getRoles(jwt).stream()
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.debug("[JwtAuthFilter] Missing/invalid/non-access token on {}", request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }
}
