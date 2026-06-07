package com.example.short_link.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BlogInteractionEventTest {

  private static final Instant AT = Instant.parse("2026-06-04T10:00:00Z");

  @Test
  void selfActionWhenRecipientEqualsActor() {
    assertThat(BlogInteractionEvent.like(5L, 5L, 1L, "s", "T", AT).isSelfAction()).isTrue();
    assertThat(BlogInteractionEvent.like(5L, 9L, 1L, "s", "T", AT).isSelfAction()).isFalse();
  }

  @Test
  void notSelfActionWhenRecipientNull() {
    assertThat(BlogInteractionEvent.follow(null, 9L, AT).isSelfAction()).isFalse();
  }

  @Test
  void factoriesSetTheRightType() {
    assertThat(BlogInteractionEvent.like(1L, 2L, 3L, "s", "t", AT).type())
        .isEqualTo(BlogInteractionType.LIKE);
    assertThat(BlogInteractionEvent.comment(1L, 2L, 3L, "s", "t", AT).type())
        .isEqualTo(BlogInteractionType.COMMENT);
    assertThat(BlogInteractionEvent.follow(1L, 2L, AT).type())
        .isEqualTo(BlogInteractionType.FOLLOW);
    assertThat(BlogInteractionEvent.seriesSubscribe(1L, 2L, 3L, "x-slug", "x", AT).type())
        .isEqualTo(BlogInteractionType.SERIES_SUBSCRIBE);
  }
}
