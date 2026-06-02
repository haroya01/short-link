package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeriesOwnershipTest {

  @Mock private SeriesRepository seriesRepository;
  @InjectMocks private SeriesOwnership seriesOwnership;

  private static final long USER = 7L;
  private static final long SERIES_ID = 3L;

  @Test
  void throwsWhenSeriesNotFound() {
    when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> seriesOwnership.requireOwned(USER, SERIES_ID))
        .isInstanceOf(PostException.class);
  }

  @Test
  void throwsWhenSeriesOwnedByAnotherUser() {
    SeriesEntity series = mock(SeriesEntity.class);
    when(series.isOwnedBy(USER)).thenReturn(false);
    when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

    assertThatThrownBy(() -> seriesOwnership.requireOwned(USER, SERIES_ID))
        .isInstanceOf(PostException.class);
  }

  @Test
  void returnsSeriesWhenOwned() {
    SeriesEntity series = mock(SeriesEntity.class);
    when(series.isOwnedBy(USER)).thenReturn(true);
    when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

    assertThat(seriesOwnership.requireOwned(USER, SERIES_ID)).isSameAs(series);
  }
}
