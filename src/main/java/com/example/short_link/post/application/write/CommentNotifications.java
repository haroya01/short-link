package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentNotifications {
  private final UserSummaryReader users;
  private final PostRepository postRepository;
  private final ApplicationEventPublisher events;
  private final BlogNotificationMuteReader muteReader;
  private final PostAccessPolicy access;

  public void commentCreated(
      PostEntity post, CommentEntity parent, CommentEntity comment, String body) {
    Long actorId = comment.getUserId();
    Long parentAuthorId = parent == null ? null : parent.getUserId();
    List<String> handles = MentionParser.parse(body);
    CommentNotificationPlan plan =
        CommentNotificationPlan.forComment(
            post.getUserId(), actorId, parentAuthorId, lookupsFor(post));
    String ownerUsername =
        plan.authorHearsReply() || !handles.isEmpty() ? ownerUsername(post) : null;
    List<Long> mentioned = plan.mentioned(handles);
    if (plan.ownerHearsComment()) {
      events.publishEvent(
          BlogInteractionEvent.comment(
                  post.getUserId(),
                  actorId,
                  post.getId(),
                  post.getSlug(),
                  post.getTitle(),
                  comment.getId(),
                  comment.getCreatedAt())
              .onRoomPost(post.isInRoom()));
    }
    if (plan.authorHearsReply()) {
      events.publishEvent(
          new CommentReplyEvent(
              parentAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              comment.getId(),
              comment.getCreatedAt()));
    }
    for (Long recipient : mentioned) {
      events.publishEvent(
          new CommentMentionEvent(
              recipient,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              comment.getId(),
              comment.getCreatedAt()));
    }
  }

  public void highlightReplyCreated(
      PostHighlightEntity highlight, PostHighlightReplyEntity reply, String body) {
    Long actorId = reply.getUserId();
    Long highlightAuthorId = highlight.getUserId();
    List<String> handles = MentionParser.parse(body);
    if (highlightAuthorId.equals(actorId) && handles.isEmpty()) return;
    PostEntity post = postRepository.findById(highlight.getPostId()).orElse(null);
    if (post == null) return;
    CommentNotificationPlan plan =
        CommentNotificationPlan.forHighlightReply(highlightAuthorId, actorId, lookupsFor(post));
    String ownerUsername = ownerUsername(post);
    List<Long> mentioned = plan.mentioned(handles);
    if (plan.authorHearsReply()) {
      events.publishEvent(
          new HighlightReplyEvent(
              highlightAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              highlight.getId(),
              reply.getCreatedAt()));
    }
    for (Long recipient : mentioned) {
      events.publishEvent(
          new HighlightMentionEvent(
              recipient,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              highlight.getId(),
              reply.getCreatedAt()));
    }
  }

  private CommentNotificationPlan.Lookups lookupsFor(PostEntity post) {
    return new CommentNotificationPlan.Lookups() {
      @Override
      public boolean reads(Long userId) {
        return CommentNotifications.this.reads(post, userId);
      }

      @Override
      public boolean muted(Long userId, BlogNotificationKind kind) {
        return muteReader.isMuted(userId, kind);
      }

      @Override
      public Long activeUserId(String handle) {
        return users.findActiveByUsername(handle).map(UserSummary::id).orElse(null);
      }
    };
  }

  private String ownerUsername(PostEntity post) {
    return users.find(post.getUserId()).map(UserSummary::username).orElse(null);
  }

  public boolean reads(PostEntity post, Long userId) {
    return access.readersAmong(post, Set.of(userId)).contains(userId);
  }
}
