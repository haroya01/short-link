package com.example.short_link.user.application.write;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** Apple 서명·issuer·audience·만료를 검증하고 nonce를 요청 원문의 SHA-256과 대조한다. */
@Component
public class AppleIdentityVerifier {

  private final JwtDecoder decoder;

  public AppleIdentityVerifier(@Qualifier("appleIdentityDecoder") JwtDecoder decoder) {
    this.decoder = decoder;
  }

  public AppleIdentity verify(String identityToken, String rawNonce) {
    Jwt jwt;
    try {
      jwt = decoder.decode(identityToken);
    } catch (JwtException e) {
      throw new UserException(UserErrorCode.INVALID_APPLE_IDENTITY);
    }
    String nonce = jwt.getClaimAsString("nonce");
    byte[] expected = sha256Hex(rawNonce).getBytes(StandardCharsets.UTF_8);
    if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8), expected)) {
      throw new UserException(UserErrorCode.INVALID_APPLE_IDENTITY);
    }
    return new AppleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"));
  }

  private static String sha256Hex(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
