package com.example.short_link.user.application;

import com.example.short_link.user.application.dto.ParsedAccess;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.dto.TwoFactorChallenge;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
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
import java.time.Clock;
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

  public static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

  private static final String CLAIM_TYPE = "type";

  private static final String CLAIM_SESSION = "sid";
  private static final String CLAIM_CHANNEL = "ch";
  private static final String CLAIM_RESTORES_ACCOUNT = "restore";
  private static final String CHANNEL_WEB = "web";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";
  private static final String TYPE_TWOFA_CHALLENGE = "twofa_challenge";
  private static final String TYPE_STREAM = "stream";
  private static final String CLAIM_SHORT_CODE = "shortCode";
  private static final Duration STREAM_TTL = Duration.ofMinutes(2);

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final Duration mobileRefreshTtl;
  private final Clock clock;

  public JwtTokenService(JwtProperties props, Clock clock) throws GeneralSecurityException {
    this.accessTtl = props.accessTtl();
    this.refreshTtl = props.refreshTtl();
    this.mobileRefreshTtl = props.mobileRefreshTtl();
    this.clock = clock;
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

  public String createAccessToken(Long userId, String role) {
    return createAccessToken(userId, role, null);
  }

  public String createAccessToken(Long userId, String role, String sessionId) {
    Instant now = clock.instant();
    var builder =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .claim("role", role);
    if (sessionId != null) builder.claim(CLAIM_SESSION, sessionId);
    return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
  }

  public RefreshToken createRefreshToken(Long userId, String sessionId, SessionChannel channel) {
    Instant now = clock.instant();
    String jti = UUID.randomUUID().toString();
    var builder =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(refreshTtl(channel))))
            .claim(CLAIM_TYPE, TYPE_REFRESH);
    if (sessionId != null) builder.claim(CLAIM_SESSION, sessionId);
    if (channel == SessionChannel.WEB) builder.claim(CLAIM_CHANNEL, CHANNEL_WEB);
    return new RefreshToken(builder.signWith(privateKey, Jwts.SIG.RS256).compact(), jti);
  }

  public ParsedAccess parseAccessTokenDetailed(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE))) {
      throw new UserException(UserErrorCode.INVALID_TOKEN_TYPE, "expected access token");
    }
    String role = claims.get("role", String.class);
    return new ParsedAccess(
        Long.valueOf(claims.getSubject()),
        role == null ? "USER" : role,
        claims.get(CLAIM_SESSION, String.class));
  }

  public ParsedRefresh parseRefreshToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
      throw new UserException(UserErrorCode.INVALID_TOKEN_TYPE, "expected refresh token");
    }
    return new ParsedRefresh(
        Long.valueOf(claims.getSubject()),
        claims.getId(),
        claims.get(CLAIM_SESSION, String.class),
        CHANNEL_WEB.equals(claims.get(CLAIM_CHANNEL)));
  }

  // Its own type claim keeps it from passing as an access token; only second-factor verification
  // exchanges it for a session.
  public String createTwoFactorChallengeToken(Long userId, boolean restoresAccount) {
    Instant now = clock.instant();
    var builder =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(CHALLENGE_TTL)))
            .claim(CLAIM_TYPE, TYPE_TWOFA_CHALLENGE);
    if (restoresAccount) builder.claim(CLAIM_RESTORES_ACCOUNT, true);
    return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
  }

  public TwoFactorChallenge parseTwoFactorChallenge(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_TWOFA_CHALLENGE.equals(claims.get(CLAIM_TYPE))) {
      throw new UserException(UserErrorCode.INVALID_TOKEN_TYPE, "expected 2FA challenge token");
    }
    return new TwoFactorChallenge(
        Long.valueOf(claims.getSubject()),
        Boolean.TRUE.equals(claims.get(CLAIM_RESTORES_ACCOUNT, Boolean.class)));
  }

  public String createStreamToken(Long userId, String shortCode) {
    return createStreamToken(userId, shortCode, STREAM_TTL);
  }

  public String createStreamToken(Long userId, String shortCode, Duration ttl) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .claim(CLAIM_TYPE, TYPE_STREAM)
        .claim(CLAIM_SHORT_CODE, shortCode)
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public Long parseStreamToken(String token, String shortCode) {
    Claims claims = parseClaims(token);
    if (!TYPE_STREAM.equals(claims.get(CLAIM_TYPE))) {
      throw new UserException(UserErrorCode.INVALID_TOKEN_TYPE, "expected stream token");
    }
    String tokenShortCode = claims.get(CLAIM_SHORT_CODE, String.class);
    if (!shortCode.equals(tokenShortCode)) {
      throw new UserException(UserErrorCode.INVALID_TOKEN_TYPE, "stream token short code mismatch");
    }
    return Long.valueOf(claims.getSubject());
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(publicKey)
        .clock(() -> Date.from(clock.instant()))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public Duration refreshTtl(SessionChannel channel) {
    return channel == SessionChannel.MOBILE ? mobileRefreshTtl : refreshTtl;
  }

  public Duration accessTtl() {
    return accessTtl;
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
