package com.example.short_link.link.redirect.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class VisitorLocaleTest {

  @Test
  void picksTheFirstSupportedLanguageByPreference() {
    assertThat(VisitorLocale.resolve("ja-JP,ja;q=0.9,en-US;q=0.8")).isEqualTo(Locale.JAPANESE);
    assertThat(VisitorLocale.resolve("ko-KR,ko;q=0.9")).isEqualTo(Locale.KOREAN);
    assertThat(VisitorLocale.resolve("fr-FR,fr;q=0.9,ja;q=0.8")).isEqualTo(Locale.JAPANESE);
    assertThat(VisitorLocale.resolve("vi-VN")).isEqualTo(Locale.of("vi"));
    assertThat(VisitorLocale.resolve("hi-IN,en;q=0.5")).isEqualTo(Locale.of("hi"));
  }

  @Test
  void fallsBackToEnglish() {
    assertThat(VisitorLocale.resolve(null)).isEqualTo(Locale.ENGLISH);
    assertThat(VisitorLocale.resolve("  ")).isEqualTo(Locale.ENGLISH);
    assertThat(VisitorLocale.resolve("zh-CN,zh;q=0.9")).isEqualTo(Locale.ENGLISH);
    assertThat(VisitorLocale.resolve("not a header;;;")).isEqualTo(Locale.ENGLISH);
  }
}
