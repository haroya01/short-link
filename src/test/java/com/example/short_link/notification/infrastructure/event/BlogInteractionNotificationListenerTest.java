package com.example.short_link.notification.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogInteractionNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  @Mock private RecordBlogNotificationUseCase recordUseCase;

  private BlogInteractionNotificationListener listener() {
    return new BlogInteractionNotificationListener(recordUseCase);
  }

  @Test
  void likeRecordsNotificationWithPostReference() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 2L, 10L, "my-post", "Hi", AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(9L), eq(NotificationType.LIKE), eq(2L), post.capture());
    org.assertj.core.api.Assertions.assertThat(post.getValue().slug()).isEqualTo("my-post");
  }

  @Test
  void commentRecordsNotification() {
    listener().onBlogInteraction(BlogInteractionEvent.comment(9L, 2L, 10L, "my-post", "Hi", AT));

    verify(recordUseCase).record(eq(9L), eq(NotificationType.COMMENT), eq(2L), any());
  }

  @Test
  void followRecordsNotificationWithoutPost() {
    listener().onBlogInteraction(BlogInteractionEvent.follow(9L, 2L, AT));

    verify(recordUseCase).record(eq(9L), eq(NotificationType.FOLLOW), eq(2L), isNull());
  }

  @Test
  void selfActionIsSkipped() {
    listener().onBlogInteraction(BlogInteractionEvent.like(9L, 9L, 10L, "s", "t", AT));

    verify(recordUseCase, never()).record(any(), any(), any(), any());
  }

  @Test
  void seriesSubscribeRecordsNotificationWithSeriesReference() {
    listener()
        .onBlogInteraction(
            BlogInteractionEvent.seriesSubscribe(9L, 2L, 4L, "my-series", "Series", AT));

    ArgumentCaptor<NotificationSeriesRef> series =
        ArgumentCaptor.forClass(NotificationSeriesRef.class);
    verify(recordUseCase)
        .record(eq(9L), eq(NotificationType.SERIES_SUBSCRIBE), eq(2L), series.capture());
    org.assertj.core.api.Assertions.assertThat(series.getValue().slug()).isEqualTo("my-series");
  }

  @Test
  void nullRecipientIsSkipped() {
    BlogInteractionEvent event =
        new BlogInteractionEvent(
            BlogInteractionType.LIKE, null, 2L, 10L, "s", "t", null, null, null, AT);

    listener().onBlogInteraction(event);

    verify(recordUseCase, never()).record(any(), any(), any(), any());
  }
}
