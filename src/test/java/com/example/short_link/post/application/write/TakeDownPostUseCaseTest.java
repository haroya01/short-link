package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TakeDownPostUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

  @Mock private PostRepository postRepository;
  @Mock private ProfileCacheInvalidator cacheEviction;

  private TakeDownPostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new TakeDownPostUseCase(postRepository, cacheEviction, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void takingDownALivePostStampsTheTimeAndDropsTheProfileCache() {
    PostEntity post = new PostEntity(7L, "live", "Live", "ko");
    post.publish();
    when(postRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(post));

    useCase.takeDown(1L, 42L);

    assertThat(post.isUnpublished()).isTrue();
    assertThat(post.getTakenDownAt()).isEqualTo(NOW);
    verify(postRepository).save(post);
    verify(cacheEviction).evictByUserId(7L);
  }

  @Test
  void takingDownAPostThatWasNotPublicLeavesTheProfileCacheAlone() {
    PostEntity post = new PostEntity(7L, "draft", "Draft", "ko");
    when(postRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(post));

    useCase.takeDown(1L, 42L);

    assertThat(post.isTakenDown()).isTrue();
    verify(cacheEviction, never()).evictByUserId(any());
  }

  @Test
  void releaseClearsTheTakedownAndLeavesTheStatus() {
    PostEntity post = new PostEntity(7L, "live", "Live", "ko");
    post.publish();
    post.takeDown(NOW);
    when(postRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(post));

    useCase.release(1L, 42L);

    assertThat(post.isTakenDown()).isFalse();
    assertThat(post.isUnpublished()).isTrue();
    verify(postRepository).save(post);
  }

  @Test
  void aMissingPostIs404() {
    when(postRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.takeDown(1L, 42L))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
  }
}
