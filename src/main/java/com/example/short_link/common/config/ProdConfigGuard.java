package com.example.short_link.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Missing JWT or 2FA keys abort production startup: ephemeral JWT keys invalidate sessions on
 * restart, and missing 2FA keys store secrets in plaintext. Abuse-risk toggles only warn so
 * operators can disable them deliberately. Reads raw configuration to avoid a common-to-user
 * dependency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProdConfigGuard implements ApplicationRunner {

  private final Environment environment;

  @Value("${short-link.jwt.private-key:}")
  private String jwtPrivateKey;

  @Value("${short-link.jwt.public-key:}")
  private String jwtPublicKey;

  @Value("${short-link.twofa.aes-key:}")
  private String twofaAesKey;

  @Value("${short-link.pow.enforce:true}")
  private boolean powEnforced;

  @Value("${short-link.safe-browsing.enabled:true}")
  private boolean safeBrowsingEnabled;

  @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
  private String googleClientSecret;

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.matchesProfiles("prod")) {
      return;
    }
    if (jwtPrivateKey.isBlank() || jwtPublicKey.isBlank()) {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY / JWT_PUBLIC_KEY must be set in prod — otherwise an ephemeral keypair is"
              + " generated per JVM and every issued token breaks on the next restart.");
    }
    if (twofaAesKey.isBlank()) {
      throw new IllegalStateException(
          "TWOFA_AES_KEY must be set in prod — otherwise TOTP secrets are stored unencrypted.");
    }
    if (!powEnforced) {
      log.warn(
          "POW_ENFORCE is false in prod — anonymous link creation has no proof-of-work gate, only"
              + " the per-IP rate limit. Enable it unless another abuse control is in place.");
    }
    if (!safeBrowsingEnabled) {
      log.warn("SAFE_BROWSING_ENABLED is false in prod — shortened destinations are not screened.");
    }
    if (googleClientSecret.isBlank() || "placeholder".equals(googleClientSecret)) {
      log.warn("GOOGLE_CLIENT_SECRET is unset/placeholder in prod — Google sign-in will fail.");
    }
  }
}
