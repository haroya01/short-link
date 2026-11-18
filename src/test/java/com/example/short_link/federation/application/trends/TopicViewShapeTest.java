package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicArticle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TopicViewShapeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final List<String> KEYS =
      List.of(
          "id",
          "kind",
          "tag",
          "url",
          "title",
          "source",
          "image",
          "language",
          "own",
          "people",
          "servers",
          "related");

  private static List<String> keys(JsonNode node) {
    return new ArrayList<>(node.propertyNames());
  }

  @Test
  void aTagTopicCarriesEveryKeyWithItsArticleKeysEmptyAndItsRelatedArticles() {
    Topic tag =
        new Topic(
            "Harbor",
            TopicArticle.NONE,
            "ja",
            189,
            0,
            List.of("b.example", "a.example"),
            List.of(
                new TopicArticle(
                    "https://news.example/a",
                    "Harbor lights",
                    "Example Daily",
                    "https://img.example/a.png")));

    JsonNode json = JSON.valueToTree(TopicView.of(tag, Set.of("ja")));

    assertThat(keys(json)).containsExactlyElementsOf(KEYS);
    assertThat(json.path("id").asString()).isEqualTo("tag:harbor");
    assertThat(json.path("kind").asString()).isEqualTo("tag");
    assertThat(json.path("tag").asString()).isEqualTo("Harbor");
    for (String key : List.of("url", "title", "source", "image")) {
      assertThat(json.path(key).isNull()).as(key).isTrue();
    }
    assertThat(json.path("language").asString()).isEqualTo("ja");
    assertThat(json.path("own").asBoolean()).isTrue();
    assertThat(json.path("people").asInt()).isEqualTo(189);
    assertThat(json.path("servers").toString()).isEqualTo("[\"b.example\",\"a.example\"]");
    assertThat(keys(json.path("related").get(0)))
        .containsExactly("url", "title", "source", "image");
    assertThat(json.path("related").get(0).path("source").asString()).isEqualTo("Example Daily");
  }

  @Test
  void aLinkTopicCarriesTheSameKeysWithNoTagAndAnUnknownLanguageAsNull() {
    Topic link =
        new Topic(
            null,
            new TopicArticle("https://news.example/a", "A new ferry line", "Example Daily", null),
            null,
            14,
            3,
            List.of("a.example"),
            List.of());

    JsonNode json = JSON.valueToTree(TopicView.of(link, Set.of("ko")));

    assertThat(keys(json)).containsExactlyElementsOf(KEYS);
    assertThat(json.path("id").asString()).isEqualTo("link:https://news.example/a");
    assertThat(json.path("kind").asString()).isEqualTo("link");
    assertThat(json.path("tag").isNull()).isTrue();
    assertThat(json.path("url").asString()).isEqualTo("https://news.example/a");
    assertThat(json.path("title").asString()).isEqualTo("A new ferry line");
    assertThat(json.path("source").asString()).isEqualTo("Example Daily");
    assertThat(json.path("image").isNull()).isTrue();
    assertThat(json.path("language").isNull()).isTrue();
    assertThat(json.path("own").asBoolean()).isFalse();
    assertThat(json.path("related").isEmpty()).isTrue();
  }

  @Test
  void aPageOfPostsNamesItsTopicItsItemsAndWhereToGoOn() {
    JsonNode page =
        JSON.valueToTree(
            new RemoteTopicFeed.TopicPosts(
                null, List.of(new RemoteTrendingFeed.KeptNote(List.of(), "https://x/1")), "t20"));

    assertThat(keys(page)).containsExactly("topic", "items", "next");
    assertThat(page.path("topic").isNull()).isTrue();
    assertThat(keys(JSON.valueToTree(new RemoteTopicFeed.TopicList(List.of()))))
        .containsExactly("items");
  }
}
