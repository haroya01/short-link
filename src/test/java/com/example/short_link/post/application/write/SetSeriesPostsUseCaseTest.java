package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
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
  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;

  private SetSeriesPostsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetSeriesPostsUseCase(seriesOwnership, postOwnership, postRepository);
  }

  private PostEntity postWithId(long id) {
    PostEntity p = new PostEntity(7L, "p" + id, "P" + id, "ko");
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  @Test
  void assignsOrderDetachesDroppedAndReorders() {
    SeriesEntity series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwned(7L, 5L)).thenReturn(series);

    PostEntity p1 = postWithId(1L); // currently in series, gets dropped
    p1.assignToSeries(5L, 0);
    PostEntity p2 = postWithId(2L); // stays, moves to order 0
    p2.assignToSeries(5L, 1);
    PostEntity p3 = postWithId(3L); // new, order 1
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of(p1, p2));
    when(postOwnership.requireOwned(7L, 2L)).thenReturn(p2);
    when(postOwnership.requireOwned(7L, 3L)).thenReturn(p3);

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(2L, 3L)));

    assertThat(p1.getSeriesId()).isNull();
    assertThat(p1.getSeriesOrder()).isNull();
    assertThat(p2.getSeriesId()).isEqualTo(5L);
    assertThat(p2.getSeriesOrder()).isEqualTo(0);
    assertThat(p3.getSeriesId()).isEqualTo(5L);
    assertThat(p3.getSeriesOrder()).isEqualTo(1);
  }
}
