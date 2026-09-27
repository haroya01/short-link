package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.repository.PostRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishScheduledPostsUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

  @Mock private PostRepository posts;
  @Mock private PublishScheduledPostUseCase publishPost;
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private PublishScheduledPostsUseCase useCase() {
    return new PublishScheduledPostsUseCase(
        posts, publishPost, new ScheduledPublicationBackoff(), meters);
  }

  @Test
  void countsOnlyCommittedPublicationsAndContinuesAfterAFailedPost() {
    when(posts.findScheduledDueIds(NOW)).thenReturn(List.of(1L, 2L, 3L));
    when(publishPost.execute(1L, NOW)).thenThrow(new IllegalStateException("commit failed"));
    when(publishPost.execute(2L, NOW)).thenReturn(false);
    when(publishPost.execute(3L, NOW)).thenReturn(true);

    assertThat(useCase().execute(NOW)).isEqualTo(1);

    verify(publishPost).execute(3L, NOW);
    assertThat(meters.counter("short_link.post.scheduled_publish.failed").count()).isEqualTo(1);
  }

  @Test
  void emptyWorkListDoesNotOpenPublicationTransactions() {
    when(posts.findScheduledDueIds(NOW)).thenReturn(List.of());

    assertThat(useCase().execute(NOW)).isZero();

    verifyNoInteractions(publishPost);
  }

  @Test
  void aFailingPostWaitsForItsRetryWhileOtherPostsKeepPublishing() {
    PublishScheduledPostsUseCase useCase = useCase();
    Instant nextTick = NOW.plus(Duration.ofMinutes(1));
    Instant thirdTick = NOW.plus(Duration.ofMinutes(2));
    when(posts.findScheduledDueIds(NOW)).thenReturn(List.of(1L));
    when(posts.findScheduledDueIds(nextTick)).thenReturn(List.of(1L, 2L));
    when(posts.findScheduledDueIds(thirdTick)).thenReturn(List.of(1L));
    when(publishPost.execute(1L, NOW)).thenThrow(new IllegalStateException("search down"));
    when(publishPost.execute(1L, nextTick)).thenThrow(new IllegalStateException("search down"));
    when(publishPost.execute(2L, nextTick)).thenReturn(true);

    useCase.execute(NOW);
    assertThat(useCase.execute(nextTick)).isEqualTo(1);
    useCase.execute(thirdTick);

    verify(publishPost, times(1)).execute(1L, nextTick);
    verify(publishPost, never()).execute(1L, thirdTick);
  }
}
