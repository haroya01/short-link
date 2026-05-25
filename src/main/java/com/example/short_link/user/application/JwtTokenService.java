package com.example.short_link.user.application;

import com.example.short_link.user.application.dto.ParsedAccess;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.domain.RefreshToken;
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
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtTokenService {

  private static final String CLAIM_TYPE = "type";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";
  private static final String TYPE_TWOFA_CHALLENGE = "twofa_challenge";
  private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtTokenService(JwtProperties props) throws GeneralSecurityException {
    this.accessTtl = props.accessTtl();
    this.refreshTtl = props.refreshTtl();
    String privateKeyPem = props.privateKey();
    String publicKeyPem = props.publicKey();
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

  /**
   * Short-lived token issued after primary auth succeeds for a 2FA-enabled user. The frontend holds
   * it while the user enters their TOTP code; on success it's exchanged for a real access token.
   * Cannot be used as an access token (different {@code type} claim) so it won't pass {@link
   * #parseAccessToken}.
   */
  public String createTwoFactorChallengeToken(Long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(CHALLENGE_TTL)))
        .claim(CLAIM_TYPE, TYPE_TWOFA_CHALLENGE)
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public Long parseTwoFactorChallengeToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_TWOFA_CHALLENGE.equals(claims.get(CLAIM_TYPE))) {
      throw new IllegalArgumentException("expected 2FA challenge token");
    }
    return Long.valueOf(claims.getSubject());
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
