package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.ApnsProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ApnsTokenProvider {

  private static final Duration TOKEN_REUSE_WINDOW = Duration.ofMinutes(50);

  private final ApnsProperties props;
  private final PrivateKey signingKey;
  private final Clock clock;

  private String cachedJwt;
  private Instant jwtIssuedAt = Instant.EPOCH;

  public ApnsTokenProvider(ApnsProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
    this.signingKey = props.configured() ? parseKey(props.privateKey()) : null;
  }

  public boolean configured() {
    return signingKey != null;
  }

  public synchronized String token() {
    if (cachedJwt != null && jwtIssuedAt.isAfter(clock.instant().minus(TOKEN_REUSE_WINDOW))) {
      return cachedJwt;
    }
    Instant now = clock.instant();
    String header = b64url("{\"alg\":\"ES256\",\"kid\":\"" + props.keyId() + "\"}");
    String claims =
        b64url("{\"iss\":\"" + props.teamId() + "\",\"iat\":" + now.getEpochSecond() + "}");
    String signingInput = header + "." + claims;
    cachedJwt = signingInput + "." + sign(signingInput);
    jwtIssuedAt = now;
    return cachedJwt;
  }

  private String sign(String input) {
    try {
      Signature signature = Signature.getInstance("SHA256withECDSA");
      signature.initSign(signingKey);
      signature.update(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(derToJose(signature.sign()));
    } catch (Exception e) {
      throw new IllegalStateException("APNs JWT signing failed", e);
    }
  }

  /** SHA256withECDSA returns DER integers; JOSE needs two right-aligned 32-byte coordinates. */
  static byte[] derToJose(byte[] der) {
    int rLength = der[3];
    int rOffset = 4;
    int sLength = der[rOffset + rLength + 1];
    int sOffset = rOffset + rLength + 2;
    byte[] jose = new byte[64];
    copyTrimmed(der, rOffset, rLength, jose, 0);
    copyTrimmed(der, sOffset, sLength, jose, 32);
    return jose;
  }

  private static void copyTrimmed(byte[] src, int offset, int length, byte[] dst, int dstStart) {
    int skip = Math.max(0, length - 32);
    int copy = Math.min(length, 32);
    System.arraycopy(src, offset + skip, dst, dstStart + (32 - copy), copy);
  }

  private static PrivateKey parseKey(String pem) {
    try {
      String base64 =
          pem.replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(base64);
      return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("APNs private key (.p8 PEM) is malformed", e);
    }
  }

  private static String b64url(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
