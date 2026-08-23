package com.project.ticketflow.security;

import com.project.ticketflow.dto.auth.AccessTokenClaims;
import com.project.ticketflow.dto.auth.RefreshTokenClaims;
import com.project.ticketflow.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.access.secret}")
    private String accessSecret;

    @Value("${jwt.refresh.secret}")
    private String refreshSecret;

    @Value("${jwt.access.expiry}")
    private Long accessExpiry;

    @Value("${jwt.refresh.expiry}")
    private Long refreshExpiry;

    private SecretKey getAccessSigningKey() {
        return Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getRefreshSigningKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user, String jti) {
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(getAccessSigningKey())
                .compact();
    }

    public String generateRefreshToken(Long userId, String familyId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("fid", familyId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(getRefreshSigningKey())
                .compact();
    }

    public RefreshTokenClaims parseRefreshToken(String refreshToken) {
        Claims claims = Jwts.parser().verifyWith(getRefreshSigningKey()).build()
                .parseSignedClaims(refreshToken)
                .getPayload();

        return RefreshTokenClaims.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .familyId(claims.get("fid", String.class))
                .build();
    }

    public AccessTokenClaims parseAccessToken(String accessToken) {
        Claims claims = Jwts.parser().verifyWith(getAccessSigningKey()).build()
                .parseSignedClaims(accessToken)
                .getPayload();

        return AccessTokenClaims.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .name(claims.get("name", String.class))
                .email(claims.get("email", String.class))
                .roles(claims.get("roles", List.class))
                .jti(claims.getId())
                .build();
    }
}
