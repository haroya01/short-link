package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
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
  private final PostInteractionAccess access;
  private final PostAccessPolicy policy;

  @Transactional
  public PostLikeStatus like(PostReader reader, Long postId) {
    Long userId = reader.viewerId();
    PostEntity post = access.requireInteractablePostForUpdate(reader, postId);
    if (postLikeRepository.insertIgnore(postId, userId) > 0) {
      postRepository.incrementLikeCount(postId);
      if (!post.getUserId().equals(userId)) {
        events.publishEvent(
            BlogInteractionEvent.like(
                post.getUserId(), userId, postId, post.getSlug(), post.getTitle(), Instant.now()));
      }
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), true);
  }

  @Transactional
  public PostLikeStatus unlike(PostReader reader, Long postId) {
    PostEntity post = requirePost(postId);
    if (!policy.revealsExistence(post, reader)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
    }
    PostAccess decided = policy.decide(post, reader);
    if (postLikeRepository.deleteByPostIdAndUserId(postId, reader.viewerId()) > 0) {
      postRepository.decrementLikeCount(postId);
    }
    if (decided.redacted()) {
      return new PostLikeStatus(0, false);
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), false);
  }

  private PostEntity requirePost(Long postId) {
    return postRepository
        .findByIdForUpdate(postId)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
  }
}
