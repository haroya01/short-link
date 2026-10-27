package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForYouProfileTest {

  private static ForYouProfile.Signal signal(
      double weight, String author, String lang, String... tags) {
    return new ForYouProfile.Signal(weight, author, lang, List.of(tags));
  }

  @Test
  void tagsAndAuthorsAddUpByWeightAndTagsFoldTheirCase() {
    ForYouReader reader =
        ForYouProfile.of(
            List.of("ko"),
            List.of(
                signal(ForYouProfile.LIKED, "r1", "ko", "Java"),
                signal(ForYouProfile.BOOKMARKED, "r1", "ko", "java", "일상"),
                signal(ForYouProfile.DWELT, null, null)));

    assertThat(reader.tags()).containsEntry("java", 2.5).containsEntry("일상", 1.5);
    assertThat(reader.authors()).containsOnlyKeys("r1").containsEntry("r1", 2.5);
  }

  @Test
  void aLanguageJoinsTheReadersOnceItIsAQuarterOfTheSignalsAndAtLeastTwo() {
    List<ForYouProfile.Signal> signals = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      signals.add(signal(1, "a", "ko"));
    }
    signals.add(signal(1, "b", "ja-JP"));
    signals.add(signal(1, "b", "ja"));
    signals.add(signal(1, "c", "en"));

    assertThat(ForYouProfile.of(List.of("ko"), signals).languages())
        .containsExactlyInAnyOrder("ko");
    signals.add(signal(1, "b", "ja"));
    assertThat(ForYouProfile.of(List.of("ko"), signals).languages())
        .containsExactlyInAnyOrder("ko", "ja");
    assertThat(ForYouProfile.of(List.of(), List.of(signal(1, "c", "en"))).languages()).isEmpty();
  }

  @Test
  void theLanguagesAMemberSetAreKeptWithoutAnySignalInThem() {
    assertThat(ForYouProfile.of(List.of("ko-KR", "*", "JA"), List.of()).languages())
        .containsExactlyInAnyOrder("ko", "ja");
  }

  @Test
  void languageTagsComeDownToTheirFirstPartInLowerCase() {
    assertThat(ForYouProfile.language("ko-KR")).isEqualTo("ko");
    assertThat(ForYouProfile.language(" JA_jp ")).isEqualTo("ja");
    assertThat(ForYouProfile.language("*")).isNull();
    assertThat(ForYouProfile.language(" ")).isNull();
    assertThat(ForYouProfile.language(null)).isNull();
  }

  @Test
  void aBrowsersLanguagesGiveTheFirstTwoItNames() {
    assertThat(ForYouProfile.accepted("ko-KR,ko;q=0.9,en-US;q=0.8,ja;q=0.7"))
        .containsExactly("ko", "en");
    assertThat(ForYouProfile.accepted("*;q=0.5, ja")).containsExactly("ja");
    assertThat(ForYouProfile.accepted(null)).isEmpty();
    assertThat(ForYouProfile.accepted("  ")).isEmpty();
  }
}
