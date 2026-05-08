package com.example.short_link.common.lock;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tiny SET-NX-EX based lock for fencing scheduled jobs across multiple app instances. Not a
 * full-fledged distributed lock — no fencing tokens, no auto-renewal. Safe enough for "only one
 * task runs the daily cleanup" use cases where doing nothing in a contended window is fine.
 */
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

  private final StringRedisTemplate redis;

  public boolean tryAcquire(String key, Duration ttl) {
    Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", ttl);
    return Boolean.TRUE.equals(acquired);
  }

  public void release(String key) {
    redis.delete(key);
  }
}
