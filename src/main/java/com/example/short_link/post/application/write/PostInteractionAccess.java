package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Access to new public interactions. Removing an existing interaction uses its own ownership rules.
 */
@Component
@RequiredArgsConstructor
public class PostInteractionAccess {

  private final PostRepository posts;
  private final CommentRepository comments;
  private final PostHighlightRepository highlights;
  private final UserModerationGuard moderation;
  private final UserBlockChecker blocks;

  public PostEntity requireCommentablePost(Long actorId, Long postId) {
    moderation.requireCanWrite(actorId);
    return publishedPost(postId, actorId, PostErrorCode.COMMENT_BLOCKED);
  }

  public PostHighlightEntity requireReplyableHighlight(Long actorId, Long highlightId) {
    moderation.requireCanWrite(actorId);
    PostHighlightEntity highlight =
        highlights
            .findById(highlightId)
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_NOT_FOUND, highlightId));
    publishedPost(highlight.getPostId(), actorId, PostErrorCode.COMMENT_BLOCKED);
    return highlight;
  }

  public PostEntity requireInteractablePost(Long actorId, Long postId) {
    moderation.requireCanWrite(actorId);
    return publishedPost(postId, actorId, PostErrorCode.POST_INTERACTION_BLOCKED);
  }

  /** Parent counter mutations must lock the post before inserting or deleting interaction rows. */
  public PostEntity requireInteractablePostForUpdate(Long actorId, Long postId) {
    moderation.requireCanWrite(actorId);
    return publishedPost(
        posts.findByIdForUpdate(postId), postId, actorId, PostErrorCode.POST_INTERACTION_BLOCKED);
  }

  public void requireLikeableComment(Long actorId, Long commentId) {
    moderation.requireCanWrite(actorId);
    CommentEntity comment =
        comments
            .findById(commentId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_NOT_FOUND, commentId));
    publishedPost(comment.getPostId(), actorId, PostErrorCode.POST_INTERACTION_BLOCKED);
  }

  private PostEntity publishedPost(Long postId, Long actorId, PostErrorCode blockedCode) {
    return publishedPost(posts.findById(postId), postId, actorId, blockedCode);
  }

  private PostEntity publishedPost(
      Optional<PostEntity> candidate, Long postId, Long actorId, PostErrorCode blockedCode) {
    PostEntity post =
        candidate
            .filter(PostEntity::isPublished)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (blocks.isBlocked(post.getUserId(), actorId)) {
      throw new PostException(blockedCode).with("postId", postId);
    }
    return post;
  }
}
