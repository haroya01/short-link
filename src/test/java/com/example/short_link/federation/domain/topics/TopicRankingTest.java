package com.example.short_link.federation.domain.topics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TopicRankingTest {

  private static Topic tag(String name, String language, int people, int place) {
    return new Topic(
        name, TopicArticle.NONE, language, people, place, List.of("a.example"), List.of());
  }

  private static List<Topic> many(String prefix, String language, int count, int topPeople) {
    List<Topic> topics = new ArrayList<>();
    for (int n = 0; n < count; n++) {
      topics.add(tag("%s%02d".formatted(prefix, n), language, topPeople - n, 0));
    }
    return topics;
  }

  private static List<String> names(List<Topic> topics) {
    return topics.stream().map(Topic::tag).toList();
  }

  @Test
  void aSmallLanguageKeepsItsTopFifteenBesideTheTopFortyOverall() {
    List<Topic> all = new ArrayList<>(many("en", "en", 60, 1_000));
    all.addAll(many("ko", "ko", 20, 30));

    List<Topic> pool = TopicRanking.pool(all);

    assertThat(names(pool).stream().filter(name -> name.startsWith("en"))).hasSize(40);
    assertThat(names(pool)).contains("en39").doesNotContain("en40");
    assertThat(names(pool).stream().filter(name -> name.startsWith("ko"))).hasSize(15);
    assertThat(names(pool)).contains("ko14").doesNotContain("ko15");
    assertThat(pool).hasSize(55);
  }

  @Test
  void aTopicOfNoKnownLanguageEntersOnlyThroughTheTopForty() {
    List<Topic> all = new ArrayList<>(many("en", "en", 45, 1_000));
    all.addAll(many("unknown", null, 5, 10));
    all.add(tag("loud", null, 2_000, 0));

    List<Topic> pool = TopicRanking.pool(all);

    assertThat(names(pool)).contains("loud").doesNotContain("unknown00");
    assertThat(names(pool).getFirst()).isEqualTo("loud");
  }

  @Test
  void thePoolIsOrderedByPeopleThenBestPlaceThenId() {
    List<Topic> pool =
        TopicRanking.pool(
            List.of(
                tag("b", "en", 5, 3),
                tag("c", "en", 9, 7),
                tag("a", "en", 5, 3),
                tag("d", "en", 5, 1)));

    assertThat(names(pool)).containsExactly("c", "d", "a", "b");
  }

  @Test
  void aReaderSeesTheirOwnLanguagesFirstThenTheRestInTheSameOrder() {
    List<Topic> pool =
        TopicRanking.pool(
            List.of(
                tag("big", "en", 90, 0),
                tag("소풍", "ko", 4, 2),
                tag("やまびこ", "ja", 50, 0),
                tag("기차역", "ko", 9, 1),
                tag("mystery", null, 70, 0)));

    assertThat(names(TopicRanking.forReader(pool, Set.of("ko"), 20)))
        .containsExactly("기차역", "소풍", "big", "mystery", "やまびこ");
    assertThat(names(TopicRanking.forReader(pool, Set.of("ko", "ja"), 20)))
        .containsExactly("やまびこ", "기차역", "소풍", "big", "mystery");
    assertThat(names(TopicRanking.forReader(pool, Set.of(), 20)))
        .containsExactly("big", "mystery", "やまびこ", "기차역", "소풍");
  }

  @Test
  void aReaderGetsBetweenOneAndFortyTopics() {
    List<Topic> pool = TopicRanking.pool(many("en", "en", 60, 1_000));

    assertThat(TopicRanking.forReader(pool, Set.of(), 100)).hasSize(40);
    assertThat(TopicRanking.forReader(pool, Set.of(), 0)).hasSize(1);
    assertThat(TopicRanking.forReader(pool, Set.of(), 7)).hasSize(7);
    assertThat(TopicRanking.forReader(List.of(), Set.of("en"), 20)).isEmpty();
  }

  @Test
  void ownIsALanguageTheReaderHasAndNeverAnUnknownOne() {
    assertThat(TopicRanking.own(tag("a", "ko", 3, 0), Set.of("ko"))).isTrue();
    assertThat(TopicRanking.own(tag("a", "ja", 3, 0), Set.of("ko"))).isFalse();
    assertThat(TopicRanking.own(tag("a", null, 3, 0), Set.of("ko"))).isFalse();
  }
}
