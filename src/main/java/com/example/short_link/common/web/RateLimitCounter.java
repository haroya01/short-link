package com.example.short_link.common.web;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Owns the Redis buckets and their atomic one-minute expiry. */
@Component
@RequiredArgsConstructor
public class RateLimitCounter {

  private static final Duration WINDOW = Duration.ofMinutes(1);
  private static final RedisScript<Long> INCR_AND_EXPIRE =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return c",
          Long.class);

  private final StringRedisTemplate redis;

  public Long incrementEndpoint(String method, String path, String clientIp) {
    return increment("rate:ep:" + method + ":" + path + ":ip:" + clientIp);
  }

  public Long incrementUser(Long userId) {
    return increment("rate:user:" + userId);
  }

  public Long incrementAnonymous(String clientIp) {
    return increment("rate:ip:" + clientIp);
  }

  private Long increment(String key) {
    return redis.execute(INCR_AND_EXPIRE, List.of(key), String.valueOf(WINDOW.getSeconds()));
  }
}
