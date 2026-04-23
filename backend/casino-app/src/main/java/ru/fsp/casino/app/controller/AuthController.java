package ru.fsp.casino.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.fsp.casino.app.dto.auth.LoginRequest;
import ru.fsp.casino.app.dto.auth.TokenResponse;
import ru.fsp.casino.app.security.JwtTokenProvider;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.UserRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
            .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "code", "AUTH_FAILED"
            ));
        }
        String token = tokenProvider.generateToken(user);
        return ResponseEntity.ok(new TokenResponse(
            token, user.getId(), user.getUsername(),
            user.getVipTier().name(), user.getRole().name(),
            user.getBalance(), user.getReservedBalance()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<TokenResponse> me(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(new TokenResponse(
            null, user.getId(), user.getUsername(),
            user.getVipTier().name(), user.getRole().name(),
            user.getBalance(), user.getReservedBalance()
        ));
    }
}
