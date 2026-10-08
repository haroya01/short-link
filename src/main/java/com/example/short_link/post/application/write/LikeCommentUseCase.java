package com.example.short_link.post.application.write;

import com.example.short_link.common.event.CommentLikedEvent;
import com.example.short_link.post.application.read.CommentLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// INSERT IGNORE로 중복 좋아요를 무시한다.
@Service
@RequiredArgsConstructor
public class LikeCommentUseCase {

  private final CommentRepository commentRepository;
  private final CommentLikeRepository commentLikeRepository;
  private final PostInteractionAccess access;
  private final ApplicationEventPublisher events;

  @Transactional
  public CommentLikeStatus like(Long userId, Long commentId) {
    PostInteractionAccess.LikeableComment target = access.requireLikeableComment(userId, commentId);
    if (commentLikeRepository.insertIgnore(commentId, userId) > 0) {
      PostEntity post = target.post();
      events.publishEvent(
          new CommentLikedEvent(
              target.comment().getUserId(),
              userId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              post.getUserId(),
              commentId));
    }
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
