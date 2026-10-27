package com.example.short_link.common.note;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcerptTest {

  @Test
  void whitespaceFoldsAndShortTextStaysWhole() {
    assertThat(Excerpt.of("  첫 줄\n\n둘째   줄\t끝 ")).isEqualTo("첫 줄 둘째 줄 끝");
    assertThat(Excerpt.of(null)).isEmpty();
    assertThat(Excerpt.of("가".repeat(Excerpt.LENGTH))).isEqualTo("가".repeat(Excerpt.LENGTH));
  }

  @Test
  void longTextStopsAtEightyCodePointsWithoutSplittingOne() {
    String cats = "🐈".repeat(Excerpt.LENGTH + 5);

    String excerpt = Excerpt.of(cats);

    assertThat(excerpt).isEqualTo("🐈".repeat(Excerpt.LENGTH) + "…");
    assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(Excerpt.LENGTH + 1);
  }

  @Test
  void aWarnedNoteIsListedByItsWarningAndABlankWarningHidesNothing() {
    assertThat(Excerpt.of("숨긴 본문", "스포일러")).isEqualTo("스포일러");
    assertThat(Excerpt.of("본문", "   ")).isEqualTo("본문");
    assertThat(Excerpt.of("본문", null)).isEqualTo("본문");
  }
}
