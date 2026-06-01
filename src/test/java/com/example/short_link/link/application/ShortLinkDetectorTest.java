package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShortLinkDetectorTest {

  private final ShortLinkDetector detector = new ShortLinkDetector("https://kurl.me");

  @Test
  void extractsCodeFromOwnShortLink() {
    assertThat(detector.extractCode("https://kurl.me/abc123")).isEqualTo("abc123");
    assertThat(detector.isShortLink("https://kurl.me/abc123")).isTrue();
  }

  @Test
  void stripsQueryAndFragmentAndTrailingSlash() {
    assertThat(detector.extractCode("https://kurl.me/abc123?post=5")).isEqualTo("abc123");
    assertThat(detector.extractCode("https://kurl.me/abc123#x")).isEqualTo("abc123");
    assertThat(detector.extractCode("https://kurl.me/abc123/")).isEqualTo("abc123");
  }

  @Test
  void rejectsForeignHosts() {
    assertThat(detector.extractCode("https://example.com/abc123")).isNull();
    assertThat(detector.isShortLink("https://example.com/abc123")).isFalse();
  }

  @Test
  void rejectsNonCodePaths() {
    assertThat(detector.extractCode("https://kurl.me/")).isNull(); // no code
    assertThat(detector.extractCode("https://kurl.me/ab")).isNull(); // too short (<3)
    assertThat(detector.extractCode("https://kurl.me/this-code-is-way-too-long")).isNull();
    assertThat(detector.extractCode("https://kurl.me/a/b/c")).isNull(); // nested path
  }

  @Test
  void handlesNull() {
    assertThat(detector.extractCode(null)).isNull();
    assertThat(detector.isShortLink(null)).isFalse();
  }

  @Test
  void toleratesTrailingSlashInBaseUrl() {
    ShortLinkDetector d = new ShortLinkDetector("https://kurl.me/");
    assertThat(d.extractCode("https://kurl.me/abc123")).isEqualTo("abc123");
  }
}
