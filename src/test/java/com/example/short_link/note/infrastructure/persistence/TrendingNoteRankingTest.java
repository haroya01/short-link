package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.discovery.TrendingSpread;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrendingNoteRankingTest {

  private EntityManager em(List<?> rows) {
    EntityManager em = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.setMaxResults(anyInt())).thenReturn(query);
    when(query.getResultList()).thenReturn(rows);
    return em;
  }

  private TrendingNoteRanking ranking(Duration cacheFor, EntityManager em) {
    TrendingNoteRanking ranking = new TrendingNoteRanking(new TrendingSpread(2), cacheFor);
    ReflectionTestUtils.setField(ranking, "em", em);
    return ranking;
  }

  @Test
  void readersWithinCacheForShareOneRanking() {
    EntityManager em = em(List.of(7L, 3L));
    TrendingNoteRanking ranking = ranking(Duration.ofSeconds(20), em);

    assertThat(ranking.ids()).containsExactly(7L, 3L);
    assertThat(ranking.ids()).containsExactly(7L, 3L);

    verify(em, times(1)).createNativeQuery(anyString());
  }

  @Test
  void aZeroCacheForRanksOnEveryRead() {
    EntityManager em = em(List.of(7L));
    TrendingNoteRanking ranking = ranking(Duration.ZERO, em);

    ranking.ids();
    ranking.ids();

    verify(em, times(2)).createNativeQuery(anyString());
  }
}
