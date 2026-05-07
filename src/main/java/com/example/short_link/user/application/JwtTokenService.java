package com.example.short_link.user.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtTokenService {

  private static final String CLAIM_TYPE = "type";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtTokenService(
      @Value("${short-link.jwt.private-key:}") String privateKeyPem,
      @Value("${short-link.jwt.public-key:}") String publicKeyPem,
      @Value("${short-link.jwt.access-ttl}") Duration accessTtl,
      @Value("${short-link.jwt.refresh-ttl}") Duration refreshTtl)
      throws GeneralSecurityException {
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
    if (privateKeyPem.isBlank() || publicKeyPem.isBlank()) {
      log.warn(
          "JWT keypair not configured. Generating ephemeral RSA keypair for this JVM only. "
              + "Set JWT_PRIVATE_KEY and JWT_PUBLIC_KEY env vars for production.");
      KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      this.privateKey = pair.getPrivate();
      this.publicKey = pair.getPublic();
    } else {
      this.privateKey = parsePrivateKey(privateKeyPem);
      this.publicKey = parsePublicKey(publicKeyPem);
    }
  }

  public String createAccessToken(Long userId) {
    return createAccessToken(userId, "USER");
  }

  public String createAccessToken(Long userId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .claim(CLAIM_TYPE, TYPE_ACCESS)
        .claim("role", role)
        .signWith(privateKey, Jwts.SIG.RS256)
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
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    return new RefreshToken(token, jti);
  }

  public Long parseAccessToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE))) {
      throw new IllegalArgumentException("expected access token");
    }
    return Long.valueOf(claims.getSubject());
  }

  public ParsedAccess parseAccessTokenDetailed(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE))) {
      throw new IllegalArgumentException("expected access token");
    }
    String role = claims.get("role", String.class);
    return new ParsedAccess(Long.valueOf(claims.getSubject()), role == null ? "USER" : role);
  }

  public ParsedRefresh parseRefreshToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
      throw new IllegalArgumentException("expected refresh token");
    }
    return new ParsedRefresh(Long.valueOf(claims.getSubject()), claims.getId());
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
  }

  public Duration refreshTtl() {
    return refreshTtl;
  }

  private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
    String stripped =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] decoded = Base64.getDecoder().decode(stripped);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
  }

  private static PublicKey parsePublicKey(String pem) throws GeneralSecurityException {
    String stripped =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] decoded = Base64.getDecoder().decode(stripped);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
  }
}
