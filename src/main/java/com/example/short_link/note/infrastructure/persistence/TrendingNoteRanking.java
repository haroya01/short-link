package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.discovery.TrendingSpread;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// One ranking serves every reader for cacheFor or longer, and each request only filters it. It
// holds ids alone: what a note says and who may see it are read again on every request.
@Component
class TrendingNoteRanking {

  static final int SIZE = 200;

  static final int REST_PER_WORK = 30;

  private final TrendingSpread spread;
  private final Duration cacheFor;
  private final Ticker ticker;
  private final LoadingCache<Boolean, List<Long>> cache;

  @PersistenceContext private EntityManager em;

  @Autowired
  TrendingNoteRanking(
      TrendingSpread spread, @Value("${short-link.trending.cache-for:20s}") Duration cacheFor) {
    this(spread, cacheFor, Ticker.systemTicker(), ForkJoinPool.commonPool());
  }

  TrendingNoteRanking(TrendingSpread spread, Duration cacheFor, Ticker ticker, Executor executor) {
    this.spread = spread;
    this.cacheFor = cacheFor;
    this.ticker = ticker;
    this.cache =
        cacheFor.isZero()
            ? null
            : Caffeine.newBuilder()
                .ticker(ticker)
                .executor(executor)
                .refreshAfterWrite(cacheFor)
                .expireAfterWrite(cacheFor.multipliedBy(6))
                .build(key -> paced());
  }

  List<Long> ids() {
    return cache == null ? load() : cache.get(Boolean.TRUE);
  }

  private List<Long> paced() {
    long started = ticker.read();
    List<Long> ids = load();
    Duration every = Duration.ofNanos(ticker.read() - started).multipliedBy(REST_PER_WORK);
    Duration refresh = every.compareTo(cacheFor) > 0 ? every : cacheFor;
    cache.policy().refreshAfterWrite().ifPresent(policy -> policy.setRefreshesAfter(refresh));
    cache
        .policy()
        .expireAfterWrite()
        .ifPresent(policy -> policy.setExpiresAfter(refresh.multipliedBy(6)));
    return ids;
  }

  @SuppressWarnings("unchecked")
  private List<Long> load() {
    List<Number> rows =
        em.createNativeQuery(NoteSql.trendingSql("o.id", ""))
            .setParameter("perAuthor", spread.perAuthor())
            .setParameter("since", Instant.now().minus(NoteSql.TRENDING_WINDOW))
            .setParameter("viewer", NoteSql.viewer(null))
            .setParameter("now", Instant.now())
            .setMaxResults(SIZE)
            .getResultList();
    return rows.stream().map(Number::longValue).toList();
  }
}
