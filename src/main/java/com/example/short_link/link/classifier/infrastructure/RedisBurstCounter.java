package com.example.short_link.link.classifier.infrastructure;

import com.example.short_link.link.classifier.application.BurstCounter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisBurstCounter implements BurstCounter {

  private final StringRedisTemplate redis;

  @Override
  public Long increment(String key, Duration ttl) {
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, ttl);
    }
    return count;
  }
}
