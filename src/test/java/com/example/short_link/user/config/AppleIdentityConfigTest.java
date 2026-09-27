package com.example.short_link.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.application.properties.AppleSignInProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class AppleIdentityConfigTest {

  private static final String ISSUER = "https://appleid.apple.com";
  private final OAuth2TokenValidator<Jwt> validator =
      AppleIdentityConfig.acceptedClaims(
          new AppleSignInProperties(ISSUER, null, List.of("native-app", "web-app")));

  @Test
  void acceptsAnyConfiguredClientWithoutDroppingIssuerOrExpirationChecks() {
    assertThat(
            validator
                .validate(
                    token(
                        ISSUER,
                        List.of("another-client", "web-app"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().minusSeconds(1)))
                .hasErrors())
        .isFalse();
    assertThat(
            validator
                .validate(
                    token(
                        "https://other.example",
                        List.of("native-app"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().minusSeconds(1)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(
                    token(
                        ISSUER,
                        List.of("unregistered-client"),
                        Instant.now().plusSeconds(3600),
                        Instant.now().minusSeconds(1)))
                .hasErrors())
        .isTrue();
  }

  @Test
  void rejectsExpiredAndNotYetValidTokens() {
    assertThat(
            validator
                .validate(
                    token(
                        ISSUER,
                        List.of("native-app"),
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(7200)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(
                    token(
                        ISSUER,
                        List.of("native-app"),
                        Instant.now().plusSeconds(7200),
                        Instant.now().plusSeconds(3600)))
                .hasErrors())
        .isTrue();
  }

  private Jwt token(String issuer, List<String> audience, Instant expiry, Instant notBefore) {
    return Jwt.withTokenValue("fixture")
        .header("alg", "RS256")
        .subject("apple-user")
        .issuer(issuer)
        .audience(audience)
        .expiresAt(expiry)
        .notBefore(notBefore)
        .build();
  }
}
