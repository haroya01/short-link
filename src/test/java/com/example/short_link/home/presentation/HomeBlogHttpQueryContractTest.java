package com.example.short_link.home.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class HomeBlogHttpQueryContractTest extends AccountHttpJourneySupport {

  private long post(UserEntity author, String audience, Instant at) {
    String slug = "bl-" + UUID.randomUUID().toString().substring(0, 8);
    LocalDateTime when = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, 500, 'ko')",
        author.getId(),
        slug,
        slug,
        when,
        when,
        audience,
        when);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }

  private static List<String> keys(JsonNode page) {
    List<String> keys = new ArrayList<>();
    page.path("items")
        .forEach(
            item ->
                keys.add(
                    item.path("kind").asString() + ":" + item.path("post").path("id").asLong()));
    return keys;
  }

  @Test
  void theBlogTabListsPostsNewestFirstAtAnyAgeAndBringsWhatCameAboveTheMarker() throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    long old = post(stranger, "PUBLIC", now.minus(60, ChronoUnit.DAYS));
    long tieFirst = post(stranger, "PUBLIC", now.minusSeconds(600));
    long tieSecond = post(owner, "PUBLIC", now.minusSeconds(600));
    post(stranger, "UNLISTED", now.minusSeconds(300));

    JsonNode first =
        body(
            call(
                "home-blog-first",
                "GET",
                "/api/v1/public/home?tab=blog&limit=2&seen=hide",
                null,
                null,
                200));
    assertThat(keys(first)).containsExactly("post:" + tieSecond, "post:" + tieFirst);
    assertThat(first.path("next").asString()).startsWith("b");

    JsonNode onward =
        body(
            call(
                "home-blog-next",
                "GET",
                "/api/v1/public/home?tab=blog&limit=2&cursor=" + first.path("next").asString(),
                null,
                token,
                200));
    assertThat(keys(onward)).containsExactly("post:" + old);
    assertThat(onward.path("next").isNull()).isTrue();

    long arrived = post(stranger, "PUBLIC", now.plusSeconds(1));
    JsonNode refreshed =
        body(
            call(
                "home-blog-since",
                "GET",
                "/api/v1/public/home?tab=blog&limit=2&since=" + first.path("newest").asString(),
                null,
                token,
                200));
    assertThat(keys(refreshed)).containsExactly("post:" + arrived);
    assertThat(refreshed.path("gap").asBoolean()).isFalse();
  }
}
