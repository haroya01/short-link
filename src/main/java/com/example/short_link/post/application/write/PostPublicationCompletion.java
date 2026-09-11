package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 발행된 버전을 기록하고 검색·프로필·최초 발행 알림을 갱신한다. 호출자의 트랜잭션에 참여한다. */
@Component
@RequiredArgsConstructor
public class PostPublicationCompletion {
  private final PostRevisionCapture revisions;
  private final PostSearchTextUpdater searchText;
  private final ProfileCacheInvalidator cache;
  private final ApplicationEventPublisher events;

  public void complete(PostEntity post, boolean firstPublish, Supplier<Instant> eventTime) {
    revisions.capture(post);
    // 제목만 입력하고 발행한 글도 검색 대상에 포함한다.
    searchText.refresh(post);
    cache.evictByUserId(post.getUserId());
    if (firstPublish) {
      events.publishEvent(
          new PostPublishedEvent(
              post.getUserId(), post.getId(), post.getSlug(), post.getTitle(), eventTime.get()));
    }
  }
}
