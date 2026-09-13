package com.example.short_link.common.lock;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SET-NX-EX lock for scheduled jobs; has no fencing tokens or auto-renewal. Use only when skipping
 * a contended run is acceptable.
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
