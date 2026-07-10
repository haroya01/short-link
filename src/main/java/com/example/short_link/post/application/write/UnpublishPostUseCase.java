package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnpublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final ProfileCacheInvalidator cacheEviction;

  @Transactional
  public PostEntity execute(UnpublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    post.unpublish();
    PostEntity saved = postRepository.save(post);
    // Unpublishing the author's last public post drops them to hasBlog=false; evict the profile.
    cacheEviction.evictByUserId(saved.getUserId());
    return saved;
  }

  /**
   * Admin takedown of any author's post. There is no ownership gate here on purpose — {@code
   * /api/v1/admin/**} is already ADMIN-only at the security layer, mirroring {@code
   * LinkStatsQueryService#adminStats}. Idempotent: an already-unpublished post is left unchanged so
   * a repeat takedown is a no-op rather than an error; a missing post is a 404. {@code adminUserId}
   * is recorded for the audit trail only.
   */
  @Transactional
  public void adminExecute(Long adminUserId, Long postId) {
    log.info("admin post takedown: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (post.isUnpublished()) {
      return;
    }
    post.unpublish();
    PostEntity saved = postRepository.save(post);
    cacheEviction.evictByUserId(saved.getUserId());
  }
}
