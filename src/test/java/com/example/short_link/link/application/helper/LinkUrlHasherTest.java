package com.example.short_link.link.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import org.junit.jupiter.api.Test;

class LinkUrlHasherTest {

  @Test
  void sha256PrefixIsShortHexOnly() {
    String hash = LinkUrlHasher.sha256Prefix("https://phish.example/abc?token=secret");
    assertThat(hash).matches("[0-9a-f]{16}");
    assertThat(hash).doesNotContain("phish.example").doesNotContain("secret");
  }

  @Test
  void nullUrlStillProducesFingerprint() {
    assertThat(LinkUrlHasher.sha256Prefix(null)).matches("[0-9a-f]{16}");
  }

  @Test
  void maliciousUrlExceptionFromHasherHidesRawUrl() {
    String raw = "https://phish.example/abc?token=secret";
    LinkException e =
        new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(raw));
    assertThat(e.getMessage())
        .doesNotContain("phish.example")
        .doesNotContain("secret")
        .matches("malicious url rejected \\(sha256_prefix=[0-9a-f]{16}\\)");
  }
}
