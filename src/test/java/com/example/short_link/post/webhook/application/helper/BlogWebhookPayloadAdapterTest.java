package com.example.short_link.post.webhook.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlogWebhookPayloadAdapterTest {

  private static final Instant AT = Instant.parse("2026-06-04T10:00:00Z");

  private static BlogInteractionEvent like() {
    return BlogInteractionEvent.like(1L, 2L, 42L, "hello-world", "Hello World", AT);
  }

  private static BlogInteractionEvent follow() {
    return BlogInteractionEvent.follow(1L, 2L, AT);
  }

  private static BlogInteractionEvent seriesSubscribe() {
    return BlogInteractionEvent.seriesSubscribe(1L, 2L, 7L, "My Series", AT);
  }

  @Test
  void eventTypeIsLowercase() {
    assertThat(BlogWebhookPayloadAdapter.eventType(BlogInteractionType.SERIES_SUBSCRIBE))
        .isEqualTo("series_subscribe");
  }

  @Test
  void genericLikeCarriesActorAndPost() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.GENERIC, like(), "alice");
    assertThat(body.get("type")).isEqualTo("like");
    assertThat(body.get("actor")).isEqualTo("alice");
    assertThat(body.get("occurredAt")).isEqualTo(AT.toString());
    @SuppressWarnings("unchecked")
    Map<String, Object> post = (Map<String, Object>) body.get("post");
    assertThat(post.get("id")).isEqualTo(42L);
    assertThat(post.get("title")).isEqualTo("Hello World");
    assertThat(body).doesNotContainKey("series");
  }

  @Test
  void genericFollowHasNoPostOrSeries() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.GENERIC, follow(), "bob");
    assertThat(body.get("type")).isEqualTo("follow");
    assertThat(body).doesNotContainKey("post").doesNotContainKey("series");
  }

  @Test
  void genericSeriesSubscribeCarriesSeries() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.GENERIC, seriesSubscribe(), "carol");
    @SuppressWarnings("unchecked")
    Map<String, Object> series = (Map<String, Object>) body.get("series");
    assertThat(series.get("id")).isEqualTo(7L);
    assertThat(series.get("title")).isEqualTo("My Series");
  }

  @Test
  void discordWrapsSummaryInEmbed() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.DISCORD, like(), "alice");
    assertThat(body.get("username")).isEqualTo("kurl");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
    assertThat(embeds).hasSize(1);
    assertThat(embeds.get(0).get("title")).isEqualTo("New like");
    assertThat((String) embeds.get(0).get("description")).contains("@alice", "Hello World");
    assertThat(embeds.get(0).get("color")).isEqualTo(0x059669);
  }

  @Test
  void slackSendsPlainText() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, seriesSubscribe(), "carol");
    assertThat((String) body.get("text")).contains("@carol", "My Series");
  }

  @Test
  void summaryCoversEveryInteractionType() {
    assertThat(
            (String)
                BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, like(), "a").get("text"))
        .contains("liked");
    assertThat(
            (String)
                BlogWebhookPayloadAdapter.build(
                        BlogWebhookFormat.SLACK,
                        BlogInteractionEvent.comment(1L, 2L, 42L, "s", "T", AT),
                        "a")
                    .get("text"))
        .contains("commented");
    assertThat(
            (String)
                BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, follow(), "a").get("text"))
        .contains("following");
    assertThat(
            (String)
                BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, seriesSubscribe(), "a")
                    .get("text"))
        .contains("subscribed");
  }

  @Test
  void missingActorFallsBackToSomeone() {
    Map<String, Object> body =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, follow(), null);
    assertThat((String) body.get("text")).contains("@someone");
  }

  @Test
  void discordTitleCoversEveryInteractionType() {
    record Case(BlogInteractionEvent ev, String title) {}
    List<Case> cases =
        List.of(
            new Case(like(), "New like"),
            new Case(BlogInteractionEvent.comment(1L, 2L, 42L, "s", "T", AT), "New comment"),
            new Case(follow(), "New follower"),
            new Case(seriesSubscribe(), "New series subscriber"));
    for (Case c : cases) {
      Map<String, Object> body =
          BlogWebhookPayloadAdapter.build(BlogWebhookFormat.DISCORD, c.ev(), "a");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
      assertThat(embeds.get(0).get("title")).isEqualTo(c.title());
    }
  }

  @Test
  void nullTitlesRenderAsEmptyString() {
    BlogInteractionEvent likeNoTitle = BlogInteractionEvent.like(1L, 2L, 42L, null, null, AT);
    // generic carries the empty title; summary's safe() renders "" rather than "null".
    Map<String, Object> generic =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.GENERIC, likeNoTitle, "a");
    @SuppressWarnings("unchecked")
    Map<String, Object> post = (Map<String, Object>) generic.get("post");
    assertThat(post.get("title")).isEqualTo("");
    Map<String, Object> slack =
        BlogWebhookPayloadAdapter.build(BlogWebhookFormat.SLACK, likeNoTitle, "a");
    assertThat((String) slack.get("text")).doesNotContain("null");
  }
}
