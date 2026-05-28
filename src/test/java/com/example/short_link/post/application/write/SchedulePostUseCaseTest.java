package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulePostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;

  private SchedulePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SchedulePostUseCase(postOwnership, postRepository);
  }

  @Test
  void schedulesDraft() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    Instant when = Instant.now().plus(2, ChronoUnit.HOURS);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity result = useCase.execute(new SchedulePostCommand(7L, 42L, when));

    assertThat(result.getStatus()).isEqualTo(PostStatus.SCHEDULED);
    assertThat(result.getScheduledAt()).isEqualTo(when);
  }

  @Test
  void rejectsScheduleInPast() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    assertThatThrownBy(() -> useCase.execute(new SchedulePostCommand(7L, 42L, past)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SCHEDULE_IN_PAST);
  }

  @Test
  void rejectsScheduleAfterPublish() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.publish();
    Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    assertThatThrownBy(() -> useCase.execute(new SchedulePostCommand(7L, 42L, future)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SCHEDULE_AFTER_PUBLISH);
  }
}
