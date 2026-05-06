package com.example.short_link.user.application;

import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

  private final StringRedisTemplate redis;

  public void save(Long userId, String jti, Duration ttl) {
    redis.opsForValue().set(key(userId, jti), "1", ttl);
  }

  public boolean exists(Long userId, String jti) {
    return Boolean.TRUE.equals(redis.hasKey(key(userId, jti)));
  }

  public void delete(Long userId, String jti) {
    redis.delete(key(userId, jti));
  }

  public void deleteAllForUser(Long userId) {
    Set<String> keys = redis.keys(userPrefix(userId) + "*");
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  private String key(Long userId, String jti) {
    return userPrefix(userId) + jti;
  }

  private String userPrefix(Long userId) {
    return "refresh:" + userId + ":";
  }
}
