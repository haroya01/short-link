package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostPublicationCompletion {
  private final PostRevisionCapture revisions;
  private final PostSearchTextUpdater searchText;
  private final PostNoteQuotes noteQuotes;
  private final ProfileCacheInvalidator cache;
  private final ApplicationEventPublisher events;

  public void complete(PostEntity post, boolean firstPublish, Supplier<Instant> eventTime) {
    revisions.capture(post);
    List<PostBlockEntity> body = searchText.refresh(post);
    if (firstPublish) {
      noteQuotes.indexFirstPublish(post, body);
    } else {
      noteQuotes.index(post, body);
    }
    cache.evictByUserId(post.getUserId());
    if (firstPublish && post.getAudience().shownAsCard()) {
      events.publishEvent(
          new PostPublishedEvent(
              post.getUserId(), post.getId(), post.getSlug(), post.getTitle(), eventTime.get()));
    }
  }
}
