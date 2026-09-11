package com.example.short_link.common.pow;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Remembers issued challenges for five minutes and consumes them with one atomic Redis delete. */
@Component
@RequiredArgsConstructor
final class PowChallengeStore {
  static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
  static final String KEY_PREFIX = "pow:challenge:";

  private final StringRedisTemplate redis;

  void remember(String challenge) {
    redis.opsForValue().set(KEY_PREFIX + challenge, "1", CHALLENGE_TTL);
  }

  boolean consume(String challenge) {
    // Never split this into an existence check followed by deletion: concurrent proofs can race.
    return Boolean.TRUE.equals(redis.delete(KEY_PREFIX + challenge));
  }
}
