package archfixtures;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Breaks the Redis counter rule on purpose so the rule can be shown to catch it. */
public final class RedisFixtures {

  private RedisFixtures() {}

  public static class IncrementsThenExpires {
    public void run(StringRedisTemplate redis) {
      Long count = redis.opsForValue().increment("k");
      if (count != null && count == 1L) {
        redis.expire("k", Duration.ofSeconds(1));
      }
    }
  }

  public static class RunsOneScript {
    private static final RedisScript<Long> INCREMENT =
        new DefaultRedisScript<>("return redis.call('INCR', KEYS[1])", Long.class);

    public Long run(StringRedisTemplate redis) {
      return redis.execute(INCREMENT, List.of("k"), "1000");
    }
  }
}
