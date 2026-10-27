package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Row;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BlogInteractionNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  private final StoredNotices stored = new StoredNotices();

  private BlogInteractionNotificationListener listener() {
    return new BlogInteractionNotificationListener(stored.useCase(), stored.blocks);
  }

  private static String today() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  @Test
  void likesOfOnePostGroupByDayLikeNoteLikes() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "my-post", "Hi", AT));
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "my-post", "Hi", AT));
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 3L, 10L, "my-post", "Hi", AT));

    NotificationPostRef post = new NotificationPostRef(10L, "my-post", "Hi", null, null, null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(9L, NotificationType.LIKE, 2L, null, post, "LIKE:10:" + today()),
            new Row(9L, NotificationType.LIKE, 3L, null, post, "LIKE:10:" + today()));
  }

  @Test
  void commentRecordsNotificationPointingAtTheCommentOnItsOwn() {
    listener()
        .onBlogInteraction(BlogInteractionEvent.comment(9L, 2L, 10L, "my-post", "Hi", 55L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                9L,
                NotificationType.COMMENT,
                2L,
                null,
                new NotificationPostRef(10L, "my-post", "Hi", null, 55L, null),
                null));
  }

  @Test
  void aFollowIsHeardOncePerPersonAndDayWithoutFoldingPeopleTogether() {
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 3L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(9L, NotificationType.FOLLOW, 2L, null, null, "FOLLOW:2:null:" + today()),
            new Row(9L, NotificationType.FOLLOW, 3L, null, null, "FOLLOW:3:null:" + today()));
  }

  @Test
  void selfActionIsSkipped() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 9L, 10L, "s", "t", AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void aMemberMutedWithTheirNoticesIsHeardOnPostsAsLittleAsOnNotes() {
    stored.silence(9L, 2L);

    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "s", "t", AT));
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.pushes()).isEmpty();
  }

  @Test
  void seriesSubscribeRecordsNotificationWithSeriesReference() {
    listener()
        .onBlogInteraction(
            BlogInteractionEvent.seriesSubscribe(9L, 2L, 4L, "my-series", "Series", AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                9L,
                NotificationType.SERIES_SUBSCRIBE,
                2L,
                null,
                new NotificationSeriesRef(4L, "my-series", "Series"),
                "SERIES_SUBSCRIBE:2:4:" + today()));
  }

  @Test
  void nullRecipientIsSkipped() {
    BlogInteractionEvent event =
        new BlogInteractionEvent(
            BlogInteractionType.LIKE, null, 2L, 10L, "s", "t", null, null, null, null, AT);

    listener().onBlogInteraction(event);

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isZero();
  }
}
