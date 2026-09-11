package com.example.short_link.common.pow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** The client-visible proof format: random hex challenge and SHA-256(challenge:nonce). */
@Component
final class PowProof {
  private static final int CHALLENGE_BYTES = 16;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();

  String newChallenge() {
    byte[] bytes = new byte[CHALLENGE_BYTES];
    RANDOM.nextBytes(bytes);
    return HEX.formatHex(bytes);
  }

  boolean matches(String challenge, String nonce, int difficulty) {
    String hash = sha256Hex(challenge + ":" + nonce);
    return hash.startsWith("0".repeat(difficulty));
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HEX.formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }
}
