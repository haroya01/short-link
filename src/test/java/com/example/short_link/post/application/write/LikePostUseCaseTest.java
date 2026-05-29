package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikePostUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostLikeRepository postLikeRepository;

  private LikePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LikePostUseCase(postRepository, postLikeRepository);
  }

  @Test
  void likeIncrementsCountWhenNew() {
    PostEntity post = new PostEntity(7L, "s", "T", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));
    when(postLikeRepository.existsByPostIdAndUserId(42L, 9L)).thenReturn(false);

    PostLikeStatus status = useCase.like(9L, 42L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(1);
    verify(postLikeRepository).save(any(PostLikeEntity.class));
  }

  @Test
  void likeIsIdempotent() {
    PostEntity post = new PostEntity(7L, "s", "T", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));
    when(postLikeRepository.existsByPostIdAndUserId(42L, 9L)).thenReturn(true);

    PostLikeStatus status = useCase.like(9L, 42L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isZero();
    verify(postLikeRepository, never()).save(any());
  }

  @Test
  void unlikeDecrementsWhenLiked() {
    PostEntity post = new PostEntity(7L, "s", "T", "ko");
    post.incrementLikeCount();
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));
    when(postLikeRepository.findByPostIdAndUserId(42L, 9L))
        .thenReturn(Optional.of(new PostLikeEntity(42L, 9L)));

    PostLikeStatus status = useCase.unlike(9L, 42L);

    assertThat(status.liked()).isFalse();
    assertThat(status.likeCount()).isZero();
    verify(postLikeRepository).delete(any(PostLikeEntity.class));
  }
}
