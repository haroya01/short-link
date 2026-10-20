package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PostCardTest {

  @Test
  void readingTimeIsFiveHundredCharactersAMinuteAndNeverZero() {
    assertThat(PostCard.readMinutes(0)).isEqualTo(1);
    assertThat(PostCard.readMinutes(749)).isEqualTo(1);
    assertThat(PostCard.readMinutes(750)).isEqualTo(2);
    assertThat(PostCard.readMinutes(5_000)).isEqualTo(10);
  }

  @Test
  void theCardIsThePostItemWithItsReactionBarBesideIt() {
    PublicFeedItem item =
        new PublicFeedItem(
            9L,
            null,
            "slug",
            "Title",
            null,
            null,
            null,
            "ko",
            List.of(),
            Instant.parse("2026-10-20T00:00:00Z"),
            3,
            4);

    JsonNode json = JsonMapper.builder().build().valueToTree(new PostCard(item, 2, 5, true));

    assertThat(json.path("id").asLong()).isEqualTo(9L);
    assertThat(json.path("title").asString()).isEqualTo("Title");
    assertThat(json.path("likeCount").asLong()).isEqualTo(4);
    assertThat(json.path("readMinutes").asInt()).isEqualTo(2);
    assertThat(json.path("commentCount").asLong()).isEqualTo(5);
    assertThat(json.path("likedByMe").asBoolean()).isTrue();
    assertThat(json.has("item")).isFalse();
  }
}
