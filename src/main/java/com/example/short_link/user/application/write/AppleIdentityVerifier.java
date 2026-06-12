package com.example.short_link.user.application.write;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.properties.AppleSignInProperties;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies a Sign in with Apple identity token from the native app. The token is an Apple-signed
 * JWT: signature comes from Apple's JWKS (Nimbus caches the key set), issuer/audience/expiry run
 * through the standard validators, and the nonce claim must echo the SHA-256 of the raw nonce the
 * app generated for this attempt — a token lifted from some other session dies here.
 */
@Component
public class AppleIdentityVerifier {

  private final JwtDecoder decoder;

  @Autowired
  public AppleIdentityVerifier(AppleSignInProperties props) {
    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
    // setJwtValidator replaces the default chain — keep the timestamp check in explicitly.
    nimbus.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtIssuerValidator(props.issuer()),
            audienceValidator(props.clientIds())));
    this.decoder = nimbus;
  }

  AppleIdentityVerifier(JwtDecoder decoder) {
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

  private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> clientIds) {
    return jwt ->
        jwt.getAudience().stream().anyMatch(clientIds::contains)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "audience is not an accepted client id", null));
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
