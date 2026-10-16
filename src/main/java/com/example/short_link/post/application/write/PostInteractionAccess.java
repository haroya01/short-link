package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostInteractionAccess {

  private final PostRepository posts;
  private final CommentRepository comments;
  private final PostHighlightRepository highlights;
  private final PostHighlightReplyRepository highlightReplies;
  private final UserModerationGuard moderation;
  private final UserBlockChecker blocks;
  private final PostAccessPolicy access;

  public PostEntity requireCommentablePost(PostReader reader, Long postId) {
    moderation.requireCanWrite(reader.viewerId());
    return readablePost(postId, reader, PostErrorCode.COMMENT_BLOCKED);
  }

  public PostHighlightEntity requireReplyableHighlight(PostReader reader, Long highlightId) {
    moderation.requireCanWrite(reader.viewerId());
    PostHighlightEntity highlight =
        highlights
            .findById(highlightId)
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_NOT_FOUND, highlightId));
    readablePost(highlight.getPostId(), reader, PostErrorCode.COMMENT_BLOCKED);
    return highlight;
  }

  public PostEntity requireInteractablePost(PostReader reader, Long postId) {
    moderation.requireCanWrite(reader.viewerId());
    return readablePost(postId, reader, PostErrorCode.POST_INTERACTION_BLOCKED);
  }

  // Parent counter mutations must lock the post before inserting or deleting interaction rows.
  public PostEntity requireInteractablePostForUpdate(PostReader reader, Long postId) {
    moderation.requireCanWrite(reader.viewerId());
    return readablePost(
        posts.findByIdForUpdate(postId), postId, reader, PostErrorCode.POST_INTERACTION_BLOCKED);
  }

  public LikeableComment requireLikeableComment(PostReader reader, Long commentId) {
    moderation.requireCanWrite(reader.viewerId());
    CommentEntity comment =
        comments
            .findById(commentId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_NOT_FOUND, commentId));
    return new LikeableComment(
        comment, readablePost(comment.getPostId(), reader, PostErrorCode.POST_INTERACTION_BLOCKED));
  }

  public record LikeableComment(CommentEntity comment, PostEntity post) {}

  public LikeableHighlightReply requireLikeableHighlightReply(PostReader reader, Long replyId) {
    moderation.requireCanWrite(reader.viewerId());
    PostHighlightReplyEntity reply =
        highlightReplies
            .findById(replyId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND, replyId));
    PostHighlightEntity highlight =
        highlights
            .findById(reply.getHighlightId())
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND, replyId));
    return new LikeableHighlightReply(
        reply,
        highlight,
        readablePost(highlight.getPostId(), reader, PostErrorCode.POST_INTERACTION_BLOCKED));
  }

  public record LikeableHighlightReply(
      PostHighlightReplyEntity reply, PostHighlightEntity highlight, PostEntity post) {}

  private PostEntity readablePost(Long postId, PostReader reader, PostErrorCode blockedCode) {
    return readablePost(posts.findById(postId), postId, reader, blockedCode);
  }

  private PostEntity readablePost(
      Optional<PostEntity> candidate, Long postId, PostReader reader, PostErrorCode blockedCode) {
    PostEntity post =
        candidate.orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    PostAccess decided = access.decide(post, reader);
    if (decided.hidden()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
    }
    if (blocks.isBlocked(post.getUserId(), reader.viewerId())) {
      throw new PostException(blockedCode).with("postId", postId);
    }
    if (decided.redacted()) {
      throw new PostException(PostErrorCode.POST_LOCKED, postId).with("reason", decided.lock());
    }
    return post;
  }
}
