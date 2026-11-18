package com.example.short_link.federation.domain.topics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicMergeTest {

  private static TopicArticle article(String url, String title) {
    return new TopicArticle(url, title, "Example Daily", null);
  }

  private static TopicSighting link(String host, int place, String url, String title, int people) {
    return TopicSighting.link(host, place, article(url, title), "en", people);
  }

  @Test
  void aTagSeenOnSeveralServersIsOneTopicNamedAsTheFirstServerWroteIt() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                TopicSighting.tag("a.example", 4, "Harbor", 5),
                TopicSighting.tag("b.example", 1, "harbor", 9),
                TopicSighting.tag("c.example", 2, "HARBOR", 3)));

    assertThat(topics).hasSize(1);
    Topic topic = topics.getFirst();
    assertThat(topic.id()).isEqualTo("tag:harbor");
    assertThat(topic.tag()).isEqualTo("Harbor");
    assertThat(topic.servers()).containsExactly("b.example", "c.example", "a.example");
    assertThat(topic.place()).isEqualTo(1);
  }

  @Test
  void peopleIsTheLargestAnySingleServerReportedNotTheSum() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                TopicSighting.tag("a.example", 0, "lantern", 2),
                TopicSighting.tag("b.example", 0, "lantern", 2),
                TopicSighting.tag("a.example", 1, "harbor", 4),
                TopicSighting.tag("b.example", 1, "harbor", 7)));

    assertThat(topics).extracting(Topic::id).containsExactly("tag:harbor");
    assertThat(topics.getFirst().people()).isEqualTo(7);
  }

  @Test
  void aTopicNeedsThreePeople() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                TopicSighting.tag("a.example", 0, "two", 2),
                TopicSighting.tag("a.example", 1, "three", 3),
                link("a.example", 0, "https://news.example/two", "Two readers", 2),
                link("a.example", 1, "https://news.example/three", "Three readers", 3)));

    assertThat(topics)
        .extracting(Topic::id)
        .containsExactly("tag:three", "link:https://news.example/three");
  }

  @Test
  void aLinkSeenOnSeveralServersIsOneTopicByItsAddress() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                link("a.example", 3, "https://news.example/a", "The bridge reopens", 4),
                link("b.example", 0, "https://news.example/a", "The bridge reopens", 6),
                link("b.example", 1, "https://news.example/b", "A new ferry line", 5)));

    assertThat(topics)
        .extracting(Topic::id)
        .containsExactly("link:https://news.example/a", "link:https://news.example/b");
    assertThat(topics.getFirst().people()).isEqualTo(6);
    assertThat(topics.getFirst().servers()).containsExactly("b.example", "a.example");
    assertThat(topics.getFirst().tag()).isNull();
  }

  @Test
  void linksWithTheSameTitleBecomeTheOneMorePeopleShare() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                link("a.example", 2, "https://news.example/amp/a", "The Bridge  reopens ", 4),
                link("b.example", 5, "https://news.example/a", "the bridge reopens", 11),
                link("c.example", 0, "https://mirror.example/a", "The bridge reopens", 3),
                link("a.example", 6, "https://news.example/b", "The bridge reopened", 8)));

    assertThat(topics)
        .extracting(Topic::id)
        .containsExactly("link:https://news.example/a", "link:https://news.example/b");
    Topic merged = topics.getFirst();
    assertThat(merged.article().title()).isEqualTo("the bridge reopens");
    assertThat(merged.people()).isEqualTo(11);
    assertThat(merged.place()).isZero();
    assertThat(merged.servers()).containsExactly("c.example", "a.example", "b.example");
  }

  @Test
  void aLinkThatOnlyOneCopyNamesALanguageForKeepsThatLanguage() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                TopicSighting.link(
                    "a.example", 0, article("https://news.example/a", "Ferry"), null, 5),
                TopicSighting.link(
                    "b.example", 0, article("https://news.example/a", "Ferry"), "en", 4)));

    assertThat(topics.getFirst().language()).isEqualTo("en");
  }

  @Test
  void aTagAndALinkNeverMergeEvenWhenTheyReadTheSame() {
    List<Topic> topics =
        TopicMerge.merge(
            List.of(
                TopicSighting.tag("a.example", 0, "ferry", 5),
                link("a.example", 0, "https://news.example/ferry", "ferry", 5)));

    assertThat(topics).hasSize(2);
  }
}
