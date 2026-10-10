package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.read.PostBodyView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReplacePostBlocksUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostSearchTextRepository postSearchTextRepository;
  @Mock private PostNoteQuotes noteQuotes;
  @Mock private PostRevisionCapture revisionCapture;
  @Mock private UserModerationGuard moderation;

  private ReplacePostBlocksUseCase useCase;

  @BeforeEach
  void setUp() {
    PostSearchTextUpdater searchTextUpdater =
        new PostSearchTextUpdater(
            postBlockRepository, postSearchTextRepository, JsonMapper.builder().build());
    useCase =
        new ReplacePostBlocksUseCase(
            postOwnership,
            new PostEditGuard(revisionCapture, moderation),
            postBlockRepository,
            searchTextUpdater,
            noteQuotes);
  }

  @Test
  void replacesWithNewBlocks() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    // Batch INSERT는 ID를 반환하지 않아 응답용 블록을 다시 읽는다.
    List<PostBlockEntity> persisted =
        List.of(
            new PostBlockEntity(42L, PostBlockType.PARAGRAPH, "Hello", 0),
            new PostBlockEntity(42L, PostBlockType.IMAGE, "{\"url\":\"x\"}", 1),
            new PostBlockEntity(42L, PostBlockType.DIVIDER, null, 2));
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L)).thenReturn(persisted);

    PostBodyView result =
        useCase.execute(
            new ReplacePostBlocksCommand(
                7L,
                42L,
                List.of(
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "Hello"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.IMAGE, "{\"url\":\"x\"}"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.DIVIDER, null)),
                null,
                false));

    verify(postBlockRepository).deleteAllByPostId(42L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PostBlockEntity>> inserted = ArgumentCaptor.forClass(List.class);
    verify(postBlockRepository).insertAll(inserted.capture());
    List<PostBlockEntity> built = inserted.getValue();
    assertThat(built).hasSize(3);
    assertThat(built.get(0).getType()).isEqualTo(PostBlockType.PARAGRAPH);
    assertThat(built.get(0).getBlockOrder()).isZero();
    assertThat(built.get(1).getType()).isEqualTo(PostBlockType.IMAGE);
    assertThat(built.get(1).getBlockOrder()).isEqualTo(1);
    assertThat(built.get(2).getType()).isEqualTo(PostBlockType.DIVIDER);
    assertThat(built.get(2).getBlockOrder()).isEqualTo(2);

    assertThat(result.blocks())
        .containsExactlyElementsOf(persisted.stream().map(PostBlockView::from).toList());
    assertThat(result.contentVersion()).isEqualTo(1L);

    ArgumentCaptor<String> searchText = ArgumentCaptor.forClass(String.class);
    verify(postSearchTextRepository).upsert(any(), searchText.capture());
    assertThat(searchText.getValue()).contains("My Post").contains("Hello");
    verify(noteQuotes).index(post, persisted);
  }

  @Test
  void replaceWithEmptyDeletesAll() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    PostBodyView result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), null, false));

    verify(postBlockRepository).deleteAllByPostId(42L);
    assertThat(result.blocks()).isEmpty();
    assertThat(result.contentVersion()).isEqualTo(1L);
    verify(postSearchTextRepository).upsert(any(), eq("My Post"));
    verify(noteQuotes).index(post, List.of());
  }

  @Test
  void rejectsBlocksOverMax() {
    List<ReplacePostBlocksCommand.BlockInput> tooMany =
        IntStream.range(0, 501)
            .mapToObj(
                i -> new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "block " + i))
            .toList();

    assertThatThrownBy(() -> new ReplacePostBlocksCommand(7L, 42L, tooMany, null, false))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.BODY_LIMIT);
  }

  @Test
  void rejectsForeignOwner() {
    when(postOwnership.requireOwnedForUpdate(7L, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    assertThatThrownBy(
            () -> useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), null, false)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void matchingBaseVersionReplacesTheBodyAndAdvancesTheVersion() {
    PostEntity post = postAtVersion(2);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    PostBodyView result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), 2L, false));

    verify(postBlockRepository).deleteAllByPostId(42L);
    assertThat(result.contentVersion()).isEqualTo(3L);
    assertThat(post.getContentVersion()).isEqualTo(3L);
  }

  @Test
  void staleBaseVersionIsRefusedBeforeTheBodyIsTouched() {
    PostEntity post = postAtVersion(2);
    Instant editedAt = post.getLastEditedAt();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertThatThrownBy(
            () -> useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), 1L, false)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_EDIT_CONFLICT);
              assertThat(e.properties()).containsEntry("contentVersion", 2L);
            });

    verify(postBlockRepository, never()).deleteAllByPostId(anyLong());
    verify(postBlockRepository, never()).insertAll(any());
    verify(revisionCapture, never()).capture(any());
    assertThat(post.getContentVersion()).isEqualTo(2L);
    assertThat(post.getLastEditedAt()).isEqualTo(editedAt);
  }

  @Test
  void overwriteKeepsTheReplacedBodyAsARevisionBeforeDeletingIt() {
    PostEntity post = postAtVersion(5);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    PostBodyView result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), 1L, true));

    InOrder order = inOrder(revisionCapture, postBlockRepository);
    order.verify(revisionCapture).capture(post);
    order.verify(postBlockRepository).deleteAllByPostId(42L);
    assertThat(result.contentVersion()).isEqualTo(6L);
  }

  private static PostEntity postAtVersion(int version) {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    for (int i = 0; i < version; i++) post.markEdited();
    return post;
  }
}
