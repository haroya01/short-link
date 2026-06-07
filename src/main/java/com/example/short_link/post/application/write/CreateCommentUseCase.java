package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
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

  @Transactional
  public CommentView execute(CreateCommentCommand cmd) {
    PostEntity post =
        postRepository
            .findById(cmd.postId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId()));
    if (!post.isPublished()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId());
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
    }

    // Notify the parent comment's author of a reply — never for replying to your own comment. The
    // parent's author may not own the post, so we carry the post owner's username for the link.
    if (parentAuthorId != null && !parentAuthorId.equals(actorId)) {
      String ownerUsername =
          userRepository.findById(ownerId).map(UserEntity::getUsername).orElse(null);
      events.publishEvent(
          new CommentReplyEvent(
              parentAuthorId,
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
        saved.getCreatedAt());
  }
}
