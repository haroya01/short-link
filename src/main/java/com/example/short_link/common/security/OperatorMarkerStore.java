package com.example.short_link.common.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorMarkerStore {

  public static final String COOKIE_NAME = "kurl_op";

  private static final String KEY_PREFIX = "operator-marker:";
  private static final int ID_BYTES = 24;
  private static final Pattern ID_SHAPE = Pattern.compile("[A-Za-z0-9_-]{32}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;

  public String issue(Duration ttl) {
    byte[] bytes = new byte[ID_BYTES];
    RANDOM.nextBytes(bytes);
    String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis.opsForValue().set(KEY_PREFIX + id, "1", ttl);
    return id;
  }

  public boolean extend(String id, Duration ttl) {
    return wellFormed(id)
        && Boolean.TRUE.equals(redis.opsForValue().setIfPresent(KEY_PREFIX + id, "1", ttl));
  }

  public void revoke(String id) {
    if (wellFormed(id)) {
      redis.delete(KEY_PREFIX + id);
    }
  }

  public boolean isValid(String id) {
    if (!wellFormed(id)) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + id));
    } catch (DataAccessException e) {
      log.warn("operator marker lookup failed, counting the visit as a person's: {}", e.toString());
      return false;
    }
  }

  private static boolean wellFormed(String id) {
    return id != null && ID_SHAPE.matcher(id).matches();
  }
}
