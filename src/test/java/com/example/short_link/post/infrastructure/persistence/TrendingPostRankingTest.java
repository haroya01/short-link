package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendingPostRankingTest {

  private JpaPostRepository jpa(List<Long> ids) {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    when(jpa.findPublishedTrendingIdsSince(
            any(Instant.class), any(), anyInt(), anyLong(), any(Instant.class), any()))
        .thenReturn(ids);
    return jpa;
  }

  @Test
  void readersWithinCacheForShareOneRankingRankedAsNobody() {
    JpaPostRepository jpa = jpa(List.of(7L, 3L));
    TrendingPostRanking ranking = new TrendingPostRanking(jpa, Duration.ofSeconds(20));

    assertThat(ranking.ids(null)).containsExactly(7L, 3L);
    assertThat(ranking.ids(null)).containsExactly(7L, 3L);

    verify(jpa, times(1))
        .findPublishedTrendingIdsSince(
            any(Instant.class),
            isNull(),
            anyInt(),
            eq(-1L),
            any(Instant.class),
            eq(new OffsetLimit(0, TrendingPostRanking.SIZE)));
  }

  @Test
  void eachLanguageHasItsOwnRanking() {
    JpaPostRepository jpa = jpa(List.of(7L));
    TrendingPostRanking ranking = new TrendingPostRanking(jpa, Duration.ofSeconds(20));

    ranking.ids(null);
    ranking.ids("ko");
    ranking.ids("ko");

    verify(jpa, times(1))
        .findPublishedTrendingIdsSince(
            any(Instant.class), isNull(), anyInt(), anyLong(), any(Instant.class), any());
    verify(jpa, times(1))
        .findPublishedTrendingIdsSince(
            any(Instant.class), eq("ko"), anyInt(), anyLong(), any(Instant.class), any());
  }

  @Test
  void aZeroCacheForRanksOnEveryRead() {
    JpaPostRepository jpa = jpa(List.of(7L));
    TrendingPostRanking ranking = new TrendingPostRanking(jpa, Duration.ZERO);

    ranking.ids(null);
    ranking.ids(null);

    verify(jpa, times(2))
        .findPublishedTrendingIdsSince(
            any(Instant.class), any(), anyInt(), anyLong(), any(Instant.class), any());
  }
}
