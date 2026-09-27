package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.PostView;
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
  @Mock private UserModerationGuard moderationGuard;

  private CreatePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CreatePostUseCase(
            postRepository, moderationGuard, new PostWriteViewAssembler(postRepository));
  }

  @Test
  void createsDraftPost() {
    when(postRepository.existsByUserIdAndSlug(7L, "first-post")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView created = useCase.execute(new CreatePostCommand(7L, "first-post", "First Post", "ko"));

    verify(postRepository)
        .save(org.mockito.ArgumentMatchers.argThat(post -> post.getUserId().equals(7L)));
    assertThat(created.slug()).isEqualTo("first-post");
    assertThat(created.title()).isEqualTo("First Post");
    assertThat(created.status()).isEqualTo(PostStatus.DRAFT.name());
    assertThat(created.languageTag()).isEqualTo("ko");
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
  void allowsEmptyTitleForDraft() {
    when(postRepository.existsByUserIdAndSlug(7L, "valid-slug")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView created = useCase.execute(new CreatePostCommand(7L, "valid-slug", "", "ko"));

    assertThat(created.title()).isEmpty();
    assertThat(created.status()).isEqualTo(PostStatus.DRAFT.name());
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

    PostView created = useCase.execute(new CreatePostCommand(7L, "default-lang", "Title", null));
    assertThat(created.languageTag()).isEqualTo("ko");
  }
}
