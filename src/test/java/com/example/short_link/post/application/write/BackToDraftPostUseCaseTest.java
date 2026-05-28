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
class BackToDraftPostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;

  private BackToDraftPostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new BackToDraftPostUseCase(postOwnership, postRepository);
  }

  @Test
  void revertsScheduledToDraft() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.schedule(Instant.now().plus(1, ChronoUnit.HOURS));
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity result = useCase.execute(new BackToDraftPostCommand(7L, 42L));

    assertThat(result.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(result.getScheduledAt()).isNull();
  }

  @Test
  void rejectsBackToDraftFromPublished() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.publish();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    assertThatThrownBy(() -> useCase.execute(new BackToDraftPostCommand(7L, 42L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.BACK_TO_DRAFT_NOT_SCHEDULED);
  }
}
