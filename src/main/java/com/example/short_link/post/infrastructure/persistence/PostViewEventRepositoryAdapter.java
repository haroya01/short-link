package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostViewEventRepositoryAdapter implements PostViewEventRepository {

  private final JpaPostViewEventRepository jpa;

  @Override
  public PostViewEventEntity save(PostViewEventEntity event) {
    return jpa.save(event);
  }

  @Override
  public List<DailyViewCount> countDailyByPostIdSince(Long postId, Instant since) {
    return jpa.countDailyByPostId(postId, since).stream()
        .map(r -> new DailyViewCount(r.getViewDate(), r.getViews()))
        .toList();
  }

  @Override
  public List<DailyViewCount> countDailyByUserIdSince(Long userId, Instant since) {
    return jpa.countDailyByUserId(userId, since).stream()
        .map(r -> new DailyViewCount(r.getViewDate(), r.getViews()))
        .toList();
  }
}
