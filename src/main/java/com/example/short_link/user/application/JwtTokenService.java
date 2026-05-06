package com.example.short_link.user.application;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtTokenService(
      @Value("${short-link.jwt.secret}") String secret,
      @Value("${short-link.jwt.access-ttl}") Duration accessTtl,
      @Value("${short-link.jwt.refresh-ttl}") Duration refreshTtl) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public String createAccessToken(Long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .claim("type", "access")
        .signWith(key)
        .compact();
  }

  public RefreshToken createRefreshToken(Long userId) {
    Instant now = Instant.now();
    String jti = UUID.randomUUID().toString();
    String token =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(refreshTtl)))
            .claim("type", "refresh")
            .signWith(key)
            .compact();
    return new RefreshToken(token, jti);
  }

  public Long parseUserId(String token) {
    return Long.valueOf(
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
  }

  public String parseJti(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getId();
  }

  public Duration refreshTtl() {
    return refreshTtl;
  }
}
