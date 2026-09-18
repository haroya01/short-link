package com.example.short_link.common.counter;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 고정 창 카운터. 증가와 만료를 서버에서 한 스크립트로 처리한다. INCR 과 EXPIRE 를 따로 보내면 그 사이 연결이 끊겼을 때 만료 없는 키가 남아 카운터가 영원히
 * 줄지 않는다. 만료 없이 남아 있던 키도 다음 증가·조회 때 창을 다시 건다.
 */
@Component
@RequiredArgsConstructor
public class RedisWindowCounter {

  private static final RedisScript<Long> INCREMENT =
      new DefaultRedisScript<>(
          "local count = redis.call('INCR', KEYS[1]) "
              + "if redis.call('PTTL', KEYS[1]) == -1 then "
              + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
              + "return count",
          Long.class);

  private static final RedisScript<Long> CURRENT =
      new DefaultRedisScript<>(
          "local count = redis.call('GET', KEYS[1]) "
              + "if not count then return 0 end "
              + "if redis.call('PTTL', KEYS[1]) == -1 then "
              + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
              + "return tonumber(count)",
          Long.class);

  private final StringRedisTemplate redis;

  public long increment(String key, Duration window) {
    return run(INCREMENT, key, window);
  }

  public long current(String key, Duration window) {
    return run(CURRENT, key, window);
  }

  public void reset(String key) {
    redis.delete(key);
  }

  private long run(RedisScript<Long> script, String key, Duration window) {
    Long count = redis.execute(script, List.of(key), String.valueOf(window.toMillis()));
    return count == null ? 0 : count;
  }
}
