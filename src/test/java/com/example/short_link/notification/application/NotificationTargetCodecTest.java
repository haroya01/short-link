package com.example.short_link.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NotificationTargetCodecTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final NotificationTargetCodec codec = new NotificationTargetCodec(mapper);

  @Test
  void postKeepsExistingJsonShapeAndSubtitle() {
    var post = new NotificationPostRef(10L, "my-post", "Title", "author");
    String payload = codec.encode(post);

    assertThat(mapper.readTree(payload))
        .isEqualTo(
            mapper.readTree(
                "{\"postId\":10,\"slug\":\"my-post\",\"title\":\"Title\",\"authorUsername\":\"author\"}"));
    assertThat(codec.decode(NotificationType.REPLY, payload)).isEqualTo(post);
    assertThat(post.pushSubtitle()).isEqualTo("Title");
  }

  @Test
  void postWithoutAuthorKeepsItsNullAuthorKey() {
    String payload = codec.encode(new NotificationPostRef(10L, "my-post", "Title", null));

    assertThat(mapper.readTree(payload))
        .isEqualTo(
            mapper.readTree(
                "{\"postId\":10,\"slug\":\"my-post\",\"title\":\"Title\",\"authorUsername\":null}"));
  }

  @Test
  void postRoundTripsTheSpotInsideThePost() {
    var comment = new NotificationPostRef(10L, "my-post", "Title", "author", 77L, null);
    var highlight = new NotificationPostRef(10L, "my-post", "Title", "author", null, 41L);

    assertThat(mapper.readTree(codec.encode(comment)).get("commentId").asLong()).isEqualTo(77L);
    assertThat(mapper.readTree(codec.encode(comment)).has("highlightId")).isFalse();
    assertThat(codec.decode(NotificationType.COMMENT, codec.encode(comment))).isEqualTo(comment);
    assertThat(codec.decode(NotificationType.REPLY, codec.encode(highlight))).isEqualTo(highlight);
  }

  @Test
  void payloadStoredBeforeTheSpotDecodesWithoutIt() {
    NotificationTarget decoded =
        codec.decode(
            NotificationType.COMMENT,
            "{\"postId\":10,\"slug\":\"my-post\",\"title\":\"Title\",\"authorUsername\":null}");

    assertThat(decoded).isEqualTo(new NotificationPostRef(10L, "my-post", "Title", null));
  }

  @Test
  void collectionKeepsExistingJsonShapeForBothGraphNotices() {
    var collection = new NotificationCollectionRef(42L, "Reading path", null);
    String payload = codec.encode(collection);

    assertThat(mapper.readTree(payload))
        .isEqualTo(
            mapper.readTree(
                "{\"collectionId\":42,\"collectionName\":\"Reading path\",\"postId\":null}"));
    assertThat(codec.decode(NotificationType.CONNECTED, payload)).isEqualTo(collection);
    assertThat(codec.decode(NotificationType.PATH_GREW, payload)).isEqualTo(collection);
    assertThat(collection.pushSubtitle()).isEqualTo("Reading path");
  }

  @Test
  void seriesKeepsItsTitleInTheBellWithoutAddingPushSubtitle() {
    var series = new NotificationSeriesRef(7L, "series", "Series title");
    String payload = codec.encode(series);

    assertThat(mapper.readTree(payload))
        .isEqualTo(
            mapper.readTree("{\"seriesId\":7,\"slug\":\"series\",\"title\":\"Series title\"}"));
    assertThat(codec.decode(NotificationType.SERIES_SUBSCRIBE, payload)).isEqualTo(series);
    assertThat(series.pushSubtitle()).isNull();
  }

  @Test
  void followAndMissingTargetsRemainNull() {
    assertThat(codec.encode(null)).isNull();
    for (NotificationType type : NotificationType.values()) {
      assertThat(codec.decode(type, null)).isNull();
      assertThat(codec.decode(type, "  ")).isNull();
      assertThat(codec.decode(type, "null")).isNull();
    }
  }
}
