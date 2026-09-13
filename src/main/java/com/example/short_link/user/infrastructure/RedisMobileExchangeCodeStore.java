package com.example.short_link.user.infrastructure;

import com.example.short_link.user.application.write.MobileExchangeCodeStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisMobileExchangeCodeStore implements MobileExchangeCodeStore {

  // Allow the app to return from the browser while limiting replay exposure from a leaked code.
  private static final Duration TTL = Duration.ofSeconds(60);

  private final StringRedisTemplate redis;

  @Override
  public String create(Long userId) {
    String code = UUID.randomUUID().toString();
    redis.opsForValue().set(key(code), String.valueOf(userId), TTL);
    return code;
  }

  @Override
  public Optional<Long> consume(String code) {
    // GETDEL keeps redeem-and-burn atomic — two racing exchanges can't both win.
    String userId = redis.opsForValue().getAndDelete(key(code));
    if (userId == null) return Optional.empty();
    try {
      return Optional.of(Long.parseLong(userId));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private String key(String code) {
    return "mobile-exchange:" + code;
  }
}
