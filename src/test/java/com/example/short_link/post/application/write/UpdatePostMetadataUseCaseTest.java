package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatePostMetadataUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;

  private UpdatePostMetadataUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdatePostMetadataUseCase(postOwnership, postRepository);
  }

  private PostEntity ownedPost() {
    return new PostEntity(7L, "original-slug", "Original", "ko");
  }

  @Test
  void updatesTitleOnly() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity updated =
        useCase.execute(
            new UpdatePostMetadataCommand(
                7L, 42L, "New Title", null, null, null, null, null, null));

    assertThat(updated.getTitle()).isEqualTo("New Title");
    assertThat(updated.getSlug()).isEqualTo("original-slug");
  }

  @Test
  void updatesExcerptAndClearsWithBlank() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, "Summary text.", null, null, null, null));
    assertThat(post.getExcerpt()).isEqualTo("Summary text.");

    useCase.execute(new UpdatePostMetadataCommand(7L, 42L, null, null, "", null, null, null, null));
    assertThat(post.getExcerpt()).isNull();
  }

  @Test
  void updatesOgImageAndClearsWithBlank() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, null, "https://cdn/og/1.png", "og/1.png", null, null));
    assertThat(post.getOgImageUrl()).isEqualTo("https://cdn/og/1.png");
    assertThat(post.getOgImageKey()).isEqualTo("og/1.png");

    useCase.execute(new UpdatePostMetadataCommand(7L, 42L, null, null, null, "", null, null, null));
    assertThat(post.getOgImageUrl()).isNull();
    assertThat(post.getOgImageKey()).isNull();
  }

  @Test
  void updatesLanguageTag() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(7L, 42L, null, null, null, null, null, "ja", null));
    assertThat(post.getLanguageTag()).isEqualTo("ja");
  }

  @Test
  void updatesTagsWithNormalization() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L,
            42L,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("  Spring ", "spring", "JPA", "")));

    // trimmed, case-insensitive dedup (first casing wins), blanks dropped
    assertThat(post.getTags()).containsExactly("Spring", "JPA");
  }

  @Test
  void clearsTagsWithEmptyList() {
    PostEntity post = ownedPost();
    post.updateTags(List.of("a", "b"));
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(7L, 42L, null, null, null, null, null, null, List.of()));

    assertThat(post.getTags()).isEmpty();
  }

  @Test
  void leavesTagsUnchangedWhenNull() {
    PostEntity post = ownedPost();
    post.updateTags(List.of("keep"));
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(7L, 42L, "T", null, null, null, null, null, null));

    assertThat(post.getTags()).containsExactly("keep");
  }

  @Test
  void updatesSlugInDraft() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "new-slug")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(7L, 42L, null, "new-slug", null, null, null, null, null));
    assertThat(post.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void rejectsSlugCollision() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "taken")).thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L, 42L, null, "taken", null, null, null, null, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_CONFLICT);
  }

  @Test
  void rejectsSlugChangeWhenFrozen() {
    PostEntity post = ownedPost();
    post.publish();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "new-slug")).thenReturn(false);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L, 42L, null, "new-slug", null, null, null, null, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_FROZEN);
  }

  @Test
  void allowsBlankTitleForDraft() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new UpdatePostMetadataCommand(7L, 42L, "", null, null, null, null, null, null));

    assertThat(post.getTitle()).isEmpty();
  }
}
