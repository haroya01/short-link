package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikePostUseCase {

  private final PostRepository postRepository;
  private final PostLikeRepository postLikeRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public PostLikeStatus like(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    // Insert + counter bump are both atomic at the DB level, so concurrent likes from different
    // users can't lose an increment, and a double-like from the same user bumps the count once.
    if (postLikeRepository.insertIgnore(postId, userId) > 0) {
      postRepository.incrementLikeCount(postId);
      // Only a genuinely new like (not a repeat) notifies the author, and never a self-like.
      if (!post.getUserId().equals(userId)) {
        events.publishEvent(
            BlogInteractionEvent.like(
                post.getUserId(), userId, postId, post.getSlug(), post.getTitle(), Instant.now()));
      }
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), true);
  }

  @Transactional
  public PostLikeStatus unlike(Long userId, Long postId) {
    requirePost(postId);
    if (postLikeRepository.deleteByPostIdAndUserId(postId, userId) > 0) {
      postRepository.decrementLikeCount(postId);
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), false);
  }

  private PostEntity requirePost(Long postId) {
    return postRepository
        .findById(postId)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
  }
}
