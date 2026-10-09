package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.write.SetSeriesItemsCommand.Item;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetSeriesItemsUseCaseTest {

  private static final Instant AT = Instant.parse("2026-10-09T00:00:00Z");

  @Mock private SeriesOwnership seriesOwnership;
  @Mock private PostRepository postRepository;
  @Mock private SeriesItemRepository seriesItemRepository;
  @Mock private SeriesItemReader seriesItemReader;

  private SetSeriesItemsUseCase useCase;
  private SeriesEntity series;

  @BeforeEach
  void setUp() {
    useCase =
        new SetSeriesItemsUseCase(
            seriesOwnership, postRepository, seriesItemRepository, seriesItemReader);
    series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
  }

  private PostEntity postWithId(long id) {
    PostEntity p = new PostEntity(7L, "p" + id, "P" + id, "ko");
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private static SeriesNote note(long id, long authorId, boolean shared) {
    return new SeriesNote(id, authorId, "body " + id, null, AT, shared);
  }

  @SuppressWarnings("unchecked")
  private List<SeriesItemEntity> replacedRows() {
    ArgumentCaptor<List<SeriesItemEntity>> rows = ArgumentCaptor.forClass(List.class);
    verify(seriesItemRepository).replace(eq(5L), rows.capture());
    return rows.getValue();
  }

  @Test
  void postsAndNotesShareOneOrderAndPostsMirrorTheirPlace() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity p1 = postWithId(1L);
    PostEntity p2 = postWithId(2L);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(1L, 2L)))
        .thenReturn(List.of(p1, p2));
    when(seriesItemReader.notes(List.of(40L))).thenReturn(Map.of(40L, note(40L, 7L, true)));

    useCase.execute(
        new SetSeriesItemsCommand(
            7L,
            5L,
            List.of(
                new Item(SeriesItemType.POST, 1L),
                new Item(SeriesItemType.NOTE, 40L),
                new Item(SeriesItemType.POST, 2L))));

    assertThat(p1.getSeriesId()).isEqualTo(5L);
    assertThat(p1.getSeriesOrder()).isEqualTo(0);
    assertThat(p2.getSeriesOrder()).isEqualTo(2);
    assertThat(replacedRows())
        .extracting(
            SeriesItemEntity::getSeriesId,
            SeriesItemEntity::getType,
            SeriesItemEntity::getRefId,
            SeriesItemEntity::getPosition)
        .containsExactly(
            tuple(5L, SeriesItemType.POST, 1L, 0),
            tuple(5L, SeriesItemType.NOTE, 40L, 1),
            tuple(5L, SeriesItemType.POST, 2L, 2));
  }

  @Test
  void detachesDroppedPostsBeforeAssigningAndReplacesItemsLast() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity p1 = postWithId(1L);
    p1.assignToSeries(5L, 1);
    PostEntity p2 = postWithId(2L);
    p2.assignToSeries(5L, 0);
    PostEntity p3 = postWithId(3L);
    p3.assignToSeries(6L, 4);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(3L, 2L)))
        .thenReturn(List.of(p1, p2, p3));

    useCase.execute(
        new SetSeriesItemsCommand(
            7L, 5L, List.of(new Item(SeriesItemType.POST, 3L), new Item(SeriesItemType.POST, 2L))));

    assertThat(p1.getSeriesId()).isNull();
    assertThat(p1.getSeriesOrder()).isNull();
    assertThat(p2.getSeriesOrder()).isEqualTo(1);
    assertThat(p3.getSeriesId()).isEqualTo(5L);
    assertThat(p3.getSeriesOrder()).isEqualTo(0);
    InOrder writes = inOrder(seriesOwnership, postRepository, seriesItemRepository);
    writes.verify(seriesOwnership).requireOwnedForUpdate(7L, 5L);
    writes.verify(postRepository).findSeriesMembersAndRequestedForUpdate(5L, List.of(3L, 2L));
    writes.verify(postRepository).save(p1);
    writes.verify(postRepository).save(p3);
    writes.verify(postRepository).save(p2);
    writes.verify(seriesItemRepository).replace(eq(5L), any());
    writes.verifyNoMoreInteractions();
  }

  @Test
  void anEmptyListEmptiesTheSeries() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity member = postWithId(1L);
    member.assignToSeries(5L, 0);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of(member));

    useCase.execute(new SetSeriesItemsCommand(7L, 5L, List.of()));

    assertThat(member.getSeriesId()).isNull();
    assertThat(replacedRows()).isEmpty();
  }

  @Test
  void aMissingPostChangesNothing() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity member = postWithId(1L);
    member.assignToSeries(5L, 0);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(99L)))
        .thenReturn(List.of(member));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SetSeriesItemsCommand(7L, 5L, List.of(new Item(SeriesItemType.POST, 99L)))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
              assertThat(error.getMessage()).isEqualTo(PostErrorCode.POST_NOT_FOUND.format(99L));
            });

    assertThat(member.getSeriesId()).isEqualTo(5L);
    verify(postRepository, never()).save(any());
    verify(seriesItemRepository, never()).replace(anyLong(), any());
  }

  @Test
  void someoneElsesPostChangesNothing() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity foreign = new PostEntity(8L, "foreign", "Foreign", "ko");
    ReflectionTestUtils.setField(foreign, "id", 3L);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(3L)))
        .thenReturn(List.of(foreign));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SetSeriesItemsCommand(7L, 5L, List.of(new Item(SeriesItemType.POST, 3L)))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo(PostErrorCode.PERMISSION_DENIED);
              assertThat(error.properties()).containsEntry("postId", 3L);
            });

    assertThat(foreign.getSeriesId()).isNull();
    verify(seriesItemRepository, never()).replace(anyLong(), any());
  }

  @Test
  void aMissingNoteIsNotFound() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of());
    when(seriesItemReader.notes(List.of(40L))).thenReturn(Map.of());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SetSeriesItemsCommand(7L, 5L, List.of(new Item(SeriesItemType.NOTE, 40L)))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> assertThat(error.errorCode()).isEqualTo(PostErrorCode.SERIES_NOTE_NOT_FOUND));
    verify(seriesItemRepository, never()).replace(anyLong(), any());
  }

  @Test
  void someoneElsesNoteIsDenied() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of());
    when(seriesItemReader.notes(List.of(40L))).thenReturn(Map.of(40L, note(40L, 8L, true)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SetSeriesItemsCommand(7L, 5L, List.of(new Item(SeriesItemType.NOTE, 40L)))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo(PostErrorCode.PERMISSION_DENIED);
              assertThat(error.properties()).containsEntry("noteId", 40L);
            });
    verify(seriesItemRepository, never()).replace(anyLong(), any());
  }

  @Test
  void aNoteOnlyFollowersCanReadCannotJoin() {
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of());
    when(seriesItemReader.notes(List.of(40L))).thenReturn(Map.of(40L, note(40L, 7L, false)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SetSeriesItemsCommand(7L, 5L, List.of(new Item(SeriesItemType.NOTE, 40L)))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> assertThat(error.errorCode()).isEqualTo(PostErrorCode.SERIES_NOTE_NOT_SHARED));
    verify(seriesItemRepository, never()).replace(anyLong(), any());
  }

  @Test
  void theCommandRejectsDuplicatesAndBlanks() {
    assertThatThrownBy(
            () ->
                new SetSeriesItemsCommand(
                    7L,
                    5L,
                    List.of(new Item(SeriesItemType.NOTE, 1L), new Item(SeriesItemType.NOTE, 1L))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Item(null, 1L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Item(SeriesItemType.POST, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SetSeriesItemsCommand(null, 5L, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SetSeriesItemsCommand(7L, null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new SetSeriesItemsCommand(7L, 5L, null).items()).isEmpty();
    assertThat(
            new SetSeriesItemsCommand(
                    7L,
                    5L,
                    List.of(new Item(SeriesItemType.NOTE, 1L), new Item(SeriesItemType.POST, 1L)))
                .items())
        .hasSize(2);
  }
}
