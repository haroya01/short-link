package com.example.short_link.post.application.write;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.write.SetSeriesItemsCommand.Item;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetSeriesPostsUseCaseTest {

  @Mock private SeriesOwnership seriesOwnership;
  @Mock private SeriesItemRepository seriesItemRepository;
  @Mock private SetSeriesItemsUseCase setSeriesItems;

  private SetSeriesPostsUseCase useCase;
  private SeriesEntity series;

  @BeforeEach
  void setUp() {
    useCase = new SetSeriesPostsUseCase(seriesOwnership, seriesItemRepository, setSeriesItems);
    series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
  }

  private static SeriesItemEntity row(SeriesItemType type, long refId, int position) {
    return new SeriesItemEntity(5L, type, refId, position);
  }

  private static Item post(long id) {
    return new Item(SeriesItemType.POST, id);
  }

  private static Item note(long id) {
    return new Item(SeriesItemType.NOTE, id);
  }

  @Test
  void notesKeepTheirPlacesWhilePostsFillThePostPlacesInTheGivenOrder() {
    when(seriesItemRepository.findBySeriesId(5L))
        .thenReturn(
            List.of(
                row(SeriesItemType.POST, 1L, 0),
                row(SeriesItemType.NOTE, 40L, 1),
                row(SeriesItemType.POST, 2L, 2),
                row(SeriesItemType.NOTE, 41L, 3)));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(2L, 1L)));

    verify(setSeriesItems).write(series, 7L, List.of(post(2L), note(40L), post(1L), note(41L)));
  }

  @Test
  void extraPostsJoinAtTheEnd() {
    when(seriesItemRepository.findBySeriesId(5L))
        .thenReturn(List.of(row(SeriesItemType.NOTE, 40L, 0), row(SeriesItemType.POST, 1L, 1)));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(1L, 3L, 4L)));

    verify(setSeriesItems).write(series, 7L, List.of(note(40L), post(1L), post(3L), post(4L)));
  }

  @Test
  void fewerPostsLeaveTheNotesAlone() {
    when(seriesItemRepository.findBySeriesId(5L))
        .thenReturn(
            List.of(
                row(SeriesItemType.POST, 1L, 0),
                row(SeriesItemType.POST, 2L, 1),
                row(SeriesItemType.NOTE, 40L, 2)));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of()));

    verify(setSeriesItems).write(series, 7L, List.of(note(40L)));
  }

  @Test
  void aPostLeavingTheSeriesTakesItsOwnPlaceAndTheRestStayAroundTheNotes() {
    when(seriesItemRepository.findBySeriesId(5L))
        .thenReturn(
            List.of(
                row(SeriesItemType.POST, 1L, 0),
                row(SeriesItemType.NOTE, 40L, 1),
                row(SeriesItemType.POST, 2L, 2),
                row(SeriesItemType.NOTE, 41L, 3),
                row(SeriesItemType.POST, 3L, 4)));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(2L, 3L)));

    verify(setSeriesItems).write(series, 7L, List.of(note(40L), post(2L), note(41L), post(3L)));
  }
}
