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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePostUseCaseTest {

  @Mock private PostRepository postRepository;

  private CreatePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreatePostUseCase(postRepository);
  }

  @Test
  void createsDraftPost() {
    when(postRepository.existsByUserIdAndSlug(7L, "first-post")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity created =
        useCase.execute(new CreatePostCommand(7L, "first-post", "First Post", "ko"));

    assertThat(created.getUserId()).isEqualTo(7L);
    assertThat(created.getSlug()).isEqualTo("first-post");
    assertThat(created.getTitle()).isEqualTo("First Post");
    assertThat(created.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(created.getLanguageTag()).isEqualTo("ko");
  }

  @Test
  void rejectsSlugCollision() {
    when(postRepository.existsByUserIdAndSlug(7L, "taken")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreatePostCommand(7L, "taken", "Title", "ko")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_CONFLICT);
  }

  @Test
  void rejectsInvalidSlug() {
    assertThatThrownBy(
            () -> useCase.execute(new CreatePostCommand(7L, "Invalid Slug!", "Title", "ko")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEmptyTitle() {
    assertThatThrownBy(() -> useCase.execute(new CreatePostCommand(7L, "valid-slug", "", "ko")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnsupportedLanguage() {
    assertThatThrownBy(
            () -> useCase.execute(new CreatePostCommand(7L, "valid-slug", "Title", "zh")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultsLanguageWhenBlank() {
    when(postRepository.existsByUserIdAndSlug(7L, "default-lang")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity created = useCase.execute(new CreatePostCommand(7L, "default-lang", "Title", null));
    assertThat(created.getLanguageTag()).isEqualTo("ko");
  }
}
