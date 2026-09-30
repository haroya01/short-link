package com.example.short_link.user.infrastructure;

import com.example.short_link.user.application.write.RefreshTokenStore;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final long SCAN_BATCH = 100;

  // Sent as separate commands, two refreshes of one token can both pass the check and both rotate,
  // or the loser can look for the marker before the winner has written it. The marker is written
  // before the delete so a rejected SET leaves the token usable.
  private static final RedisScript<Long> CONSUME =
      new DefaultRedisScript<>(
          "if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end "
              + "redis.call('SET', KEYS[2], '1', 'PX', ARGV[1]) "
              + "redis.call('DEL', KEYS[1]) "
              + "return 1",
          Long.class);

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
  public boolean consume(Long userId, String jti, Duration graceTtl) {
    Long consumed =
        redis.execute(
            CONSUME,
            List.of(key(userId, jti), rotatedKey(userId, jti)),
            String.valueOf(graceTtl.toMillis()));
    return Long.valueOf(1L).equals(consumed);
  }

  @Override
  public void delete(Long userId, String jti) {
    redis.delete(key(userId, jti));
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

  // Grace markers use a separate prefix, survive deleteAllForUser, and expire on their own TTL.
  private String rotatedKey(Long userId, String jti) {
    return "refresh-rotated:" + userId + ":" + jti;
  }
}
