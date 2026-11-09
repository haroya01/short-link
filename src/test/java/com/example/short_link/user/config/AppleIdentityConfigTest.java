package com.example.short_link.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.example.short_link.user.application.properties.AppleSignInProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

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
  void anAppleKeyServerThatNeverAnswersFailsTheSignInInsteadOfHoldingIt() throws Exception {
    List<Socket> held = new CopyOnWriteArrayList<>();
    try (ServerSocket silent = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      Thread.ofVirtual().start(() -> holdEveryConnection(silent, held));
      JwtDecoder decoder =
          AppleIdentityConfig.decoder(
              new AppleSignInProperties(
                  ISSUER,
                  "http://127.0.0.1:" + silent.getLocalPort() + "/auth/keys",
                  List.of("native-app")),
              Duration.ofMillis(300),
              Duration.ofMillis(500));
      String token = signedToken();

      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class));
    } finally {
      for (Socket socket : held) socket.close();
    }
  }

  private static void holdEveryConnection(ServerSocket silent, List<Socket> held) {
    while (true) {
      try {
        held.add(silent.accept());
      } catch (IOException closed) {
        return;
      }
    }
  }

  private static String signedToken() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("apple-key").generate();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple-key").build(),
            new JWTClaimsSet.Builder().issuer(ISSUER).subject("apple-user").build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
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
