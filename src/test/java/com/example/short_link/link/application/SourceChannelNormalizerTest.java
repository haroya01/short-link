package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceChannelNormalizerTest {

  @Test
  void trimsAndLowercasesAllowedTokens() {
    assertThat(SourceChannelNormalizer.normalize("  KAKAO  ")).isEqualTo("kakao");
    assertThat(SourceChannelNormalizer.normalize("Bio_v2")).isEqualTo("bio_v2");
    assertThat(SourceChannelNormalizer.normalize("offline-poster.1")).isEqualTo("offline-poster.1");
  }

  @Test
  void rejectsBlankAndNull() {
    assertThat(SourceChannelNormalizer.normalize(null)).isNull();
    assertThat(SourceChannelNormalizer.normalize("")).isNull();
    assertThat(SourceChannelNormalizer.normalize("   ")).isNull();
  }

  @Test
  void rejectsDangerousChars() {
    assertThat(SourceChannelNormalizer.normalize("<script>")).isNull();
    assertThat(SourceChannelNormalizer.normalize("a b")).isNull();
    assertThat(SourceChannelNormalizer.normalize("a/b")).isNull();
    assertThat(SourceChannelNormalizer.normalize("\"sql\"")).isNull();
  }

  @Test
  void truncatesOver40ThenAcceptsIfStillValid() {
    String long41 = "a".repeat(41);
    assertThat(SourceChannelNormalizer.normalize(long41)).isEqualTo("a".repeat(40));
  }
}
