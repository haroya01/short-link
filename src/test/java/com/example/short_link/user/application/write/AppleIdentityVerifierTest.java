package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.exception.UserException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class AppleIdentityVerifierTest {

  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final AppleIdentityVerifier verifier = new AppleIdentityVerifier(decoder);

  private static String sha256Hex(String raw) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
  }

  private static Jwt jwt(String nonceClaim) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("apple-sub-1")
            .claim("email", "relay@privaterelay.appleid.com");
    if (nonceClaim != null) {
      builder.claim("nonce", nonceClaim);
    }
    return builder.build();
  }

  @Test
  void validTokenYieldsSubjectAndEmail() throws Exception {
    when(decoder.decode(anyString())).thenReturn(jwt(sha256Hex("raw-nonce")));

    AppleIdentity identity = verifier.verify("token", "raw-nonce");

    assertThat(identity.subject()).isEqualTo("apple-sub-1");
    assertThat(identity.email()).isEqualTo("relay@privaterelay.appleid.com");
  }

  @Test
  void nonceMismatchIsRejected() throws Exception {
    when(decoder.decode(anyString())).thenReturn(jwt(sha256Hex("someone-elses-nonce")));

    assertThatThrownBy(() -> verifier.verify("token", "raw-nonce"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void missingNonceClaimIsRejected() {
    when(decoder.decode(anyString())).thenReturn(jwt(null));

    assertThatThrownBy(() -> verifier.verify("token", "raw-nonce"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void decoderFailureMapsToUserException() {
    when(decoder.decode(anyString())).thenThrow(new JwtException("bad signature"));

    assertThatThrownBy(() -> verifier.verify("token", "raw-nonce"))
        .isInstanceOf(UserException.class);
  }
}
