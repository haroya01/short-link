package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flips SCHEDULED posts whose time has come to PUBLISHED. The schedule endpoint only parks a post
 * (status=SCHEDULED, scheduledAt); this is what actually publishes it — invoked per-minute by
 * {@code PublishScheduledPostsJob}. A post that can't go public (e.g. its title was cleared after
 * scheduling) is skipped, not allowed to fail the whole batch. Revisions are captured like a manual
 * publish, so the published version is recorded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishScheduledPostsUseCase {

  private final PostRepository postRepository;
  private final PostRevisionCapture postRevisionCapture;
  private final ProfileCacheInvalidator cacheEviction;

  @Transactional
  public int execute(Instant now) {
    List<PostEntity> due = postRepository.findScheduledDue(now);
    int published = 0;
    for (PostEntity post : due) {
      try {
        post.publish();
        postRepository.save(post);
        postRevisionCapture.capture(post);
        // Auto-publish flips hasBlog false→true just like a manual publish; evict the profile.
        cacheEviction.evictByUserId(post.getUserId());
        published++;
      } catch (RuntimeException e) {
        log.warn("scheduled publish skipped post {}: {}", post.getId(), e.getMessage());
      }
    }
    return published;
  }
}
