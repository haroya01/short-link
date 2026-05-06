package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaliciousUrlExceptionTest {

  @Test
  void messageHidesRawUrlAndExposesShortHash() {
    MaliciousUrlException e = new MaliciousUrlException("https://phish.example/abc?token=secret");

    assertThat(e.getMessage())
        .doesNotContain("phish.example")
        .doesNotContain("secret")
        .matches("malicious url rejected \\(sha256_prefix=[0-9a-f]{16}\\)");
  }

  @Test
  void handlesNullWithoutThrowing() {
    MaliciousUrlException e = new MaliciousUrlException(null);

    assertThat(e.getMessage()).matches("malicious url rejected \\(sha256_prefix=[0-9a-f]{16}\\)");
  }
}
