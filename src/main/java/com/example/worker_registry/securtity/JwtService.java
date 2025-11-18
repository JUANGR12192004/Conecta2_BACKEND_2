package com.example.worker_registry.securtity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.activation.exp-min:15}")
    private long activationExpMin;

    @Value("${app.jwt.access.exp-min:60}")
    private long accessExpMin;

    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Activation =====
    public String generateActivationToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "activation")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(activationExpMin * 60)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isActivationToken(String token) {
        try {
            Claims claims = parse(token);
            return "activation".equals(claims.get("type"));
        } catch (Exception ex) {
            return false;
        }
    }

    public Long parseActivationToken(String token) {
        Claims claims = parse(token);
        Object type = claims.get("type");
        if (type == null || !"activation".equals(type.toString())) {
            throw new IllegalArgumentException("Token invalido para activacion");
        }
        return Long.valueOf(claims.getSubject());
    }

    // ===== Access =====
    public String generateAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "access")
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessExpMin * 60)))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public AccessTokenPayload parseAccessToken(String token) {
        Claims claims = parse(token);
        Object type = claims.get("type");
        if (type == null || !"access".equals(type.toString())) {
            throw new IllegalArgumentException("Token no corresponde a un acceso valido");
        }
        String role = String.valueOf(claims.get("role"));
        Long userId = Long.valueOf(claims.getSubject());
        return new AccessTokenPayload(userId, role);
    }

    // ===== Helpers =====
    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public record AccessTokenPayload(Long userId, String role) {}
}
