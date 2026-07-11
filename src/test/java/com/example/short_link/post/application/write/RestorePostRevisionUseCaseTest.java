package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestorePostRevisionUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private PostRevisionRepository postRevisionRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostSearchTextRepository postSearchTextRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private RestorePostRevisionUseCase useCase;

  @BeforeEach
  void setUp() {
    PostSearchTextUpdater searchTextUpdater =
        new PostSearchTextUpdater(
            postBlockRepository,
            postSearchTextRepository,
            tools.jackson.databind.json.JsonMapper.builder().build());
    useCase =
        new RestorePostRevisionUseCase(
            postOwnership,
            postRepository,
            postRevisionRepository,
            postBlockRepository,
            searchTextUpdater);
  }

  private PostRevisionEntity revision(int version, String json) {
    return new PostRevisionEntity(42L, version, "Snapshot Title", json);
  }

  @Test
  void restoresMetadataAndBlocks() throws Exception {
    PostEntity post = new PostEntity(7L, "my-post", "Current", "ko");
    String json =
        objectMapper.writeValueAsString(
            new PostSnapshot(
                "Restored",
                "Old excerpt",
                "https://cdn/og.png",
                "og.png",
                "ja",
                List.of(new PostSnapshot.BlockSnapshot("PARAGRAPH", "Old content"))));
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRevisionRepository.findByPostIdAndVersionNumber(42L, 2))
        .thenReturn(Optional.of(revision(2, json)));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity restored = useCase.execute(new RestorePostRevisionCommand(7L, 42L, 2));

    assertThat(restored.getTitle()).isEqualTo("Restored");
    assertThat(restored.getExcerpt()).isEqualTo("Old excerpt");
    assertThat(restored.getOgImageUrl()).isEqualTo("https://cdn/og.png");
    assertThat(restored.getLanguageTag()).isEqualTo("ja");
    verify(postBlockRepository).deleteAllByPostId(42L);
    verify(postBlockRepository).insertAll(anyList());
  }

  @Test
  void clearsOgWhenSnapshotNull() throws Exception {
    PostEntity post = new PostEntity(7L, "my-post", "Current", "ko");
    post.updateOgImage("https://cdn/existing.png", "existing.png");
    String json =
        objectMapper.writeValueAsString(new PostSnapshot("T", null, null, null, "ko", List.of()));
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRevisionRepository.findByPostIdAndVersionNumber(42L, 1))
        .thenReturn(Optional.of(revision(1, json)));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new RestorePostRevisionCommand(7L, 42L, 1));

    assertThat(post.getOgImageUrl()).isNull();
    assertThat(post.getOgImageKey()).isNull();
    verify(postBlockRepository).deleteAllByPostId(42L);
  }

  @Test
  void rejectsRevisionNotFound() {
    PostEntity post = new PostEntity(7L, "my-post", "Current", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRevisionRepository.findByPostIdAndVersionNumber(42L, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new RestorePostRevisionCommand(7L, 42L, 99)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.REVISION_NOT_FOUND);
  }
}
