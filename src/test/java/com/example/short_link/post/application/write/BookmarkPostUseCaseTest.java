package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.PostBookmarkStatus;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookmarkPostUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostBookmarkRepository postBookmarkRepository;

  private BookmarkPostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new BookmarkPostUseCase(postRepository, postBookmarkRepository);
  }

  @Test
  void bookmarksWhenNew() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));
    when(postBookmarkRepository.insertIgnore(42L, 9L)).thenReturn(1);

    PostBookmarkStatus status = useCase.bookmark(9L, 42L);

    assertThat(status.bookmarked()).isTrue();
    verify(postBookmarkRepository).insertIgnore(42L, 9L);
  }

  @Test
  void bookmarkIsIdempotent() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));
    when(postBookmarkRepository.insertIgnore(42L, 9L)).thenReturn(0); // already bookmarked → no-op

    PostBookmarkStatus status = useCase.bookmark(9L, 42L);

    assertThat(status.bookmarked()).isTrue();
    verify(postBookmarkRepository).insertIgnore(42L, 9L);
  }

  @Test
  void bookmarkUnknownPostThrows() {
    when(postRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.bookmark(9L, 99L)).isInstanceOf(PostException.class);
    verify(postBookmarkRepository, never()).insertIgnore(any(), any());
  }

  @Test
  void unbookmarkDeletesWhenPresent() {
    when(postBookmarkRepository.findByPostIdAndUserId(42L, 9L))
        .thenReturn(Optional.of(new PostBookmarkEntity(42L, 9L)));

    PostBookmarkStatus status = useCase.unbookmark(9L, 42L);

    assertThat(status.bookmarked()).isFalse();
    verify(postBookmarkRepository).delete(any(PostBookmarkEntity.class));
  }

  @Test
  void unbookmarkNoOpWhenAbsent() {
    when(postBookmarkRepository.findByPostIdAndUserId(42L, 9L)).thenReturn(Optional.empty());

    PostBookmarkStatus status = useCase.unbookmark(9L, 42L);

    assertThat(status.bookmarked()).isFalse();
    verify(postBookmarkRepository, never()).delete(any());
  }
}
