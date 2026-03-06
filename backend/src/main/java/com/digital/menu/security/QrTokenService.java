package com.digital.menu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QrTokenService {
    @Value("${app.qr.secret:}")
    private String qrSecret;

    @PostConstruct
    public void validateSecret() {
        if (qrSecret == null || qrSecret.isBlank()) {
            throw new IllegalStateException("app.qr.secret must be configured");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(qrSecret);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("app.qr.secret must decode to at least 32 bytes");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("app.qr.secret must be valid base64", ex);
        }
    }

    public String generateToken(String tenantId, Integer tableNumber) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException("tableNumber must be greater than 0");
        }

        Instant now = Instant.now();
        return Jwts.builder()
            .setSubject("qr-order-session")
            .addClaims(Map.of("tenantId", tenantId, "tableNumber", tableNumber))
            .setIssuedAt(Date.from(now))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public QrContext parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("qrToken is required");
        }

        Claims claims = Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();

        String tenantId = claims.get("tenantId", String.class);
        Integer tableNumber = claims.get("tableNumber", Integer.class);
        if (tenantId == null || tenantId.isBlank() || tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException("Invalid QR token payload");
        }
        return new QrContext(tenantId, tableNumber);
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(qrSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record QrContext(String tenantId, Integer tableNumber) {}
}
