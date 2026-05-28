package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateSeriesUseCaseTest {

  @Mock private SeriesOwnership seriesOwnership;
  @Mock private SeriesRepository seriesRepository;

  private UpdateSeriesUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateSeriesUseCase(seriesOwnership, seriesRepository);
  }

  private SeriesEntity owned() {
    return new SeriesEntity(7L, "old-slug", "Old Title");
  }

  @Test
  void updatesTitleAndSlug() {
    SeriesEntity series = owned();
    when(seriesOwnership.requireOwned(7L, 5L)).thenReturn(series);
    when(seriesRepository.existsByUserIdAndSlug(7L, "new-slug")).thenReturn(false);
    when(seriesRepository.save(any(SeriesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new UpdateSeriesCommand(7L, 5L, "New Title", "new-slug"));

    assertThat(series.getTitle()).isEqualTo("New Title");
    assertThat(series.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void rejectsSlugConflict() {
    SeriesEntity series = owned();
    when(seriesOwnership.requireOwned(7L, 5L)).thenReturn(series);
    when(seriesRepository.existsByUserIdAndSlug(7L, "taken")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new UpdateSeriesCommand(7L, 5L, null, "taken")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SERIES_SLUG_CONFLICT);
  }
}
