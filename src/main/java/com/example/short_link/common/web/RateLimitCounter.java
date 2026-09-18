package com.example.short_link.common.web;

import com.example.short_link.common.counter.RedisWindowCounter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owns the Redis buckets and their atomic one-minute expiry. */
@Component
@RequiredArgsConstructor
public class RateLimitCounter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final RedisWindowCounter counter;

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
    return counter.increment(key, WINDOW);
  }
}
