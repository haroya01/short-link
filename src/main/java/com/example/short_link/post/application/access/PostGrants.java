package com.example.short_link.post.application.access;

import com.example.short_link.post.domain.PostEntity;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// A grant names its expiry and is signed over the post and its current password hash, so a new
// password ends every grant handed out before it.
@Component
public class PostGrants {

  static final Duration TTL = Duration.ofHours(24);
  private static final String LABEL = "kurl/post-grant/v1";
  private static final String HMAC = "HmacSHA256";

  private final byte[] key;
  private final Clock clock;

  public PostGrants(@Value("${short-link.jwt.private-key:}") String signingSecret, Clock clock) {
    this.key = signingSecret.isBlank() ? randomKey() : hmac(bytes(signingSecret), bytes(LABEL));
    this.clock = clock;
  }

  public Grant issue(PostEntity post) {
    Instant expiresAt = clock.instant().plus(TTL).truncatedTo(ChronoUnit.SECONDS);
    long expiry = expiresAt.getEpochSecond();
    return new Grant(expiry + "." + signature(post, expiry), expiresAt);
  }

  public boolean verify(PostEntity post, String grant) {
    if (grant == null || post.getPasswordHash() == null) return false;
    int dot = grant.indexOf('.');
    if (dot <= 0) return false;
    long expiry;
    try {
      expiry = Long.parseLong(grant.substring(0, dot));
    } catch (NumberFormatException malformed) {
      return false;
    }
    if (clock.instant().getEpochSecond() >= expiry) return false;
    return MessageDigest.isEqual(bytes(grant.substring(dot + 1)), bytes(signature(post, expiry)));
  }

  private String signature(PostEntity post, long expiry) {
    String payload = post.getId() + ":" + expiry + ":" + post.getPasswordHash();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, bytes(payload)));
  }

  private static byte[] hmac(byte[] secret, byte[] payload) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return mac.doFinal(payload);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] randomKey() {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    return random;
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  public record Grant(String grant, Instant expiresAt) {}
}
