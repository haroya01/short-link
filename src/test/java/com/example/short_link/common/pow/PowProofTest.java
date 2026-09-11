package com.example.short_link.common.pow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PowProofTest {
  private final PowProof proof = new PowProof();

  @ParameterizedTest
  @CsvSource({"0123456789abcdef0123456789abcdef,151", "deadbeefcafe,102", "한글-challenge,420"})
  void fixedClientProofsUseUtf8ColonSeparationAndHexZeroDifficulty(String challenge, String nonce) {
    // Independently computed SHA-256 prefixes are 004083, 009e9d and 00a943 respectively.
    assertThat(proof.matches(challenge, nonce, 2)).isTrue();
    assertThat(proof.matches(challenge, nonce, 3)).isFalse();
  }
}
