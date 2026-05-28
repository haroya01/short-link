package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkTextTest {

  @Test
  void normalizeTrimsBlanksAndCapsLength() {
    assertThat(LinkText.normalize("  hello  ", 10)).isEqualTo("hello");
    assertThat(LinkText.normalize("abcdef", 3)).isEqualTo("abc");
    assertThat(LinkText.normalize("   ", 10)).isNull();
    assertThat(LinkText.normalize(null, 10)).isNull();
  }

  @Test
  void firstNonBlankPrefersOverride() {
    assertThat(LinkText.firstNonBlank(" override ", "fallback")).isEqualTo(" override ");
    assertThat(LinkText.firstNonBlank("   ", "fallback")).isEqualTo("fallback");
    assertThat(LinkText.firstNonBlank(null, "fallback")).isEqualTo("fallback");
  }
}
