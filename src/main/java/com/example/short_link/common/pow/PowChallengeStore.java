package com.example.short_link.common.pow;

import com.example.short_link.common.exception.GuardUnavailableException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
final class PowChallengeStore {
  static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
  static final String KEY_PREFIX = "pow:challenge:";

  private final StringRedisTemplate redis;

  void remember(String challenge) {
    try {
      redis.opsForValue().set(KEY_PREFIX + challenge, "1", CHALLENGE_TTL);
    } catch (DataAccessException e) {
      throw new GuardUnavailableException(e);
    }
  }

  boolean consume(String challenge) {
    try {
      // Never split this into an existence check followed by deletion: concurrent proofs can race.
      return Boolean.TRUE.equals(redis.delete(KEY_PREFIX + challenge));
    } catch (DataAccessException e) {
      throw new GuardUnavailableException(e);
    }
  }
}
