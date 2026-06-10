package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentLikeRepositoryAdapterTest {

  @Mock private JpaCommentLikeRepository jpa;

  private CommentLikeRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CommentLikeRepositoryAdapter(jpa);
  }

  private JpaCommentLikeRepository.CommentLikeCount count(long commentId, long cnt) {
    return new JpaCommentLikeRepository.CommentLikeCount() {
      @Override
      public Long getCommentId() {
        return commentId;
      }

      @Override
      public Long getCnt() {
        return cnt;
      }
    };
  }

  @Test
  void countByCommentIdsMapsGroupedRows() {
    when(jpa.countGroupedByCommentId(List.of(1L, 2L)))
        .thenReturn(List.of(count(1L, 3L), count(2L, 1L)));

    assertThat(adapter.countByCommentIds(List.of(1L, 2L))).isEqualTo(Map.of(1L, 3L, 2L, 1L));
  }

  @Test
  void emptyIdListsShortCircuitWithoutQuerying() {
    assertThat(adapter.countByCommentIds(List.of())).isEmpty();
    assertThat(adapter.findLikedCommentIds(9L, List.of())).isEmpty();
    verifyNoInteractions(jpa);
  }

  @Test
  void delegatesSimpleOperations() {
    when(jpa.insertIgnore(1L, 9L)).thenReturn(1);
    when(jpa.deleteByCommentIdAndUserId(1L, 9L)).thenReturn(1);
    when(jpa.countByCommentId(1L)).thenReturn(5L);
    when(jpa.findLikedCommentIds(9L, List.of(1L))).thenReturn(List.of(1L));

    assertThat(adapter.insertIgnore(1L, 9L)).isEqualTo(1);
    assertThat(adapter.deleteByCommentIdAndUserId(1L, 9L)).isEqualTo(1);
    assertThat(adapter.countByCommentId(1L)).isEqualTo(5L);
    assertThat(adapter.findLikedCommentIds(9L, List.of(1L))).containsExactly(1L);
  }
}
