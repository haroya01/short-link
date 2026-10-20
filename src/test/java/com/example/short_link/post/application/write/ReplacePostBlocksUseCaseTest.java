package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
            noteQuotes,
            revisionCapture);
  }

  private static PostBlockEntity stored(long id, PostBlockType type, String content, int order) {
    PostBlockEntity block = new PostBlockEntity(42L, type, content, order);
    ReflectionTestUtils.setField(block, "id", id);
    return block;
  }

  private static ReplacePostBlocksCommand.BlockInput paragraph(String text) {
    return new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, text);
  }

  @Test
  void aNewBodyIsInsertedInOneStatementAndReadBackForItsIds() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    List<PostBlockEntity> persisted =
        List.of(
            stored(1, PostBlockType.PARAGRAPH, "Hello", 0),
            stored(2, PostBlockType.IMAGE, "{\"url\":\"x\"}", 1),
            stored(3, PostBlockType.DIVIDER, null, 2));
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(), persisted);

    PostBodyView result =
        useCase.execute(
            new ReplacePostBlocksCommand(
                7L,
                42L,
                List.of(
                    paragraph("Hello"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.IMAGE, "{\"url\":\"x\"}"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.DIVIDER, null)),
                null,
                false));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PostBlockEntity>> inserted = ArgumentCaptor.forClass(List.class);
    verify(postBlockRepository).insertAll(inserted.capture());
    assertThat(inserted.getValue())
        .extracting(PostBlockEntity::getType, PostBlockEntity::getBlockOrder)
        .containsExactly(
            tuple(PostBlockType.PARAGRAPH, 0),
            tuple(PostBlockType.IMAGE, 1),
            tuple(PostBlockType.DIVIDER, 2));
    verify(postBlockRepository, never()).deleteAllById(any());
    assertThat(result.blocks())
        .containsExactlyElementsOf(persisted.stream().map(PostBlockView::from).toList());
    assertThat(result.contentVersion()).isEqualTo(1L);
    verify(noteQuotes).index(post, persisted);
  }

  @Test
  void aDraftAutosaveLeavesTheSearchTextForPublishing() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(List.of(stored(1, PostBlockType.PARAGRAPH, "old", 0))));

    useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(paragraph("new")), null, false));

    verify(postSearchTextRepository, never()).upsert(any(), any());
  }

  @Test
  void aPublishedPostsSearchTextFollowsItsBody() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.publish();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(List.of(stored(1, PostBlockType.PARAGRAPH, "old", 0))));

    useCase.execute(
        new ReplacePostBlocksCommand(7L, 42L, List.of(paragraph("Hello")), null, false));

    ArgumentCaptor<String> searchText = ArgumentCaptor.forClass(String.class);
    verify(postSearchTextRepository).upsert(any(), searchText.capture());
    assertThat(searchText.getValue()).contains("My Post").contains("Hello").doesNotContain("old");
  }

  @Test
  void onlyTheBlocksThatChangedAreRewrittenInPlace() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    PostBlockEntity first = stored(1, PostBlockType.PARAGRAPH, "same", 0);
    PostBlockEntity second = stored(2, PostBlockType.PARAGRAPH, "before", 1);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(List.of(first, second)));

    PostBodyView result =
        useCase.execute(
            new ReplacePostBlocksCommand(
                7L,
                42L,
                List.of(
                    paragraph("same"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.QUOTE, "after")),
                null,
                false));

    verify(postBlockRepository, never()).insertAll(any());
    verify(postBlockRepository, never()).deleteAllById(any());
    verify(postBlockRepository, never()).deleteAllByPostId(anyLong());
    assertThat(second.getType()).isEqualTo(PostBlockType.QUOTE);
    assertThat(second.getContent()).isEqualTo("after");
    assertThat(result.blocks()).extracting(PostBlockView::id).containsExactly(1L, 2L);
  }

  @Test
  void aShorterBodyDeletesOnlyTheBlocksPastItsEnd() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    stored(1, PostBlockType.PARAGRAPH, "a", 0),
                    stored(2, PostBlockType.PARAGRAPH, "b", 1),
                    stored(3, PostBlockType.PARAGRAPH, "c", 2))));

    PostBodyView result =
        useCase.execute(
            new ReplacePostBlocksCommand(7L, 42L, List.of(paragraph("a")), null, false));

    verify(postBlockRepository).deleteAllById(List.of(2L, 3L));
    verify(postBlockRepository, never()).insertAll(any());
    assertThat(result.blocks()).extracting(PostBlockView::id).containsExactly(1L);
  }

  @Test
  void replaceWithEmptyDeletesAll() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(List.of(stored(1, PostBlockType.PARAGRAPH, "a", 0))));

    PostBodyView result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), null, false));

    verify(postBlockRepository).deleteAllById(List.of(1L));
    assertThat(result.blocks()).isEmpty();
    assertThat(result.contentVersion()).isEqualTo(1L);
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

    verify(postBlockRepository, never()).findAllByPostIdOrderByBlockOrderAsc(anyLong());
    verify(postBlockRepository, never()).deleteAllById(any());
    verify(postBlockRepository, never()).insertAll(any());
    verify(revisionCapture, never()).capture(any());
    assertThat(post.getContentVersion()).isEqualTo(2L);
    assertThat(post.getLastEditedAt()).isEqualTo(editedAt);
  }

  @Test
  void overwriteKeepsTheReplacedBodyAsARevisionBeforeDeletingIt() {
    PostEntity post = postAtVersion(5);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(new ArrayList<>(List.of(stored(1, PostBlockType.PARAGRAPH, "theirs", 0))));

    PostBodyView result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of(), 1L, true));

    InOrder order = inOrder(revisionCapture, postBlockRepository);
    order.verify(revisionCapture).capture(post);
    order.verify(postBlockRepository).deleteAllById(List.of(1L));
    assertThat(result.contentVersion()).isEqualTo(6L);
  }

  private static PostEntity postAtVersion(int version) {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    for (int i = 0; i < version; i++) post.markEdited();
    return post;
  }

  private void save(PostEntity post, String text) {
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L))
        .thenReturn(List.of(new PostBlockEntity(42L, PostBlockType.PARAGRAPH, text, 0)));
    useCase.execute(
        new ReplacePostBlocksCommand(
            7L,
            42L,
            List.of(new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, text)),
            null,
            false));
  }

  @Test
  void aSaveToAPublishedPostKeepsTheNewBodyAsAVersion() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.publish();

    save(post, "readers now see this");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PostBlockEntity>> kept = ArgumentCaptor.forClass(List.class);
    verify(revisionCapture).capture(eq(post), kept.capture());
    assertThat(kept.getValue())
        .extracting(PostBlockEntity::getContent)
        .containsExactly("readers now see this");
  }

  @Test
  void aDraftAutosaveAddsNoVersion() {
    save(new PostEntity(7L, "my-post", "My Post", "ko"), "still drafting");

    verify(revisionCapture, never()).capture(any(PostEntity.class), any());
    verify(revisionCapture, never()).capture(any(PostEntity.class));
  }
}
