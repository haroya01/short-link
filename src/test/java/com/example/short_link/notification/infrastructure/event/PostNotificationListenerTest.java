package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  @Mock private RecordBlogNotificationUseCase recordUseCase;
  @Mock private NotificationFollowerReader followerReader;

  private PostNotificationListener listener() {
    return new PostNotificationListener(recordUseCase, followerReader);
  }

  @Test
  void replyRecordsReplyNotificationCarryingPostAuthorHandle() {
    listener()
        .onCommentReply(new CommentReplyEvent(3L, 9L, 10L, "the-post", "The Post", "owner", AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(3L), eq(NotificationType.REPLY), eq(9L), post.capture());
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().slug()).isEqualTo("the-post");
  }

  @Test
  void selfReplyIsSkipped() {
    listener().onCommentReply(new CommentReplyEvent(9L, 9L, 10L, "s", "t", "o", AT));

    verify(recordUseCase, never()).record(any(), any(), any(), any());
  }

  @Test
  void postPublishedFansOutOneNewPostNotificationPerFollower() {
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of(1L, 2L, 3L));

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "the-post", "The Post", AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .recordForEach(
            eq(List.of(1L, 2L, 3L)), eq(NotificationType.NEW_POST), eq(7L), post.capture());
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().authorUsername()).isNull(); // resolved from actor at read time
  }

  @Test
  void postPublishedWithNoFollowersIsNoOp() {
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of());

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "s", "t", AT));

    verify(recordUseCase, never()).recordForEach(any(), any(), any(), any());
  }
}
