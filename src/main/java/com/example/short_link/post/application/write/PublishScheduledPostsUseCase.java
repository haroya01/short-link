package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.repository.PostRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 글별 트랜잭션으로 발행하며, 한 글이 실패해도 나머지 예약 글은 계속 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishScheduledPostsUseCase {

  private final PostRepository postRepository;
  private final PublishScheduledPostUseCase publishScheduledPost;
  private final ScheduledPublicationBackoff backoff;
  private final MeterRegistry meterRegistry;

  public int execute(Instant now) {
    List<Long> due = postRepository.findScheduledDueIds(now);
    backoff.forgetAllExcept(due);
    int published = 0;
    for (Long postId : due) {
      if (!backoff.isDue(postId, now)) continue;
      try {
        if (publishScheduledPost.execute(postId, now)) published++;
        backoff.recordSuccess(postId);
      } catch (RuntimeException e) {
        ScheduledPublicationBackoff.Failure failure = backoff.recordFailure(postId, now);
        meterRegistry.counter("short_link.post.scheduled_publish.failed").increment();
        log.warn(
            "scheduled publish failed for post {} (attempt {}, next at {}): {}",
            postId,
            failure.attempts(),
            failure.retryAt(),
            e.getMessage());
      }
    }
    return published;
  }
}
