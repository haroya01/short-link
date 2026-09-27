package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishScheduledPostUseCaseTest {
  @Mock private PostRepository posts;
  @Mock private PostPublicationCompletion completion;

  @Test
  void publishesDuePostAndPreservesBatchEventTime() {
    PostEntity post = scheduledPost();
    Instant now = post.getScheduledAt();
    when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post));

    assertThat(new PublishScheduledPostUseCase(posts, completion).execute(1L, now)).isTrue();

    assertThat(post.isPublished()).isTrue();
    verify(posts).save(post);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Supplier<Instant>> time = ArgumentCaptor.forClass(Supplier.class);
    verify(completion).complete(eq(post), eq(true), time.capture());
    assertThat(time.getValue().get()).isEqualTo(now);
  }

  @Test
  void rechecksMissingCancelledRescheduledAndAlreadyPublishedCandidates() {
    Instant now = Instant.now().plusSeconds(7200);
    PostEntity cancelled = scheduledPost();
    cancelled.backToDraft();
    PostEntity published = scheduledPost();
    published.publish();
    PostEntity rescheduled = scheduledPost();
    rescheduled.schedule(now.plusSeconds(3600));
    when(posts.findByIdForUpdate(1L)).thenReturn(Optional.empty());
    when(posts.findByIdForUpdate(2L)).thenReturn(Optional.of(cancelled));
    when(posts.findByIdForUpdate(3L)).thenReturn(Optional.of(published));
    when(posts.findByIdForUpdate(4L)).thenReturn(Optional.of(rescheduled));
    var useCase = new PublishScheduledPostUseCase(posts, completion);

    for (long id = 1; id <= 4; id++) assertThat(useCase.execute(id, now)).isFalse();

    verify(posts, never()).save(any());
    verifyNoInteractions(completion);
  }

  @Test
  void invalidTitleFailsTheSingleTransactionBeforeSaving() {
    PostEntity post = scheduledPost();
    post.updateTitle("");
    when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post));

    assertThatThrownBy(
            () ->
                new PublishScheduledPostUseCase(posts, completion)
                    .execute(1L, post.getScheduledAt()))
        .isInstanceOf(PostException.class);

    assertThat(post.isScheduled()).isTrue();
    verify(posts, never()).save(any());
  }

  private static PostEntity scheduledPost() {
    PostEntity post = new PostEntity(1L, "scheduled", "Title", "ko");
    post.schedule(Instant.now().plusSeconds(3600));
    return post;
  }
}
