package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.ReferrerViewCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Append-only log of public post views — the source the trending feed windows over. */
public interface PostViewEventRepository {

  PostViewEventEntity save(PostViewEventEntity event);

  /** Per-day view counts for one post since {@code since} (sparse — empty days omitted). */
  List<DailyViewCount> countDailyByPostIdSince(Long postId, Instant since);

  /** Per-day view counts across all of an author's posts since {@code since} (sparse). */
  List<DailyViewCount> countDailyByUserIdSince(Long userId, Instant since);

  /**
   * Top referrer hosts across all of an author's posts since {@code since}, views-desc. 사람 조회만 집계하고
   * direct(레퍼러 없음)는 제외 — 개요 대시보드의 "유입 경로" 행.
   */
  List<ReferrerViewCount> topReferrerHostsByUserSince(Long userId, Instant since, int limit);

  /**
   * Distinct human reader fingerprints (visitor_hash) per post, keyed by post id. Lifetime — the
   * series read-through funnel intersects adjacent episodes' reader sets. Posts with no readers are
   * absent from the map.
   */
  Map<Long, Set<String>> readersByPostId(Collection<Long> postIds);
}
