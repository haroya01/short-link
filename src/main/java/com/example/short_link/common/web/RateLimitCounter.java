package com.example.short_link.common.web;

import com.example.short_link.common.counter.RedisWindowCounter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitCounter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final RedisWindowCounter counter;

  public Long incrementEndpoint(String method, String path, String clientNetwork) {
    return increment("rate:ep:" + method + ":" + path + ":ip:" + clientNetwork);
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
