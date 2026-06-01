package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostViewEventEntity;
import java.time.Instant;
import java.util.List;

/** Append-only log of public post views — the source the trending feed windows over. */
public interface PostViewEventRepository {

  PostViewEventEntity save(PostViewEventEntity event);

  /** Per-day view counts for one post since {@code since} (sparse — empty days omitted). */
  List<DailyViewCount> countDailyByPostIdSince(Long postId, Instant since);

  /** Per-day view counts across all of an author's posts since {@code since} (sparse). */
  List<DailyViewCount> countDailyByUserIdSince(Long userId, Instant since);
}
