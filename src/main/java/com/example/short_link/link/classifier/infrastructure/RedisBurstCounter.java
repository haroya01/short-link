package com.example.short_link.link.classifier.infrastructure;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.classifier.application.BurstCounter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisBurstCounter implements BurstCounter {

  private final RedisWindowCounter counter;

  @Override
  public Long increment(String key, Duration ttl) {
    return counter.increment(key, ttl);
  }
}
