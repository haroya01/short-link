package com.example.short_link.link.access.infrastructure;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-(shortCode, client-IP) failed-password limiter for the unlock endpoint. The global per-IP
 * rate limit (100/min across all endpoints) is too loose to stop a focused brute-force against one
 * short link's password; this locks an IP out of a specific code after a handful of misses, for a
 * cooldown window. Only actual password failures count — a correct password resets the counter.
 */
@Component
@RequiredArgsConstructor
public class LinkPasswordAttemptLimiter {

  static final int MAX_FAILURES = 10;
  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final StringRedisTemplate redis;

  public boolean isLockedOut(String shortCode, String clientIp) {
    String raw = redis.opsForValue().get(key(shortCode, clientIp));
    return raw != null && Integer.parseInt(raw) >= MAX_FAILURES;
  }

  public void recordFailure(String shortCode, String clientIp) {
    String k = key(shortCode, clientIp);
    Long count = redis.opsForValue().increment(k);
    if (count != null && count == 1L) {
      redis.expire(k, WINDOW);
    }
  }

  public void reset(String shortCode, String clientIp) {
    redis.delete(key(shortCode, clientIp));
  }

  private static String key(String shortCode, String clientIp) {
    return "pwd-attempt:" + shortCode + ":" + clientIp;
  }
}
