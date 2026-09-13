package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentNotifications {
  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final ApplicationEventPublisher events;
  private final BlogNotificationMuteReader muteReader;

  public void commentCreated(
      PostEntity post, CommentEntity parent, CommentEntity comment, String body) {
    Long ownerId = post.getUserId();
    Long actorId = comment.getUserId();
    Long parentAuthorId = parent == null ? null : parent.getUserId();
    Set<Long> mentionSuppressedRecipients = new HashSet<>();
    // 소유자에게 직접 답한 경우 REPLY가 우선한다. REPLY를 꺼도 COMMENT로 대체하지 않는다.
    boolean replyToOwner = ownerId.equals(parentAuthorId);
    if (!ownerId.equals(actorId) && !replyToOwner) {
      events.publishEvent(
          BlogInteractionEvent.comment(
              ownerId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              comment.getCreatedAt()));
      suppressMentionUnlessMuted(
          mentionSuppressedRecipients, ownerId, BlogNotificationKind.COMMENT);
    }
    List<String> handles = MentionParser.parse(body);
    boolean notifyParent = parentAuthorId != null && !parentAuthorId.equals(actorId);
    String ownerUsername = notifyParent || !handles.isEmpty() ? ownerUsername(post) : null;
    if (notifyParent) {
      events.publishEvent(
          new CommentReplyEvent(
              parentAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              comment.getCreatedAt()));
      suppressMentionUnlessMuted(
          mentionSuppressedRecipients, parentAuthorId, BlogNotificationKind.REPLY);
    }
    publishMentions(
        handles,
        actorId,
        mentionSuppressedRecipients,
        recipient ->
            events.publishEvent(
                new CommentMentionEvent(
                    recipient,
                    actorId,
                    post.getId(),
                    post.getSlug(),
                    post.getTitle(),
                    ownerUsername,
                    comment.getCreatedAt())));
  }

  public void highlightReplyCreated(
      PostHighlightEntity highlight, PostHighlightReplyEntity reply, String body) {
    Long actorId = reply.getUserId();
    Long highlightAuthorId = highlight.getUserId();
    List<String> handles = MentionParser.parse(body);
    boolean notifyAuthor = !highlightAuthorId.equals(actorId);
    if (!notifyAuthor && handles.isEmpty()) return;
    PostEntity post = postRepository.findById(highlight.getPostId()).orElse(null);
    if (post == null) return;
    String ownerUsername = ownerUsername(post);
    Set<Long> mentionSuppressedRecipients = new HashSet<>();
    if (notifyAuthor) {
      events.publishEvent(
          new HighlightReplyEvent(
              highlightAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              reply.getCreatedAt()));
      suppressMentionUnlessMuted(
          mentionSuppressedRecipients, highlightAuthorId, BlogNotificationKind.REPLY);
    }
    publishMentions(
        handles,
        actorId,
        mentionSuppressedRecipients,
        recipient ->
            events.publishEvent(
                new HighlightMentionEvent(
                    recipient,
                    actorId,
                    post.getId(),
                    post.getSlug(),
                    post.getTitle(),
                    ownerUsername,
                    reply.getCreatedAt())));
  }

  private String ownerUsername(PostEntity post) {
    return userRepository.findById(post.getUserId()).map(UserEntity::getUsername).orElse(null);
  }

  private void suppressMentionUnlessMuted(
      Set<Long> recipients, Long recipient, BlogNotificationKind kind) {
    // 이벤트 발행은 전달 완료가 아니다. 수신 거부한 COMMENT/REPLY는 명시적 멘션을 억제하지 않는다.
    if (!muteReader.isMuted(recipient, kind)) recipients.add(recipient);
  }

  private void publishMentions(
      List<String> handles, Long actorId, Set<Long> recipients, Consumer<Long> publish) {
    for (String handle : handles) {
      Long recipient = userRepository.findByUsername(handle).map(UserEntity::getId).orElse(null);
      if (recipient != null && !recipient.equals(actorId) && recipients.add(recipient)) {
        publish.accept(recipient);
      }
    }
  }
}
