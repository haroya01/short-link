package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.CommentLikeStatus;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comment like toggle — the post-like contract in miniature: INSERT IGNORE keeps a double-like
 * idempotent, the response carries the authoritative count. No notification on purpose: the post
 * owner already hears about the comment itself, and comment-like pings are noise at this scale.
 */
@Service
@RequiredArgsConstructor
public class LikeCommentUseCase {

  private final CommentRepository commentRepository;
  private final CommentLikeRepository commentLikeRepository;

  @Transactional
  public CommentLikeStatus like(Long userId, Long commentId) {
    requireComment(commentId);
    commentLikeRepository.insertIgnore(commentId, userId);
    return new CommentLikeStatus(commentLikeRepository.countByCommentId(commentId), true);
  }

  @Transactional
  public CommentLikeStatus unlike(Long userId, Long commentId) {
    requireComment(commentId);
    commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
    return new CommentLikeStatus(commentLikeRepository.countByCommentId(commentId), false);
  }

  private void requireComment(Long commentId) {
    if (commentRepository.findById(commentId).isEmpty()) {
      throw new PostException(PostErrorCode.COMMENT_NOT_FOUND, commentId);
    }
  }
}
