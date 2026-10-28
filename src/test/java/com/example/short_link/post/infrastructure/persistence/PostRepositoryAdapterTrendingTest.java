package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class PostRepositoryAdapterTrendingTest {

  private final JpaPostRepository jpa = mock(JpaPostRepository.class);
  private final TrendingPostRanking ranking = mock(TrendingPostRanking.class);
  private final PostRepositoryAdapter adapter =
      new PostRepositoryAdapter(jpa, ranking, Clock.systemUTC());

  private static List<Long> ranked(int size) {
    return LongStream.rangeClosed(1, size).boxed().toList();
  }

  private static List<PostEntity> posts(int size) {
    return LongStream.range(0, size).mapToObj(i -> mock(PostEntity.class)).toList();
  }

  private void shown(List<PostEntity> page) {
    when(jpa.findPublishedRankedAmong(
            any(), any(), any(), anyInt(), anyLong(), any(Instant.class), any()))
        .thenReturn(page);
  }

  @Test
  void aPageWithinTheRankingIsCutFromItForTheReader() {
    List<Long> ids = ranked(TrendingPostRanking.SIZE);
    when(ranking.ids(null)).thenReturn(ids);
    List<PostEntity> page = posts(21);
    shown(page);

    assertThat(adapter.findPublishedTrendingAt(7L, 40, 21)).isSameAs(page);

    verify(jpa)
        .findPublishedRankedAmong(
            eq(ids),
            eq(ids.stream().map(String::valueOf).collect(Collectors.joining(","))),
            isNull(),
            anyInt(),
            eq(7L),
            any(Instant.class),
            eq(new OffsetLimit(40, 21)));
    verify(jpa, never())
        .findPublishedTrendingSince(any(), any(), anyInt(), anyLong(), any(), any());
  }

  @Test
  void aShortPageOfAFullRankingIsRankedLive() {
    when(ranking.ids(null)).thenReturn(ranked(TrendingPostRanking.SIZE));
    shown(posts(3));
    List<PostEntity> live = posts(21);
    when(jpa.findPublishedTrendingSince(
            any(Instant.class), isNull(), anyInt(), eq(7L), any(Instant.class), any()))
        .thenReturn(live);

    assertThat(adapter.findPublishedTrendingAt(7L, 190, 21)).isSameAs(live);

    verify(jpa)
        .findPublishedTrendingSince(
            any(Instant.class),
            isNull(),
            anyInt(),
            eq(7L),
            any(Instant.class),
            eq(new OffsetLimit(190, 21)));
  }

  @Test
  void aShortPageOfARankingThatHoldsEveryPostIsTheEnd() {
    when(ranking.ids(null)).thenReturn(ranked(5));
    List<PostEntity> page = posts(2);
    shown(page);

    assertThat(adapter.findPublishedTrendingAt(null, 3, 21)).isSameAs(page);

    verify(jpa, never())
        .findPublishedTrendingSince(any(), any(), anyInt(), anyLong(), any(), any());
  }

  @Test
  void anEmptyRankingAsksNothingMore() {
    when(ranking.ids(null)).thenReturn(List.of());

    assertThat(adapter.findPublishedTrendingAt(null, 0, 21)).isEmpty();

    verify(jpa, never())
        .findPublishedRankedAmong(any(), any(), any(), anyInt(), anyLong(), any(), any());
    verify(jpa, never())
        .findPublishedTrendingSince(any(), any(), anyInt(), anyLong(), any(), any());
  }

  @Test
  void theDiscoveryPageReadsTheRankingOfItsLanguage() {
    List<Long> ids = ranked(TrendingPostRanking.SIZE);
    when(ranking.ids("ko")).thenReturn(ids);
    shown(posts(20));

    adapter.findPublishedTrending(null, " ko ", 2, 20);

    verify(jpa)
        .findPublishedRankedAmong(
            eq(ids),
            any(),
            eq("ko"),
            anyInt(),
            eq(-1L),
            any(Instant.class),
            eq(new OffsetLimit(40, 20)));
  }
}
