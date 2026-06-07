package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records the post-derived notifications that aren't plain BlogInteractionEvents: a REPLY to the
 * parent comment's author, and a NEW_POST fan-out to every follower of a newly-publishing author.
 * Like its sibling {@link BlogInteractionNotificationListener}, it fires only after the source
 * action commits and runs on the shared {@code webhookExecutor}.
 */
@Component
@RequiredArgsConstructor
public class PostNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;
  private final NotificationFollowerReader followerReader;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentReply(CommentReplyEvent event) {
    if (event.isSelfReply() || event.recipientUserId() == null) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(), event.postSlug(), event.postTitle(), event.postAuthorUsername());
    recordUseCase.record(
        event.recipientUserId(), NotificationType.REPLY, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentMention(CommentMentionEvent event) {
    if (event.recipientUserId() == null) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(), event.postSlug(), event.postTitle(), event.postAuthorUsername());
    recordUseCase.record(
        event.recipientUserId(), NotificationType.MENTION, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostPublished(PostPublishedEvent event) {
    List<Long> followers = followerReader.followerIdsOf(event.authorUserId());
    if (followers.isEmpty()) {
      return;
    }
    // The author is the actor; the follower's bell resolves the author handle at read time, so no
    // author username is snapshotted here (post link uses the resolved actor username).
    NotificationPostRef post =
        new NotificationPostRef(event.postId(), event.postSlug(), event.postTitle(), null);
    recordUseCase.recordForEach(followers, NotificationType.NEW_POST, event.authorUserId(), post);
  }
}
