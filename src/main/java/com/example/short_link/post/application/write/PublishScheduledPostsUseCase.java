package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final PostSearchTextUpdater searchTextUpdater;
  private final ProfileCacheInvalidator cacheEviction;
  private final ApplicationEventPublisher events;

  @Transactional
  public int execute(Instant now) {
    List<PostEntity> due = postRepository.findScheduledDue(now);
    int published = 0;
    for (PostEntity post : due) {
      try {
        // A scheduled post has never been public, so its first auto-publish notifies followers.
        boolean firstPublish = post.getPublishedAt() == null;
        post.publish();
        postRepository.save(post);
        postRevisionCapture.capture(post);
        // 예약 발행도 수동 발행과 같은 이유로 검색 평문을 채운다 — 예약만 걸고 블록 편집 없이 자동 발행되는 글이
        // 곁 테이블에 누락되지 않도록(본문·제목 검색 회귀 방지). upsert 라 이미 채워진 글에도 무해하다.
        searchTextUpdater.refresh(post);
        // Auto-publish flips hasBlog false→true just like a manual publish; evict the profile.
        cacheEviction.evictByUserId(post.getUserId());
        if (firstPublish) {
          events.publishEvent(
              new PostPublishedEvent(
                  post.getUserId(), post.getId(), post.getSlug(), post.getTitle(), now));
        }
        published++;
      } catch (RuntimeException e) {
        log.warn("scheduled publish skipped post {}: {}", post.getId(), e.getMessage());
      }
    }
    return published;
  }
}
