package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeleteSeriesUseCaseTest {

  @Mock private SeriesOwnership seriesOwnership;
  @Mock private SeriesRepository seriesRepository;
  @Mock private PostRepository postRepository;

  private DeleteSeriesUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteSeriesUseCase(seriesOwnership, seriesRepository, postRepository);
  }

  @Test
  void detachesMembersThenDeletes() {
    SeriesEntity series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwned(7L, 5L)).thenReturn(series);

    PostEntity member = new PostEntity(7L, "p", "P", "ko");
    member.assignToSeries(5L, 0);
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of(member));

    useCase.execute(new DeleteSeriesCommand(7L, 5L));

    assertThat(member.getSeriesId()).isNull();
    verify(seriesRepository).delete(series);
  }
}
