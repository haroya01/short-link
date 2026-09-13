package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetSeriesPostsUseCaseTest {

  @Mock private SeriesOwnership seriesOwnership;
  @Mock private PostRepository postRepository;

  private SetSeriesPostsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetSeriesPostsUseCase(seriesOwnership, postRepository);
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
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);

    PostEntity p1 = postWithId(1L); // currently in series, gets dropped
    p1.assignToSeries(5L, 1);
    PostEntity p2 = postWithId(2L); // stays, moves to order 1
    p2.assignToSeries(5L, 0);
    PostEntity p3 = postWithId(3L); // new, order 0
    p3.assignToSeries(6L, 4);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(3L, 2L)))
        .thenReturn(List.of(p1, p2, p3));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(3L, 2L)));

    assertThat(p1.getSeriesId()).isNull();
    assertThat(p1.getSeriesOrder()).isNull();
    assertThat(p2.getSeriesId()).isEqualTo(5L);
    assertThat(p2.getSeriesOrder()).isEqualTo(1);
    assertThat(p3.getSeriesId()).isEqualTo(5L);
    assertThat(p3.getSeriesOrder()).isEqualTo(0);

    InOrder writes = inOrder(seriesOwnership, postRepository);
    writes.verify(seriesOwnership).requireOwnedForUpdate(7L, 5L);
    writes.verify(postRepository).findSeriesMembersAndRequestedForUpdate(5L, List.of(3L, 2L));
    writes.verify(postRepository).save(p1);
    writes.verify(postRepository).save(p3);
    writes.verify(postRepository).save(p2);
    writes.verifyNoMoreInteractions();
  }

  @Test
  void clearsAllMembersWhenRequestedListIsEmpty() {
    SeriesEntity series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity member = postWithId(1L);
    member.assignToSeries(5L, 0);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of()))
        .thenReturn(List.of(member));

    useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of()));

    assertThat(member.getSeriesId()).isNull();
    assertThat(member.getSeriesOrder()).isNull();
  }

  @Test
  void missingRequestedPostKeepsNotFoundContractBeforeChangingMembership() {
    SeriesEntity series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity member = postWithId(1L);
    member.assignToSeries(5L, 0);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(99L)))
        .thenReturn(List.of(member));

    assertThatThrownBy(() -> useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(99L))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
              assertThat(error.getMessage()).isEqualTo(PostErrorCode.POST_NOT_FOUND.format(99L));
            });

    assertThat(member.getSeriesId()).isEqualTo(5L);
    verify(postRepository, never()).save(any());
  }

  @Test
  void foreignRequestedPostKeepsPermissionContextBeforeChangingMembership() {
    SeriesEntity series = new SeriesEntity(7L, "s", "S");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesOwnership.requireOwnedForUpdate(7L, 5L)).thenReturn(series);
    PostEntity member = postWithId(1L);
    member.assignToSeries(5L, 0);
    PostEntity foreign = new PostEntity(8L, "foreign", "Foreign", "ko");
    ReflectionTestUtils.setField(foreign, "id", 3L);
    when(postRepository.findSeriesMembersAndRequestedForUpdate(5L, List.of(3L)))
        .thenReturn(List.of(member, foreign));

    assertThatThrownBy(() -> useCase.execute(new SetSeriesPostsCommand(7L, 5L, List.of(3L))))
        .isInstanceOfSatisfying(
            PostException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo(PostErrorCode.PERMISSION_DENIED);
              assertThat(error.properties()).containsEntry("postId", 3L);
            });

    assertThat(member.getSeriesId()).isEqualTo(5L);
    assertThat(foreign.getSeriesId()).isNull();
    verify(postRepository, never()).save(any());
  }
}
