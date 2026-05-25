package com.example.short_link.link.classifier.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageExtractorTest {

  @Test
  void nullReturnsNull() {
    assertThat(LanguageExtractor.extract(null)).isNull();
  }

  @Test
  void blankReturnsNull() {
    assertThat(LanguageExtractor.extract("")).isNull();
    assertThat(LanguageExtractor.extract("   ")).isNull();
  }

  @Test
  void wildcardReturnsNull() {
    assertThat(LanguageExtractor.extract("*")).isNull();
  }

  @Test
  void invalidFormatReturnsNull() {
    assertThat(LanguageExtractor.extract("not-a-language")).isNull();
  }

  @Test
  void extractsKoreanWithRegion() {
    assertThat(LanguageExtractor.extract("ko-KR,ko;q=0.9,en-US;q=0.8")).isEqualTo("ko-KR");
  }

  @Test
  void stripsQualityFactor() {
    assertThat(LanguageExtractor.extract("en;q=0.8")).isEqualTo("en");
  }

  @Test
  void rejectsTagWithMultipleSubtags() {
    assertThat(LanguageExtractor.extract("zh-Hans-CN")).isNull();
  }

  @Test
  void acceptsBcp47ScriptOrRegion() {
    assertThat(LanguageExtractor.extract("zh-Hant")).isEqualTo("zh-Hant");
  }
}
