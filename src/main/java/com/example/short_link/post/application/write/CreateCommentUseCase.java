package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCommentUseCase {

  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;
  private final BlogNotificationMuteReader muteReader;
  private final UserModerationGuard moderationGuard;
  private final UserBlockChecker blockChecker;

  @Transactional
  public CommentView execute(CreateCommentCommand cmd) {
    // 제재(BANNED/현재 SUSPENDED) 유저는 댓글을 달 수 없다.
    moderationGuard.requireCanWrite(cmd.userId());
    PostEntity post =
        postRepository
            .findById(cmd.postId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId()));
    if (!post.isPublished()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId());
    }
    // 차단 서버집행: 글 작성자가 댓글 작성자를 차단했다면 상호작용 거부.
    if (blockChecker.isBlocked(post.getUserId(), cmd.userId())) {
      throw new PostException(PostErrorCode.COMMENT_BLOCKED).with("postId", cmd.postId());
    }

    CommentEntity parent = null;
    if (cmd.parentId() != null) {
      parent =
          commentRepository
              .findById(cmd.parentId())
              .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_PARENT_INVALID));
      // Parent must be a top-level comment on the same post (only one level of threading).
      if (!parent.getPostId().equals(cmd.postId()) || parent.isReply()) {
        throw new PostException(PostErrorCode.COMMENT_PARENT_INVALID);
      }
    }

    CommentEntity saved =
        commentRepository.save(
            new CommentEntity(cmd.postId(), cmd.userId(), cmd.parentId(), cmd.body().trim()));

    Long ownerId = post.getUserId();
    Long actorId = cmd.userId();
    Long parentAuthorId = parent == null ? null : parent.getUserId();
    // Everyone this one comment will actually deliver a COMMENT/REPLY to — so a @-mention of the
    // same person collapses into the notice they already get (one notice per person). Only people
    // who will *receive* the COMMENT/REPLY are folded in: if they've muted that kind the notice is
    // dropped at record time, so we must leave them un-folded here or an explicit @-mention of them
    // would silently vanish. Their MENTION is still subject to their own MENTION opt-out
    // downstream.
    Set<Long> notified = new HashSet<>();

    // Notify the post's author of a new comment — never for a comment on your own post. But when
    // the
    // comment replies to the owner's own comment, the more specific REPLY notice below covers it,
    // so
    // we don't also send a COMMENT (one notice, not two, for the same person).
    boolean replyToOwner = parentAuthorId != null && parentAuthorId.equals(ownerId);
    if (!ownerId.equals(actorId) && !replyToOwner) {
      events.publishEvent(
          BlogInteractionEvent.comment(
              ownerId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              saved.getCreatedAt()));
      if (!muteReader.isMuted(ownerId, BlogNotificationKind.COMMENT)) {
        notified.add(ownerId);
      }
    }

    // REPLY and MENTION links point at the post, whose owner may not be the recipient — so both
    // carry
    // the post owner's username. Resolve it once, only when one of them will actually fire.
    List<String> mentionedHandles = MentionParser.parse(cmd.body());
    boolean needsOwnerHandle =
        (parentAuthorId != null && !parentAuthorId.equals(actorId)) || !mentionedHandles.isEmpty();
    String ownerUsername =
        needsOwnerHandle
            ? userRepository.findById(ownerId).map(UserEntity::getUsername).orElse(null)
            : null;

    // Notify the parent comment's author of a reply — never for replying to your own comment.
    if (parentAuthorId != null && !parentAuthorId.equals(actorId)) {
      events.publishEvent(
          new CommentReplyEvent(
              parentAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              saved.getCreatedAt()));
      if (!muteReader.isMuted(parentAuthorId, BlogNotificationKind.REPLY)) {
        notified.add(parentAuthorId);
      }
    }

    // @-mentions — one notice per mentioned user, skipping self, unknown handles, and anyone who
    // will actually receive the COMMENT/REPLY above (notified.add returns false → skip). Someone
    // who
    // muted that kind was left out of `notified`, so their explicit mention still fires here.
    for (String handle : mentionedHandles) {
      Long mentionedId = userRepository.findByUsername(handle).map(UserEntity::getId).orElse(null);
      if (mentionedId == null || mentionedId.equals(actorId) || !notified.add(mentionedId)) {
        continue;
      }
      events.publishEvent(
          new CommentMentionEvent(
              mentionedId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              saved.getCreatedAt()));
    }

    UserEntity author = userRepository.findById(actorId).orElse(null);
    return new CommentView(
        saved.getId(),
        saved.getParentId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt(),
        0L);
  }
}
