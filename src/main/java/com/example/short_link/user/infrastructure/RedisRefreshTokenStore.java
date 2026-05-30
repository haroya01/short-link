package com.example.short_link.user.infrastructure;

import com.example.short_link.user.application.write.RefreshTokenStore;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final long SCAN_BATCH = 100;

  private final StringRedisTemplate redis;

  @Override
  public void save(Long userId, String jti, Duration ttl) {
    redis.opsForValue().set(key(userId, jti), "1", ttl);
  }

  @Override
  public boolean exists(Long userId, String jti) {
    return Boolean.TRUE.equals(redis.hasKey(key(userId, jti)));
  }

  @Override
  public void delete(Long userId, String jti) {
    redis.delete(key(userId, jti));
  }

  @Override
  public void markRotated(Long userId, String jti, Duration graceTtl) {
    redis.opsForValue().set(rotatedKey(userId, jti), "1", graceTtl);
  }

  @Override
  public boolean wasRecentlyRotated(Long userId, String jti) {
    return Boolean.TRUE.equals(redis.hasKey(rotatedKey(userId, jti)));
  }

  @Override
  public void deleteAllForUser(Long userId) {
    Set<String> toDelete = new HashSet<>();
    ScanOptions opts =
        ScanOptions.scanOptions().match(userPrefix(userId) + "*").count(SCAN_BATCH).build();
    try (Cursor<String> cursor = redis.scan(opts)) {
      while (cursor.hasNext()) {
        toDelete.add(cursor.next());
      }
    }
    if (!toDelete.isEmpty()) {
      redis.delete(toDelete);
    }
  }

  private String key(Long userId, String jti) {
    return userPrefix(userId) + jti;
  }

  private String userPrefix(Long userId) {
    return "refresh:" + userId + ":";
  }

  // Separate prefix so the grace marker isn't swept by deleteAllForUser's "refresh:{id}:*" scan —
  // it expires on its own short TTL, so a theft wipe never needs to clear it.
  private String rotatedKey(Long userId, String jti) {
    return "refresh-rotated:" + userId + ":" + jti;
  }
}
