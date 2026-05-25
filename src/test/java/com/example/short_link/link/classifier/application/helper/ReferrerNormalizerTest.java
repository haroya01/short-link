package com.example.short_link.link.classifier.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferrerNormalizerTest {

  @Test
  void nullReturnsNull() {
    assertThat(ReferrerNormalizer.normalize(null)).isNull();
  }

  @Test
  void blankReturnsNull() {
    assertThat(ReferrerNormalizer.normalize("   ")).isNull();
  }

  @Test
  void invalidUrlReturnsNull() {
    assertThat(ReferrerNormalizer.normalize("not a url at all")).isNull();
  }

  @Test
  void malformedUriReturnsNull() {
    assertThat(ReferrerNormalizer.normalize("https://exa mple.com")).isNull();
  }

  @Test
  void uriWithoutHostReturnsNull() {
    assertThat(ReferrerNormalizer.normalize("mailto:user@example.com")).isNull();
  }

  @Test
  void stripsQueryString() {
    assertThat(ReferrerNormalizer.normalize("https://www.youtube.com/watch?v=xyz&t=10"))
        .isEqualTo("https://www.youtube.com/watch");
  }

  @Test
  void stripsFragment() {
    assertThat(ReferrerNormalizer.normalize("https://example.com/page#section"))
        .isEqualTo("https://example.com/page");
  }

  @Test
  void stripsPiiQueryParams() {
    String result =
        ReferrerNormalizer.normalize("https://partner.com/landing?token=secret&email=a@b.com");
    assertThat(result).isEqualTo("https://partner.com/landing");
  }

  @Test
  void keepsHostAndPath() {
    assertThat(ReferrerNormalizer.normalize("https://www.instagram.com/p/abc"))
        .isEqualTo("https://www.instagram.com/p/abc");
  }

  @Test
  void lowercasesHost() {
    assertThat(ReferrerNormalizer.normalize("https://WWW.Example.COM/Path"))
        .isEqualTo("https://www.example.com/Path");
  }

  @Test
  void collapsesRootPath() {
    assertThat(ReferrerNormalizer.normalize("https://example.com/"))
        .isEqualTo("https://example.com");
  }

  @Test
  void truncatesOverlyLongInput() {
    String longPath = "/" + "a".repeat(1000);
    String result = ReferrerNormalizer.normalize("https://example.com" + longPath);
    assertThat(result).hasSize(512);
  }

  @Test
  void hostOfExtractsLowercaseHost() {
    assertThat(ReferrerNormalizer.hostOf("https://WWW.Example.COM/path?q=1"))
        .isEqualTo("www.example.com");
  }

  @Test
  void hostOfReturnsNullForNullOrBlank() {
    assertThat(ReferrerNormalizer.hostOf(null)).isNull();
    assertThat(ReferrerNormalizer.hostOf("   ")).isNull();
  }

  @Test
  void hostOfReturnsNullForUriWithoutHost() {
    assertThat(ReferrerNormalizer.hostOf("mailto:foo@bar.com")).isNull();
  }

  @Test
  void hostOfReturnsNullForMalformedUri() {
    assertThat(ReferrerNormalizer.hostOf("https://exa mple.com")).isNull();
  }
}
