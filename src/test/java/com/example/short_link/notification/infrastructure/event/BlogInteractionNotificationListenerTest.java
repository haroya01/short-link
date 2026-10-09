package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogInteractionNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  @Mock private RecordBlogNotificationUseCase recordUseCase;
  @Mock private UserBlockChecker blocks;

  private BlogInteractionNotificationListener listener() {
    return new BlogInteractionNotificationListener(recordUseCase, blocks);
  }

  @Test
  void likesOfOnePostGroupByDayLikeNoteLikes() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "my-post", "Hi", AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(
            eq(9L),
            eq(NotificationType.LIKE),
            eq(2L),
            isNull(),
            post.capture(),
            eq("LIKE:10:" + LocalDate.now(ZoneOffset.UTC)));
    assertThat(post.getValue().slug()).isEqualTo("my-post");
  }

  @Test
  void commentRecordsNotificationPointingAtTheCommentOnItsOwn() {
    listener()
        .onBlogInteraction(BlogInteractionEvent.comment(9L, 2L, 10L, "my-post", "Hi", 55L, AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(eq(9L), eq(NotificationType.COMMENT), eq(2L), isNull(), post.capture(), isNull());
    assertThat(post.getValue().commentId()).isEqualTo(55L);
    assertThat(post.getValue().slug()).isEqualTo("my-post");
  }

  @Test
  void aFollowIsHeardOncePerPersonAndDayWithoutFoldingPeopleTogether() {
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));

    verify(recordUseCase)
        .record(
            eq(9L),
            eq(NotificationType.FOLLOW),
            eq(2L),
            isNull(),
            isNull(),
            eq("FOLLOW:2:null:" + LocalDate.now(ZoneOffset.UTC)));
  }

  @Test
  void selfActionIsSkipped() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 9L, 10L, "s", "t", AT));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void aMemberMutedWithTheirNoticesIsHeardOnPostsAsLittleAsOnNotes() {
    when(blocks.silences(9L, 2L)).thenReturn(true);

    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "s", "t", AT));
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void seriesSubscribeRecordsNotificationWithSeriesReference() {
    listener()
        .onBlogInteraction(
            BlogInteractionEvent.seriesSubscribe(9L, 2L, 4L, "my-series", "Series", AT));

    ArgumentCaptor<NotificationSeriesRef> series =
        ArgumentCaptor.forClass(NotificationSeriesRef.class);
    verify(recordUseCase)
        .record(
            eq(9L),
            eq(NotificationType.SERIES_SUBSCRIBE),
            eq(2L),
            isNull(),
            series.capture(),
            eq("SERIES_SUBSCRIBE:2:4:" + LocalDate.now(ZoneOffset.UTC)));
    assertThat(series.getValue().slug()).isEqualTo("my-series");
  }

  @Test
  void nullRecipientIsSkipped() {
    BlogInteractionEvent event =
        new BlogInteractionEvent(
            BlogInteractionType.LIKE, null, 2L, 10L, "s", "t", null, null, null, null, AT);

    listener().onBlogInteraction(event);

    verifyNoInteractions(recordUseCase);
    verify(blocks, org.mockito.Mockito.never()).silences(any(), any());
  }
}
