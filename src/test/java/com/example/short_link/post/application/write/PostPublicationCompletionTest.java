package com.example.short_link.post.application.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.support.TestEntities;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PostPublicationCompletionTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final PostPublicationCompletion completion =
      new PostPublicationCompletion(
          mock(PostRevisionCapture.class),
          mock(PostSearchTextUpdater.class),
          mock(PostNoteQuotes.class),
          mock(ProfileCacheInvalidator.class),
          events);

  @Test
  void followersHearOfAFirstPublishTheyMayFindInTheirFeeds() {
    completion.complete(post(PostAudience.FOLLOWERS), true, Instant::now);

    verify(events).publishEvent(any(PostPublishedEvent.class));
  }

  @Test
  void anUnlistedFirstPublishIsKeptQuiet() {
    completion.complete(post(PostAudience.UNLISTED), true, Instant::now);

    verify(events, never()).publishEvent(any(PostPublishedEvent.class));
  }

  private static PostEntity post(PostAudience audience) {
    PostEntity post = TestEntities.withId(new PostEntity(7L, "p", "Title", "ko"), 42L);
    post.publish();
    post.changeAudience(audience, "key");
    return post;
  }
}
