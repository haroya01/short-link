package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShortLinkUrlBuilderTest {

  @Test
  void buildsCanonicalShortUrl() {
    ShortLinkUrlBuilder builder = new ShortLinkUrlBuilder("https://kurl.me");
    assertThat(builder.build("aBc1234")).isEqualTo("https://kurl.me/aBc1234");
  }

  @Test
  void doesNotStripTrailingSlashFromBase() {
    // Caller is responsible for the base shape — builder is a pure concatenation. If a misconfig
    // hands us "https://kurl.me/" we get a double slash, which is the visible signal to fix the
    // base URL config, not to silently paper over it here.
    ShortLinkUrlBuilder builder = new ShortLinkUrlBuilder("https://kurl.me/");
    assertThat(builder.build("xyz")).isEqualTo("https://kurl.me//xyz");
  }

  @Test
  void supportsCustomDomainBase() {
    ShortLinkUrlBuilder builder = new ShortLinkUrlBuilder("https://go.example.com");
    assertThat(builder.build("a")).isEqualTo("https://go.example.com/a");
  }
}
