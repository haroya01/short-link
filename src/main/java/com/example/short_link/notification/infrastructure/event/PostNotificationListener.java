package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.CommentLikedEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.event.HighlightReplyLikedEvent;
import com.example.short_link.common.event.NotesEmbeddedEvent;
import com.example.short_link.common.event.PostHighlightedEvent;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;
  private final NotificationFollowerReader followerReader;
  private final NotificationUserReader userReader;
  private final UserBlockChecker blocks;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentReply(CommentReplyEvent event) {
    if (event.isSelfReply()
        || event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            event.postAuthorUsername(),
            event.commentId(),
            null);
    recordUseCase.record(
        event.recipientUserId(), NotificationType.REPLY, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentMention(CommentMentionEvent event) {
    if (event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            event.postAuthorUsername(),
            event.commentId(),
            null);
    recordUseCase.record(
        event.recipientUserId(), NotificationType.MENTION, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onHighlightReply(HighlightReplyEvent event) {
    if (event.isSelfReply()
        || event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            event.postAuthorUsername(),
            null,
            event.highlightId());
    recordUseCase.record(
        event.recipientUserId(), NotificationType.REPLY, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onHighlightMention(HighlightMentionEvent event) {
    if (event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            event.postAuthorUsername(),
            null,
            event.highlightId());
    recordUseCase.record(
        event.recipientUserId(), NotificationType.MENTION, event.actorUserId(), post);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentLiked(CommentLikedEvent event) {
    if (event.isSelfAction()
        || event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    String postAuthor =
        event.recipientUserId().equals(event.postAuthorId())
            ? null
            : userReader
                .findById(event.postAuthorId())
                .map(NotificationUser::username)
                .orElse(null);
    recordUseCase.record(
        event.recipientUserId(),
        NotificationType.COMMENT_LIKE,
        event.actorUserId(),
        null,
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            postAuthor,
            event.commentId(),
            null),
        NotificationGroupKey.of(NotificationType.COMMENT_LIKE, event.commentId()));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onHighlightReplyLiked(HighlightReplyLikedEvent event) {
    if (event.isSelfAction()
        || event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    String postAuthor =
        event.recipientUserId().equals(event.postAuthorId())
            ? null
            : userReader
                .findById(event.postAuthorId())
                .map(NotificationUser::username)
                .orElse(null);
    recordUseCase.record(
        event.recipientUserId(),
        NotificationType.COMMENT_LIKE,
        event.actorUserId(),
        null,
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            postAuthor,
            null,
            event.highlightId()),
        NotificationGroupKey.ofHighlightReply(NotificationType.COMMENT_LIKE, event.replyId()));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostHighlighted(PostHighlightedEvent event) {
    if (event.isSelfAction()
        || event.recipientUserId() == null
        || blocks.silences(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    recordUseCase.record(
        event.recipientUserId(),
        NotificationType.HIGHLIGHT,
        event.actorUserId(),
        null,
        new NotificationPostRef(
            event.postId(), event.postSlug(), event.postTitle(), null, null, event.highlightId()),
        NotificationGroupKey.of(NotificationType.HIGHLIGHT, event.postId()));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNotesEmbedded(NotesEmbeddedEvent event) {
    List<Long> authors =
        followerReader.shareableNoteAuthorsOf(event.noteIds()).stream()
            .filter(author -> !author.equals(event.actorUserId()))
            .filter(author -> !blocks.silences(author, event.actorUserId()))
            .toList();
    if (authors.isEmpty()) {
      return;
    }
    NotificationPostRef post =
        new NotificationPostRef(
            event.postId(),
            event.postSlug(),
            event.postTitle(),
            userReader.findById(event.actorUserId()).map(NotificationUser::username).orElse(null),
            null,
            null);
    for (Long author : authors) {
      recordUseCase.record(
          author,
          NotificationType.NOTE_EMBED,
          event.actorUserId(),
          null,
          post,
          NotificationGroupKey.of(NotificationType.NOTE_EMBED, event.postId()));
    }
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostPublished(PostPublishedEvent event) {
    List<Long> followers = followerReader.followerIdsOf(event.authorUserId());
    if (followers.isEmpty()) {
      return;
    }
    // NEW_POST links use the actor username resolved at read time, so no handle is snapshotted.
    NotificationPostRef post =
        new NotificationPostRef(event.postId(), event.postSlug(), event.postTitle(), null);
    recordUseCase.recordForEach(followers, NotificationType.NEW_POST, event.authorUserId(), post);
  }
}
