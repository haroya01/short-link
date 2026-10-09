package com.example.short_link.notification.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordVerdictTest {

  private static KeywordFilter warn(String phrase) {
    return new KeywordFilter(phrase, false, false);
  }

  @Test
  void anyHidingFilterHidesAndOtherwiseAnyMatchWarns() {
    List<KeywordFilter> warns = List.of(warn("스포일러"), warn("결말"));

    assertThat(KeywordVerdict.of(warns, "스포일러 주의: 결말")).isEqualTo(KeywordVerdict.WARN);
    assertThat(
            KeywordVerdict.of(
                List.of(warn("스포일러"), new KeywordFilter("스포일러", false, true)), "스포일러"))
        .isEqualTo(KeywordVerdict.HIDE);
    assertThat(KeywordVerdict.of(warns, "평범한 글")).isEqualTo(KeywordVerdict.CLEAN);
    assertThat(KeywordVerdict.of(warns, null)).isEqualTo(KeywordVerdict.CLEAN);
  }

  @Test
  void matchesLikeTheClientsCaseInsensitivelyAndAWholeWordOnlyOnItsEdges() {
    assertThat(warn("hexagonal").matches("HEXAGONAL ports")).isTrue();
    KeywordFilter cat = new KeywordFilter("cat", true, false);
    assertThat(cat.matches("a cat sat")).isTrue();
    assertThat(cat.matches("concatenate")).isFalse();
    assertThat(new KeywordFilter("고양이", true, false).matches("고양이가")).isFalse();
    assertThat(warn("고양이").matches("고양이가")).isTrue();
    assertThat(warn(".").matches("a.b")).isTrue();
    assertThat(warn("a+b").matches("aab")).isFalse();
  }
}
