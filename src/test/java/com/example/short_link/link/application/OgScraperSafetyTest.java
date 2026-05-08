package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OgScraperSafetyTest {

  @Test
  void rejectsLoopback() {
    assertThat(OgScraper.isPublicHttpUrl("http://127.0.0.1/x")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("http://localhost/")).isFalse();
  }

  @Test
  void rejectsPrivateRanges() {
    assertThat(OgScraper.isPublicHttpUrl("http://10.0.0.1/")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("http://192.168.1.1/")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("http://172.16.0.1/")).isFalse();
  }

  @Test
  void rejectsLinkLocalAndMetadata() {
    assertThat(OgScraper.isPublicHttpUrl("http://169.254.169.254/latest/meta-data")).isFalse();
  }

  @Test
  void rejectsNonHttpSchemes() {
    assertThat(OgScraper.isPublicHttpUrl("file:///etc/passwd")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("ftp://example.com/x")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("javascript:alert(1)")).isFalse();
  }

  @Test
  void acceptsPublicHosts() {
    assertThat(OgScraper.isPublicHttpUrl("https://www.google.com/")).isTrue();
    assertThat(OgScraper.isPublicHttpUrl("https://github.com/haroya01")).isTrue();
  }

  @Test
  void rejectsMalformed() {
    assertThat(OgScraper.isPublicHttpUrl(null)).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("")).isFalse();
    assertThat(OgScraper.isPublicHttpUrl("not a url")).isFalse();
  }
}
