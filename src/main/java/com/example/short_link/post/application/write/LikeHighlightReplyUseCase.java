package com.example.short_link.post.application.write;

import com.example.short_link.common.event.HighlightReplyLikedEvent;
import com.example.short_link.post.application.read.HighlightReplyLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.HighlightReplyLikeRepository;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeHighlightReplyUseCase {

  private final PostHighlightReplyRepository replyRepository;
  private final HighlightReplyLikeRepository likeRepository;
  private final PostInteractionAccess access;
  private final ApplicationEventPublisher events;

  @Transactional
  public HighlightReplyLikeStatus like(Long userId, Long replyId) {
    PostInteractionAccess.LikeableHighlightReply target =
        access.requireLikeableHighlightReply(userId, replyId);
    if (likeRepository.insertIgnore(replyId, userId) > 0) {
      PostEntity post = target.post();
      events.publishEvent(
          new HighlightReplyLikedEvent(
              target.reply().getUserId(),
              userId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              post.getUserId(),
              target.highlight().getId(),
              replyId));
    }
    return new HighlightReplyLikeStatus(likeRepository.countByReplyId(replyId), true);
  }

  @Transactional
  public HighlightReplyLikeStatus unlike(Long userId, Long replyId) {
    if (replyRepository.findById(replyId).isEmpty()) {
      throw new PostException(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND, replyId);
    }
    likeRepository.deleteByReplyIdAndUserId(replyId, userId);
    return new HighlightReplyLikeStatus(likeRepository.countByReplyId(replyId), false);
  }
}
