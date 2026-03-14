package com.tradingsim.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Generate access token ─────────────────────────────────────
    public String generateAccessToken(UUID userId, String email, String role) {
        return buildToken(
                Map.of("role", role, "type", "ACCESS"),
                email,
                userId.toString(),
                accessTokenExpiryMs);
    }

    // ── Generate refresh token ────────────────────────────────────
    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(
                Map.of("type", "REFRESH"),
                email,
                userId.toString(),
                refreshTokenExpiryMs);
    }

    // ── Validate token ────────────────────────────────────────────
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    // ── Extract email (subject) ───────────────────────────────────
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // ── Extract userId ────────────────────────────────────────────
    public String extractUserId(String token) {
        return parseClaims(token).getId();
    }

    // ── Extract token type ────────────────────────────────────────
    public String extractTokenType(String token) {
        return (String) parseClaims(token).get("type");
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    // ── Private helpers ───────────────────────────────────────────
    private String buildToken(Map<String, Object> extraClaims, String subject,
            String jwtId, long expiryMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .id(jwtId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getSignKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String extractRole(String token) {
        return (String) parseClaims(token).get("role");
    }
}
