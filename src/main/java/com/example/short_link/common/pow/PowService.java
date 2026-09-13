package com.example.short_link.common.pow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Requires {@code SHA-256(challenge:nonce)} to start with {@code difficulty} hex zeros. Challenges
 * expire after five minutes and can be consumed once; invalid proofs leave them usable.
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
