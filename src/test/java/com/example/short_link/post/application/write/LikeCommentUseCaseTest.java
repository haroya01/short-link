package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.application.read.CommentLikeStatus;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeCommentUseCaseTest {

  @Mock private CommentRepository commentRepository;
  @Mock private CommentLikeRepository commentLikeRepository;

  private LikeCommentUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LikeCommentUseCase(commentRepository, commentLikeRepository);
  }

  private CommentEntity comment() {
    return new CommentEntity(3L, 7L, null, "nice");
  }

  @Test
  void likeIsIdempotentAndReturnsAuthoritativeCount() {
    Mockito.when(commentRepository.findById(10L)).thenReturn(Optional.of(comment()));
    Mockito.when(commentLikeRepository.insertIgnore(10L, 9L)).thenReturn(1);
    Mockito.when(commentLikeRepository.countByCommentId(10L)).thenReturn(4L);

    CommentLikeStatus status = useCase.like(9L, 10L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(4L);
  }

  @Test
  void duplicateLikeStillReturnsLikedWithCurrentCount() {
    Mockito.when(commentRepository.findById(10L)).thenReturn(Optional.of(comment()));
    Mockito.when(commentLikeRepository.insertIgnore(10L, 9L)).thenReturn(0);
    Mockito.when(commentLikeRepository.countByCommentId(10L)).thenReturn(4L);

    CommentLikeStatus status = useCase.like(9L, 10L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(4L);
  }

  @Test
  void unlikeRemovesAndReturnsCount() {
    Mockito.when(commentRepository.findById(10L)).thenReturn(Optional.of(comment()));
    Mockito.when(commentLikeRepository.deleteByCommentIdAndUserId(10L, 9L)).thenReturn(1);
    Mockito.when(commentLikeRepository.countByCommentId(10L)).thenReturn(3L);

    CommentLikeStatus status = useCase.unlike(9L, 10L);

    assertThat(status.liked()).isFalse();
    assertThat(status.likeCount()).isEqualTo(3L);
  }

  @Test
  void likeUnknownCommentThrows() {
    Mockito.when(commentRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.like(9L, 99L)).isInstanceOf(PostException.class);
  }
}
