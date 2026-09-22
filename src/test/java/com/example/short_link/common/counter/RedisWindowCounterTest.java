package com.example.short_link.common.counter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.access.infrastructure.LinkPasswordAttemptLimiter;
import com.example.short_link.link.classifier.infrastructure.RedisBurstCounter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisWindowCounterTest {
  private static final Duration MINUTE = Duration.ofMinutes(1);

  @Autowired private RedisConnectionFactory connections;
  @Autowired private StringRedisTemplate redis;
  @Autowired private RedisWindowCounter counter;

  private final List<String> keys = new ArrayList<>();

  @AfterEach
  void deleteKeys() {
    redis.delete(keys);
  }

  @Test
  void aPasswordLockoutStillExpiresWhenTheConnectionDropsBeforeTheExpiry() {
    LinkPasswordAttemptLimiter limiter =
        new LinkPasswordAttemptLimiter(new RedisWindowCounter(expiryDropped()));
    String code = "p" + UUID.randomUUID().toString().substring(0, 8);
    String ip = "203.0.113.9";
    String key = key("pwd-attempt:" + code + ":" + ip);

    for (int i = 0; i < 10; i++) {
      limiter.recordFailure(code, ip);
    }

    assertThat(limiter.isLockedOut(code, ip)).isTrue();
    assertThat(redis.getExpire(key)).isPositive();
  }

  @Test
  void aBurstCounterStillExpiresWhenTheConnectionDropsBeforeTheExpiry() {
    RedisBurstCounter burst = new RedisBurstCounter(new RedisWindowCounter(expiryDropped()));
    String key = key("bot:click:ip:test-" + UUID.randomUUID());

    burst.increment(key, Duration.ofSeconds(1));

    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
  }

  @Test
  void aLockoutLeftWithoutExpiryByTheOldCodeExpiresOnTheNextCheck() {
    LinkPasswordAttemptLimiter limiter = new LinkPasswordAttemptLimiter(counter);
    String code = "p" + UUID.randomUUID().toString().substring(0, 8);
    String ip = "203.0.113.10";
    String key = key("pwd-attempt:" + code + ":" + ip);
    redis.opsForValue().set(key, "10");

    assertThat(limiter.isLockedOut(code, ip)).isTrue();

    assertThat(redis.getExpire(key)).isBetween(1L, Duration.ofMinutes(15).toSeconds());
  }

  @Test
  void aCounterLeftWithoutExpiryIsGivenAWindowOnTheNextIncrement() {
    String key = key("rate:ip:test-" + UUID.randomUUID());
    redis.opsForValue().set(key, "7");

    assertThat(counter.increment(key, MINUTE)).isEqualTo(8);
    assertThat(redis.getExpire(key)).isBetween(1L, MINUTE.toSeconds());
  }

  @Test
  void laterIncrementsKeepTheWindowThatTheFirstOneOpened() {
    String key = key("rate:ip:test-" + UUID.randomUUID());
    counter.increment(key, MINUTE);
    redis.expire(key, Duration.ofSeconds(1));

    counter.increment(key, MINUTE);

    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 1_000L);
  }

  @Test
  void checkingTheCounterDoesNotPushTheWindowBack() {
    LinkPasswordAttemptLimiter limiter = new LinkPasswordAttemptLimiter(counter);
    String code = "p" + UUID.randomUUID().toString().substring(0, 8);
    String ip = "203.0.113.11";
    String key = key("pwd-attempt:" + code + ":" + ip);
    for (int i = 0; i < 10; i++) {
      limiter.recordFailure(code, ip);
    }
    redis.expire(key, Duration.ofSeconds(10));

    for (int i = 0; i < 5; i++) {
      assertThat(limiter.isLockedOut(code, ip)).isTrue();
    }

    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 10_000L);
  }

  @Test
  void aWindowShorterThanASecondIsNotRoundedDownToImmediateDeletion() {
    String key = key("bot:click:ip:test-" + UUID.randomUUID());

    assertThat(counter.increment(key, Duration.ofMillis(500))).isEqualTo(1);

    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 500L);
  }

  @Test
  void concurrentIncrementsAreNeitherLostNorLeftWithoutExpiry() throws Exception {
    String key = key("rate:ip:test-" + UUID.randomUUID());
    List<Callable<Long>> increments =
        IntStream.range(0, 200)
            .mapToObj(i -> (Callable<Long>) () -> counter.increment(key, MINUTE))
            .toList();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      for (Future<Long> done : pool.invokeAll(increments)) {
        done.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(redis.opsForValue().get(key)).isEqualTo("200");
    assertThat(redis.getExpire(key)).isPositive();
  }

  private String key(String key) {
    keys.add(key);
    return key;
  }

  /** A connection that stays up for INCR but drops before a separate EXPIRE reaches the server. */
  private StringRedisTemplate expiryDropped() {
    return new StringRedisTemplate(connections) {
      @Override
      public Boolean expire(String key, long timeout, TimeUnit unit) {
        throw new RedisConnectionFailureException("connection dropped before EXPIRE");
      }

      @Override
      public Boolean expire(String key, Duration timeout) {
        throw new RedisConnectionFailureException("connection dropped before EXPIRE");
      }
    };
  }
}
