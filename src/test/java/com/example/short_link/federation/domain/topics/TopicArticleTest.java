package com.example.short_link.federation.domain.topics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicArticleTest {

  @Test
  void aTitleEndingWithASeparatorAndItsSourceLosesThatTail() {
    for (String separator : List.of(" | ", "｜", " - ", " – ", " — ", ": ", " · ", "|", "-", " :")) {
      assertThat(
              TopicArticle.withoutSource(
                  "The bridge reopens" + separator + "Example Daily", "Example Daily"))
          .as("separator [%s]", separator)
          .isEqualTo("The bridge reopens");
    }
    assertThat(TopicArticle.withoutSource("다리가 다시 열린다 | 보기일보", "보기일보")).isEqualTo("다리가 다시 열린다");
    assertThat(TopicArticle.withoutSource("橋がまた開く｜みほん新聞", " みほん新聞 ")).isEqualTo("橋がまた開く");
  }

  @Test
  void theSourceIsComparedAfterNfkcAndWithoutCase() {
    assertThat(TopicArticle.withoutSource("The bridge reopens | EXAMPLE daily", "Example Daily"))
        .isEqualTo("The bridge reopens");
    assertThat(TopicArticle.withoutSource("The bridge reopens | Ｅｘａｍｐｌｅ Ｄａｉｌｙ", "Example Daily"))
        .isEqualTo("The bridge reopens");
    assertThat(TopicArticle.withoutSource("橋がまた開く：ﾐﾎﾝ新聞", "ミホン新聞")).isEqualTo("橋がまた開く");
  }

  @Test
  void onlyTheTailIsCutAndOnlyOnce() {
    assertThat(
            TopicArticle.withoutSource(
                "Example Daily: the bridge | Local | Example Daily", "Example Daily"))
        .isEqualTo("Example Daily: the bridge | Local");
    assertThat(
            TopicArticle.withoutSource(
                "The bridge - Example Daily - Example Daily", "Example Daily"))
        .isEqualTo("The bridge - Example Daily");
  }

  @Test
  void aSourceNameWithNoSeparatorBeforeItStays() {
    for (String title :
        List.of(
            "Read the Example Daily",
            "The bridge reopens (Example Daily)",
            "The bridge reopens | The Example Daily",
            "The bridge reopens | Another Paper",
            "Example Daily | The bridge reopens")) {
      assertThat(TopicArticle.withoutSource(title, "Example Daily")).isEqualTo(title);
    }
  }

  @Test
  void aTitleThatWouldBeLeftEmptyStays() {
    for (String title :
        List.of(
            "Example Daily",
            " Example Daily",
            "| Example Daily",
            " - Example Daily",
            "-Example Daily")) {
      assertThat(TopicArticle.withoutSource(title, "Example Daily")).isEqualTo(title);
    }
  }

  @Test
  void withoutASourceNothingIsCut() {
    assertThat(TopicArticle.withoutSource("The bridge reopens | Example Daily", null))
        .isEqualTo("The bridge reopens | Example Daily");
    assertThat(TopicArticle.withoutSource("The bridge reopens | ", " "))
        .isEqualTo("The bridge reopens | ");
    assertThat(TopicArticle.withoutSource(null, "Example Daily")).isNull();
  }
}
