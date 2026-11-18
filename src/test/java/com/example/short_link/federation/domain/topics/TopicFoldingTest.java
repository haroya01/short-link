package com.example.short_link.federation.domain.topics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicFoldingTest {

  private static Topic tag(String name, String language, int people) {
    return new Topic(name, TopicArticle.NONE, language, people, 0, List.of("a.example"), List.of());
  }

  private static Topic link(String slug, String title, String language, int people) {
    return new Topic(
        null,
        new TopicArticle("https://news.example/" + slug, title, "Example Daily", null),
        language,
        people,
        0,
        List.of("a.example"),
        List.of());
  }

  private static Topic only(List<Topic> folded, String id) {
    return folded.stream().filter(topic -> topic.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void anArticleWhoseTitleNamesATagFoldsUnderItAndLeavesTheList() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                link("a", "The Lantern festival opens tonight", "en", 20),
                tag("lantern", "en", 12),
                link("b", "A new ferry line", "en", 9)));

    assertThat(folded)
        .extracting(Topic::id)
        .containsExactly("link:https://news.example/b", "tag:lantern");
    Topic tag = only(folded, "tag:lantern");
    assertThat(tag.related())
        .extracting(TopicArticle::url)
        .containsExactly("https://news.example/a");
    assertThat(tag.related().getFirst().source()).isEqualTo("Example Daily");
  }

  @Test
  void theTagCountsAsManyPeopleAsItsBiggestFoldedArticle() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("lantern", "en", 12),
                link("a", "Lantern night", "en", 40),
                link("b", "Lantern makers", "en", 7)));

    assertThat(only(folded, "tag:lantern").people()).isEqualTo(40);

    List<Topic> smaller =
        TopicFolding.fold(List.of(tag("lantern", "en", 12), link("b", "Lantern makers", "en", 7)));
    assertThat(only(smaller, "tag:lantern").people()).isEqualTo(12);
  }

  @Test
  void atMostThreeArticlesStayMostPeopleFirstAndTheRestAreGone() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("lantern", "en", 12),
                link("a", "Lantern one", "en", 4),
                link("b", "Lantern two", "en", 9),
                link("c", "Lantern three", "en", 6),
                link("d", "Lantern four", "en", 30)));

    assertThat(folded).extracting(Topic::id).containsExactly("tag:lantern");
    assertThat(folded.getFirst().related())
        .extracting(TopicArticle::url)
        .containsExactly(
            "https://news.example/d", "https://news.example/b", "https://news.example/c");
  }

  @Test
  void aLatinNameNeedsFourLettersAndToStandAsAWord() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("rust", "en", 12),
                tag("art", "en", 12),
                tag("ps5", null, 12),
                link("a", "Frustrated commuters wait", "en", 5),
                link("b", "Rust: a field guide", "en", 6),
                link("c", "The art of waiting", "en", 7),
                link("d", "PS5 restock", "en", 8)));

    assertThat(only(folded, "tag:rust").related())
        .extracting(TopicArticle::url)
        .containsExactly("https://news.example/b");
    assertThat(only(folded, "tag:art").related()).isEmpty();
    assertThat(only(folded, "tag:ps5").related()).isEmpty();
    assertThat(folded)
        .extracting(Topic::id)
        .contains(
            "link:https://news.example/a",
            "link:https://news.example/c",
            "link:https://news.example/d");
  }

  @Test
  void aLatinNameNextToAnotherScriptStillStandsAsAWord() {
    List<Topic> folded =
        TopicFolding.fold(List.of(tag("Yamabiko", "ja", 12), link("a", "新しいyamabikoの切符", "ja", 5)));

    assertThat(only(folded, "tag:yamabiko").related()).hasSize(1);
  }

  @Test
  void aNameInAnotherScriptNeedsTwoCharactersAndNoWordEdges() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("소풍", "ko", 12),
                tag("달", "ko", 12),
                link("a", "봄소풍가는 길", "ko", 5),
                link("b", "달이 뜬 밤", "ko", 6)));

    assertThat(only(folded, "tag:소풍").related())
        .extracting(TopicArticle::url)
        .containsExactly("https://news.example/a");
    assertThat(only(folded, "tag:달").related()).isEmpty();
    assertThat(folded).extracting(Topic::id).contains("link:https://news.example/b");
  }

  @Test
  void whenBothLanguagesAreKnownTheyMustMatch() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("lantern", "en", 12),
                tag("harbor", null, 12),
                link("a", "Lantern の夜", "ja", 5),
                link("b", "Harbor の朝", "ja", 6),
                link("c", "Harbor lights", null, 7)));

    assertThat(only(folded, "tag:lantern").related()).isEmpty();
    assertThat(only(folded, "tag:harbor").related())
        .extracting(TopicArticle::url)
        .containsExactly("https://news.example/c", "https://news.example/b");
    assertThat(folded).extracting(Topic::id).contains("link:https://news.example/a");
  }

  @Test
  void aTagOfUnknownLanguageTakesTheOneItsArticlesAgreeOn() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("harbor", null, 12),
                link("a", "Harbor lights", "en", 9),
                link("b", "Harbor ferries", null, 8),
                link("c", "Harbor market", "en", 7),
                link("d", "Harbor cranes", "en", 6),
                link("e", "Harbor fog", "en", 5)));

    assertThat(only(folded, "tag:harbor").language()).isEqualTo("en");
    assertThat(only(folded, "tag:harbor").related()).hasSize(3);
  }

  @Test
  void aTagWhoseArticlesNameDifferentLanguagesStaysUnknown() {
    List<Topic> twoLanguages =
        TopicFolding.fold(
            List.of(
                tag("harbor", null, 12),
                link("a", "Harbor lights", "en", 9),
                link("b", "Harbor の朝", "ja", 8)));
    List<Topic> oneBelowTheThreeKept =
        TopicFolding.fold(
            List.of(
                tag("harbor", null, 12),
                link("a", "Harbor lights", "en", 9),
                link("b", "Harbor ferries", "en", 8),
                link("c", "Harbor market", "en", 7),
                link("d", "Harbor の朝", "ja", 3)));

    assertThat(only(twoLanguages, "tag:harbor").language()).isNull();
    assertThat(only(oneBelowTheThreeKept, "tag:harbor").language()).isNull();
    assertThat(only(oneBelowTheThreeKept, "tag:harbor").related()).hasSize(3);
  }

  @Test
  void aTagWithNoArticleThatNamesALanguageStaysUnknown() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("harbor", null, 12),
                tag("lantern", null, 9),
                link("a", "Harbor lights", null, 9)));

    assertThat(only(folded, "tag:harbor").language()).isNull();
    assertThat(only(folded, "tag:harbor").related()).hasSize(1);
    assertThat(only(folded, "tag:lantern").language()).isNull();
  }

  @Test
  void aTagThatAlreadyHasALanguageKeepsIt() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("harbor", "de", 12),
                tag("lantern", "en", 9),
                link("a", "Harbor lights", null, 9),
                link("b", "Lantern night", "en", 5)));

    assertThat(only(folded, "tag:harbor").language()).isEqualTo("de");
    assertThat(only(folded, "tag:harbor").related()).hasSize(1);
    assertThat(only(folded, "tag:lantern").language()).isEqualTo("en");
  }

  @Test
  void anArticleNamingTwoTagsFoldsUnderTheOneMorePeopleShare() {
    List<Topic> folded =
        TopicFolding.fold(
            List.of(
                tag("lantern", "en", 12),
                tag("harbor", "en", 30),
                link("a", "Lantern night at the harbor", "en", 5)));

    assertThat(only(folded, "tag:harbor").related()).hasSize(1);
    assertThat(only(folded, "tag:lantern").related()).isEmpty();
  }
}
