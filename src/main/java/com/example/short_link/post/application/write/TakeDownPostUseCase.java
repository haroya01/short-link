package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TakeDownPostUseCase {

  private final PostRepository postRepository;
  private final ProfileCacheInvalidator cacheEviction;
  private final Clock clock;

  @Transactional
  public void takeDown(Long adminUserId, Long postId) {
    log.info("admin post takedown: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post = lock(postId);
    boolean wasPublished = post.isPublished();
    post.takeDown(clock.instant());
    postRepository.save(post);
    if (wasPublished) {
      cacheEviction.evictByUserId(post.getUserId());
    }
  }

  @Transactional
  public void release(Long adminUserId, Long postId) {
    log.info("admin post takedown release: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post = lock(postId);
    post.releaseTakeDown();
    postRepository.save(post);
  }

  private PostEntity lock(Long postId) {
    return postRepository
        .findByIdForUpdate(postId)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
  }
}
