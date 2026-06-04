package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LikePostUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostLikeRepository postLikeRepository;
  @Mock private ApplicationEventPublisher events;

  private LikePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LikePostUseCase(postRepository, postLikeRepository, events);
  }

  private PostEntity post() {
    return new PostEntity(7L, "s", "T", "ko");
  }

  @Test
  void likeInsertsAndBumpsCounterWhenNew() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(post()));
    when(postLikeRepository.insertIgnore(42L, 9L)).thenReturn(1);
    when(postLikeRepository.countByPostId(42L)).thenReturn(1L);

    PostLikeStatus status = useCase.like(9L, 42L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(1);
    verify(postRepository).incrementLikeCount(42L);
    // A new like notifies the post's author (owner 7L ≠ liker 9L).
    ArgumentCaptor<BlogInteractionEvent> evt = ArgumentCaptor.forClass(BlogInteractionEvent.class);
    verify(events).publishEvent(evt.capture());
    assertThat(evt.getValue().type()).isEqualTo(BlogInteractionType.LIKE);
    assertThat(evt.getValue().recipientUserId()).isEqualTo(7L);
    assertThat(evt.getValue().actorUserId()).isEqualTo(9L);
    assertThat(evt.getValue().postId()).isEqualTo(42L);
  }

  @Test
  void newLikeOnOwnPostDoesNotNotify() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(post())); // owner 7L
    when(postLikeRepository.insertIgnore(42L, 7L)).thenReturn(1);
    when(postLikeRepository.countByPostId(42L)).thenReturn(1L);

    useCase.like(7L, 42L);

    verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void likeIsIdempotentWhenAlreadyLiked() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(post()));
    when(postLikeRepository.insertIgnore(42L, 9L)).thenReturn(0);
    when(postLikeRepository.countByPostId(42L)).thenReturn(3L);

    PostLikeStatus status = useCase.like(9L, 42L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(3);
    verify(postRepository, never()).incrementLikeCount(anyLong());
    verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unlikeRemovesAndDropsCounterWhenLiked() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(post()));
    when(postLikeRepository.deleteByPostIdAndUserId(42L, 9L)).thenReturn(1);
    when(postLikeRepository.countByPostId(42L)).thenReturn(0L);

    PostLikeStatus status = useCase.unlike(9L, 42L);

    assertThat(status.liked()).isFalse();
    assertThat(status.likeCount()).isZero();
    verify(postRepository).decrementLikeCount(42L);
  }

  @Test
  void unlikeIsNoopWhenNotLiked() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(post()));
    when(postLikeRepository.deleteByPostIdAndUserId(42L, 9L)).thenReturn(0);
    when(postLikeRepository.countByPostId(42L)).thenReturn(5L);

    PostLikeStatus status = useCase.unlike(9L, 42L);

    assertThat(status.liked()).isFalse();
    assertThat(status.likeCount()).isEqualTo(5);
    verify(postRepository, never()).decrementLikeCount(anyLong());
  }

  @Test
  void rejectsMissingPost() {
    when(postRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.like(9L, 42L)).isInstanceOf(PostException.class);
  }
}
