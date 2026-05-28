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
class CreateSeriesUseCaseTest {

  @Mock private SeriesRepository seriesRepository;

  private CreateSeriesUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateSeriesUseCase(seriesRepository);
  }

  @Test
  void createsSeries() {
    when(seriesRepository.existsByUserIdAndSlug(7L, "my-series")).thenReturn(false);
    when(seriesRepository.save(any(SeriesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    SeriesEntity created = useCase.execute(new CreateSeriesCommand(7L, "my-series", "My Series"));

    assertThat(created.getSlug()).isEqualTo("my-series");
    assertThat(created.getTitle()).isEqualTo("My Series");
    assertThat(created.getUserId()).isEqualTo(7L);
  }

  @Test
  void rejectsSlugConflict() {
    when(seriesRepository.existsByUserIdAndSlug(7L, "taken")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateSeriesCommand(7L, "taken", "T")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SERIES_SLUG_CONFLICT);
  }
}
