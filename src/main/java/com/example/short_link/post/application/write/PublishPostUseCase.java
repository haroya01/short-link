package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostRevisionCapture postRevisionCapture;
  private final PostSearchTextUpdater searchTextUpdater;
  private final ProfileCacheInvalidator cacheEviction;
  private final ApplicationEventPublisher events;

  @Transactional
  public PostEntity execute(PublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    // publishedAt is stamped only on the very first publish and preserved across
    // unpublish/republish
    // — so a null here is exactly "never been public", which is when followers should be notified.
    boolean firstPublish = post.getPublishedAt() == null;
    post.publish();
    PostEntity saved = postRepository.save(post);
    postRevisionCapture.capture(saved);
    // 검색 평문을 발행 시점에도 새로 채운다. 편집 use-case(ReplaceBlocks·RestoreRevision·UpdateMetadata)만으로는
    // "제목만 붙이고 블록 편집 없이 바로 발행" 하는 글이 곁 테이블에 아무 행도 못 만들어 본문·제목 검색에서 통째로 누락된다.
    // upsert 라 이미 편집으로 채워진 글에도 무해하며(현재 본문으로 최신화), 발행은 드문 쓰기라 조회 한 번이 부담되지 않는다.
    searchTextUpdater.refresh(saved);
    // First publish flips the author's public profile to hasBlog (publishedPostCount 0→1); evict so
    // the link-in-bio surface shows the blog entry-point immediately. Inline eviction matches the
    // existing AvatarService/OgOverrideService convention.
    cacheEviction.evictByUserId(saved.getUserId());
    if (firstPublish) {
      events.publishEvent(
          new PostPublishedEvent(
              saved.getUserId(), saved.getId(), saved.getSlug(), saved.getTitle(), Instant.now()));
    }
    return saved;
  }
}
