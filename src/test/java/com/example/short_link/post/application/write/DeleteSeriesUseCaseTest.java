package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);

    PostEntity member = new PostEntity(7L, "p", "P", "ko");
    member.assignToSeries(5L, 0);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of(member));

    useCase.execute(new DeleteSeriesCommand(7L, 5L));

    assertThat(member.getSeriesId()).isNull();
    InOrder writes = inOrder(seriesOwnership, postRepository, seriesRepository);
    writes.verify(seriesOwnership).requireOwnedForUpdate(7L, 5L);
    writes.verify(postRepository).findSeriesMembersAndRequestedForUpdate(5L, List.of());
    writes.verify(postRepository).save(member);
    writes.verify(seriesRepository).delete(series);
    writes.verifyNoMoreInteractions();
  }
}
