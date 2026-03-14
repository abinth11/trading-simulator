package com.tradingsim.user.service;

import com.tradingsim.user.dto.UserDtos.*;
import com.tradingsim.user.entity.User;
import com.tradingsim.user.exception.ConflictException;
import com.tradingsim.user.exception.UnauthorizedException;
import com.tradingsim.user.repository.UserRepository;
import com.tradingsim.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ── Register ──────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email already in use: " + req.getEmail());
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ConflictException("Username already taken: " + req.getUsername());
        }

        User user = User.builder()
                .email(req.getEmail())
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} ({})", user.getUsername(), user.getId());

        return buildAuthResponse(user);
    }

    // ── Login ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: {} ({})", user.getUsername(), user.getId());
        return buildAuthResponse(user);
    }

    // ── Refresh token ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest req) {
        String token = req.getRefreshToken();

        if (!jwtService.isTokenValid(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (!"REFRESH".equals(jwtService.extractTokenType(token))) {
            throw new UnauthorizedException("Not a refresh token");
        }

        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return buildAuthResponse(user);
    }

    // ── Get profile ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return UserResponse.from(user);
    }

    // ── Private helpers ───────────────────────────────────────────
    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .user(UserResponse.from(user))
                .build();
    }
}
