package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.discovery.TrendingSpread;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The trending order is the same for everyone up to what a reader may not see, so one ranking
// serves every reader for cacheFor and each request only filters it. It holds ids alone: what a
// note says and who may see it are read again on every request.
@Component
class TrendingNoteRanking {

  static final int SIZE = 200;

  private final TrendingSpread spread;
  private final LoadingCache<Boolean, List<Long>> cache;

  @PersistenceContext private EntityManager em;

  TrendingNoteRanking(
      TrendingSpread spread, @Value("${short-link.trending.cache-for:20s}") Duration cacheFor) {
    this.spread = spread;
    this.cache =
        cacheFor.isZero()
            ? null
            : Caffeine.newBuilder()
                .refreshAfterWrite(cacheFor)
                .expireAfterWrite(cacheFor.multipliedBy(6))
                .build(key -> load());
  }

  List<Long> ids() {
    return cache == null ? load() : cache.get(Boolean.TRUE);
  }

  @SuppressWarnings("unchecked")
  private List<Long> load() {
    List<Number> rows =
        em.createNativeQuery(NoteRepositoryAdapter.trendingSql("o.id", ""))
            .setParameter("perAuthor", spread.perAuthor())
            .setParameter("since", Instant.now().minus(NoteRepositoryAdapter.TRENDING_WINDOW))
            .setParameter("viewer", NoteRepositoryAdapter.viewer(null))
            .setParameter("now", Instant.now())
            .setMaxResults(SIZE)
            .getResultList();
    return rows.stream().map(Number::longValue).toList();
  }
}
