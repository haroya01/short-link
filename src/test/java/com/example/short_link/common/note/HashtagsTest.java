package com.example.short_link.common.note;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HashtagsTest {

  @Test
  void tagsAreWordsAfterAHashInAnyScript() {
    assertThat(Hashtags.of("오늘 #스프링 공부, #日本語 と #हिन्दी 그리고 #snake_case."))
        .containsExactly("스프링", "日本語", "हिन्दी", "snake_case");
  }

  @Test
  void aHashInsideAWordAUrlOrANumberIsNotATag() {
    assertThat(Hashtags.of("a#b https://x.com/#/route https://x.com/p#frag (#123) =#x")).isEmpty();
  }

  @Test
  void theFirstSpellingWinsAndCaseDoesNotRepeatATag() {
    assertThat(Hashtags.of("#Spring #spring #SPRING #boot")).containsExactly("Spring", "boot");
  }

  @Test
  void aTrailingSeparatorIsNotPartOfTheTag() {
    assertThat(Hashtags.of("#kurl· 다음")).containsExactly("kurl");
  }

  @Test
  void tagsLongerThanTheLimitAreSkippedAndANoteKeepsAtMostTen() {
    assertThat(Hashtags.of("#" + "a".repeat(41) + " #ok")).containsExactly("ok");
    String many = IntStream.range(0, 12).mapToObj(i -> "#t" + i).collect(Collectors.joining(" "));
    assertThat(Hashtags.of(many)).hasSize(Hashtags.MAX_TAGS).startsWith("t0").endsWith("t9");
  }

  @Test
  void theSameTagsInADifferentOrderOrCaseAreTheSameSet() {
    assertThat(Hashtags.sameTags("#a #B", "#b and #A")).isTrue();
    assertThat(Hashtags.sameTags("#a", "#a #c")).isFalse();
    assertThat(Hashtags.of(null)).isEmpty();
  }
}
