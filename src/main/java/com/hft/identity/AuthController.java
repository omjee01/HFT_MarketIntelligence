package com.hft.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response bodies here are intentionally FLAT (not wrapped in the app's usual
 * ApiResponse<T> envelope) — this is a fixed contract the UI is built against.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record RegisterResponse(String username, String email, List<String> roles) {}

    public record LoginResponse(String accessToken, String refreshToken, List<String> roles, String username) {}

    public record RefreshResponse(String accessToken) {}

    public record MeResponse(String username, String email, List<String> roles) {}

    private List<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toList());
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req.username(), req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getUsername(), user.getEmail(), roleNames(user.getRoles())));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult result = authService.login(req.username(), req.password());
        return ResponseEntity.ok(new LoginResponse(
                result.accessToken(), result.refreshToken(), roleNames(result.roles()), result.username()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        String accessToken = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new RefreshResponse(accessToken));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        User user = authService.loadByUsername(authentication.getName());
        return ResponseEntity.ok(new MeResponse(user.getUsername(), user.getEmail(), roleNames(user.getRoles())));
    }
}
