package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Ranked as a reader with no blocks or mutes sees it, so one ranking per language serves every
// reader for cacheFor. It holds ids alone: what a post says and who may see it are read again on
// every request.
@Component
class TrendingPostRanking {

  static final int SIZE = 200;

  private static final int LANGUAGES = 32;

  private final JpaPostRepository jpa;
  private final LoadingCache<String, List<Long>> cache;

  TrendingPostRanking(
      JpaPostRepository jpa, @Value("${short-link.trending.cache-for:20s}") Duration cacheFor) {
    this.jpa = jpa;
    this.cache =
        cacheFor.isZero()
            ? null
            : Caffeine.newBuilder()
                .maximumSize(LANGUAGES)
                .refreshAfterWrite(cacheFor)
                .expireAfterWrite(cacheFor.multipliedBy(6))
                .build(this::load);
  }

  List<Long> ids(String lang) {
    String key = lang == null ? "" : lang;
    return cache == null ? load(key) : cache.get(key);
  }

  private List<Long> load(String key) {
    Instant now = Instant.now();
    return jpa.findPublishedTrendingIdsSince(
        now.minus(PostRepositoryAdapter.TRENDING_WINDOW),
        key.isEmpty() ? null : key,
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        HeardSql.viewer(null),
        now,
        new OffsetLimit(0, SIZE));
  }
}
