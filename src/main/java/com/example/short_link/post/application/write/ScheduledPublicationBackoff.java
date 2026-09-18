package com.example.short_link.post.application.write;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 발행에 실패한 예약 글은 1·2·4…분 간격으로, 최대 1시간마다 다시 시도한다. 실패 기록은 프로세스 메모리에만 있어 재시작하면 바로 다시 시도한다. */
@Component
class ScheduledPublicationBackoff {

  static final Duration FIRST_DELAY = Duration.ofMinutes(1);
  static final Duration MAX_DELAY = Duration.ofHours(1);

  private final Map<Long, Failure> failures = new ConcurrentHashMap<>();

  boolean isDue(Long postId, Instant now) {
    Failure failure = failures.get(postId);
    return failure == null || !now.isBefore(failure.retryAt());
  }

  Failure recordFailure(Long postId, Instant now) {
    return failures.merge(postId, Failure.first(now), (previous, ignored) -> previous.next(now));
  }

  void recordSuccess(Long postId) {
    failures.remove(postId);
  }

  void forgetAllExcept(Collection<Long> dueIds) {
    failures.keySet().retainAll(Set.copyOf(dueIds));
  }

  record Failure(int attempts, Instant retryAt) {

    static Failure first(Instant now) {
      return new Failure(1, now.plus(FIRST_DELAY));
    }

    Failure next(Instant now) {
      int attempts = this.attempts + 1;
      Duration delay = FIRST_DELAY.multipliedBy(1L << Math.min(attempts - 1, 6));
      return new Failure(attempts, now.plus(delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay));
    }
  }
}
