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

  List<DailyViewCount> countDailyByPostIdSince(Long postId, Instant since);

  List<DailyViewCount> countDailyByUserIdSince(Long userId, Instant since);

  List<ReferrerViewCount> topReferrerHostsByUserSince(Long userId, Instant since, int limit);

  Map<Long, Long> countHumanViewsSince(Collection<Long> postIds, Instant since);

  Map<Long, Set<String>> readersByPostId(Collection<Long> postIds);
}
