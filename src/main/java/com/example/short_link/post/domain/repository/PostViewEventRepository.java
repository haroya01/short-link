package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.ReferrerViewCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PostViewEventRepository {

  PostViewEventEntity save(PostViewEventEntity event);

  /** Per-UTC-day counts; days without views are omitted. */
  List<DailyViewCount> countDailyByPostIdSince(Long postId, Instant since);

  /** Counts across the author's posts per UTC day; days without views are omitted. */
  List<DailyViewCount> countDailyByUserIdSince(Long userId, Instant since);

  /** 사람 조회만 집계하고 direct 유입은 제외한다. 조회수 내림차순이다. */
  List<ReferrerViewCount> topReferrerHostsByUserSince(Long userId, Instant since, int limit);

  /** Lifetime distinct human visitor hashes, keyed by post ID. Posts with no readers are absent. */
  Map<Long, Set<String>> readersByPostId(Collection<Long> postIds);
}
