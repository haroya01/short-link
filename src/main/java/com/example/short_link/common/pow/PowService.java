package com.example.short_link.common.pow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Hash-cash style proof-of-work for anonymous endpoints. The server hands out a random challenge;
 * the client must find a {@code nonce} such that {@code SHA-256(challenge:nonce)} starts with
 * {@code difficulty} hex zeros. Each challenge is single-use (deleted on verify) with a 5-minute
 * TTL. Invalid proofs never consume an outstanding challenge.
 */
@Service
@RequiredArgsConstructor
public class PowService {

  private final PowProof proof;
  private final PowChallengeStore challenges;
  private final PowMetrics metrics;
  private final PowProperties properties;

  public boolean isEnforced() {
    return properties.enforce();
  }

  public Challenge issue() {
    String challenge = proof.newChallenge();
    challenges.remember(challenge);
    metrics.challengeIssued();
    return new Challenge(challenge, properties.difficulty());
  }

  /**
   * Verifies and consumes the challenge. Returns true only if the challenge was issued by this
   * cluster, hasn't been used yet, and the supplied nonce produces a hash with at least {@code
   * difficulty} hex zeros. The challenge is deleted on success — same proof can't be replayed.
   */
  public boolean verifyAndConsume(String challenge, String nonce) {
    VerificationResult result = verifyThenConsume(challenge, nonce);
    metrics.verificationCompleted(result);
    return result == VerificationResult.ACCEPTED;
  }

  private VerificationResult verifyThenConsume(String challenge, String nonce) {
    if (challenge == null || nonce == null || challenge.isBlank() || nonce.isBlank()) {
      return VerificationResult.MISSING;
    }
    // Check the proof before storage: garbage must not invalidate another client's challenge.
    if (!proof.matches(challenge, nonce, properties.difficulty())) {
      return VerificationResult.BAD_PROOF;
    }
    if (!challenges.consume(challenge)) {
      return VerificationResult.UNKNOWN_OR_USED;
    }
    return VerificationResult.ACCEPTED;
  }

  enum VerificationResult {
    MISSING,
    BAD_PROOF,
    UNKNOWN_OR_USED,
    ACCEPTED
  }

  public record Challenge(String challenge, int difficulty) {}
}
