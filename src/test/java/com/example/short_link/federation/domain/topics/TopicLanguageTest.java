package com.example.short_link.federation.domain.topics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopicLanguageTest {

  private static List<String> stated(int ja, int en, int unstated) {
    List<String> languages = new ArrayList<>();
    languages.addAll(Collections.nCopies(ja, "ja"));
    languages.addAll(Collections.nCopies(en, "en-US"));
    languages.addAll(Collections.nCopies(unstated, null));
    return languages;
  }

  @Test
  void hangulReadsAsKoreanAndKanaAsJapanese() {
    assertThat(TopicLanguage.ofScript("소풍가는날")).isEqualTo("ko");
    assertThat(TopicLanguage.ofScript("やまびこ")).isEqualTo("ja");
    assertThat(TopicLanguage.ofScript("ヤマビコ通信")).isEqualTo("ja");
    assertThat(TopicLanguage.ofScript("tlを歩く")).isEqualTo("ja");
  }

  @Test
  void hangulDecidesBeforeKana() {
    assertThat(TopicLanguage.ofScript("やまびこ 소풍")).isEqualTo("ko");
  }

  @Test
  void latinAndHanAloneNameNoLanguage() {
    assertThat(TopicLanguage.ofScript("harbor")).isNull();
    assertThat(TopicLanguage.ofScript("山彦通信")).isNull();
    assertThat(TopicLanguage.ofScript("2026")).isNull();
    assertThat(TopicLanguage.ofScript("")).isNull();
    assertThat(TopicLanguage.ofScript(null)).isNull();
  }

  @Test
  void aTagsSampleNamesTheLanguageSixInTenStatedPostsAgreeOn() {
    assertThat(TopicLanguage.sampled(stated(12, 8, 0))).isEqualTo("ja");
    assertThat(TopicLanguage.sampled(stated(11, 9, 0))).isNull();
    assertThat(TopicLanguage.sampled(stated(10, 10, 0))).isNull();
  }

  @Test
  void postsThatStateNoLanguageAreLeftOutOfTheShare() {
    assertThat(TopicLanguage.sampled(stated(6, 4, 10))).isEqualTo("ja");
    assertThat(TopicLanguage.sampled(stated(0, 0, 20))).isNull();
    assertThat(TopicLanguage.sampled(List.of())).isNull();
  }

  @Test
  void aSampleCountsRegionsOfOneLanguageTogether() {
    assertThat(TopicLanguage.sampled(List.of("en", "en-US", "en-GB", "ja", "de"))).isEqualTo("en");
    assertThat(TopicLanguage.primary("zh-TW")).isEqualTo("zh");
  }
}
