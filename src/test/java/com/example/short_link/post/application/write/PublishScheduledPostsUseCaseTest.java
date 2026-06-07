package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PublishScheduledPostsUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostRevisionCapture postRevisionCapture;
  @Mock private ProfileCacheInvalidator cacheEviction;
  @Mock private ApplicationEventPublisher events;

  private PublishScheduledPostsUseCase useCase() {
    return new PublishScheduledPostsUseCase(
        postRepository, postRevisionCapture, cacheEviction, events);
  }

  private static PostEntity scheduledPost(String slug) {
    PostEntity p = new PostEntity(1L, slug, "Title " + slug, "ko");
    p.schedule(Instant.now().plusSeconds(3600)); // requires a future time to enter SCHEDULED
    return p;
  }

  @Test
  void publishesDuePostsAndCapturesRevisions() {
    PostEntity a = scheduledPost("a");
    PostEntity b = scheduledPost("b");
    when(postRepository.findScheduledDue(any())).thenReturn(List.of(a, b));

    int published = useCase().execute(Instant.now());

    assertThat(published).isEqualTo(2);
    assertThat(a.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(b.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    verify(postRepository, times(2)).save(any(PostEntity.class));
    verify(postRevisionCapture, times(2)).capture(any(PostEntity.class));
    // Each scheduled post is going public for the first time → one fan-out event apiece.
    verify(events, times(2)).publishEvent(any(PostPublishedEvent.class));
  }

  @Test
  void noDuePostsIsNoOp() {
    when(postRepository.findScheduledDue(any())).thenReturn(List.of());

    int published = useCase().execute(Instant.now());

    assertThat(published).isZero();
    verify(postRepository, never()).save(any());
    verify(postRevisionCapture, never()).capture(any());
  }
}
