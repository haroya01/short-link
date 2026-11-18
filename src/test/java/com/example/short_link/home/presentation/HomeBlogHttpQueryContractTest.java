package com.example.short_link.home.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.seen.SeenParameters;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class HomeBlogHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String DEVICE = "5e1f0a2b-7c3d-4e4f-9a5b-6c7d8e9f0a1b";

  @BeforeEach
  void noPostsYet() {
    jdbc.update("DELETE FROM posts");
  }

  private long post(UserEntity author, String audience, Instant at) {
    return post(author, audience, at, "ko");
  }

  private long post(UserEntity author, String audience, Instant at, String language) {
    String slug = "bl-" + UUID.randomUUID().toString().substring(0, 8);
    LocalDateTime when = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, 500, ?)",
        author.getId(),
        slug,
        slug,
        when,
        when,
        audience,
        when,
        language);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }

  private JsonNode blog(String step, String query, Map<String, String> headers) throws Exception {
    return body(
        callWithHeaders(step, "GET", "/api/v1/public/home?tab=blog&" + query, null, headers, 200));
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

  @Test
  void aReaderGetsTheirLanguageFirstThenTheRestAndACursorFromBeforeKeepsTheOneList()
      throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    long ja1 = post(stranger, "PUBLIC", now.minusSeconds(60), "ja");
    long ko1 = post(stranger, "PUBLIC", now.minusSeconds(120), "ko");
    long ja2 = post(owner, "PUBLIC", now.minusSeconds(180), "ja");
    long ko2 = post(stranger, "PUBLIC", now.minusSeconds(240), "ko");
    long en1 = post(stranger, "PUBLIC", now.minusSeconds(300), "en");
    Map<String, String> member = Map.of("Authorization", "Bearer " + token);

    JsonNode first = blog("home-blog-member-first", "limit=1", member);
    assertThat(keys(first)).containsExactly("post:" + ko1);
    assertThat(first.path("next").asString()).startsWith("b1-ko.");
    assertThat(first.path("newest").asString()).startsWith("b1-ko.");

    JsonNode crossing =
        blog("home-blog-crossing", "limit=2&cursor=" + first.path("next").asString(), Map.of());
    assertThat(keys(crossing)).containsExactly("post:" + ko2, "post:" + ja1);
    assertThat(crossing.path("next").asString()).startsWith("b2-ko.");

    JsonNode rest =
        blog("home-blog-rest", "limit=2&cursor=" + crossing.path("next").asString(), member);
    assertThat(keys(rest)).containsExactly("post:" + ja2, "post:" + en1);
    assertThat(rest.path("next").isNull()).isTrue();

    String before =
        "b" + ChronoUnit.MICROS.between(Instant.EPOCH, now.minusSeconds(60)) + "." + ja1;
    JsonNode plain = blog("home-blog-plain-next", "limit=2&cursor=" + before, member);
    assertThat(keys(plain)).containsExactly("post:" + ko1, "post:" + ja2);
    assertThat(plain.path("next").asString()).matches("b\\d+\\.\\d+");

    JsonNode device =
        blog(
            "home-blog-device-first",
            "limit=2",
            Map.of(SeenParameters.DEVICE_HEADER, DEVICE, "Accept-Language", "ja-JP,ja;q=0.9"));
    assertThat(keys(device)).containsExactly("post:" + ja1, "post:" + ja2);
    assertThat(device.path("next").asString()).startsWith("b1-ja.");

    post(stranger, "PUBLIC", now.plusSeconds(1), "ja");
    long arrived = post(stranger, "PUBLIC", now.plusSeconds(2), "ko");
    post(stranger, "PUBLIC", now.plusSeconds(3), "en");
    JsonNode refreshed =
        blog("home-blog-member-since", "limit=2&since=" + first.path("newest").asString(), member);
    assertThat(keys(refreshed)).containsExactly("post:" + arrived);
    assertThat(refreshed.path("newest").asString()).startsWith("b1-ko.");
    assertThat(refreshed.path("gap").asBoolean()).isFalse();
  }
}
