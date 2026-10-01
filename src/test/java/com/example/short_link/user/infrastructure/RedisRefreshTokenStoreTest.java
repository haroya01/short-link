package com.example.short_link.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.write.RefreshTokenStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisRefreshTokenStoreTest {

  private static final Long USER_ID = 42L;
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Duration GRACE = Duration.ofSeconds(60);
  private static final int THREADS = 8;
  private static final int ROUNDS = 50;

  @Autowired private RefreshTokenStore store;
  @Autowired private StringRedisTemplate redis;

  private final List<String> keys = new ArrayList<>();

  private record Attempt(boolean consumed, boolean sawMarker) {}

  @AfterEach
  void deleteKeys() {
    redis.delete(keys);
  }

  @Test
  void consumeRemovesTheTokenAndLeavesAnExpiringMarker() {
    String jti = savedToken();

    assertThat(store.consume(USER_ID, jti, GRACE)).isTrue();

    assertThat(store.exists(USER_ID, jti)).isFalse();
    assertThat(store.wasRecentlyRotated(USER_ID, jti)).isTrue();
    assertThat(redis.getExpire(markerKey(jti), TimeUnit.MILLISECONDS))
        .isBetween(1L, GRACE.toMillis());
  }

  @Test
  void aTokenIsConsumedOnlyOnce() {
    String jti = savedToken();

    assertThat(store.consume(USER_ID, jti, GRACE)).isTrue();
    assertThat(store.consume(USER_ID, jti, GRACE)).isFalse();
  }

  @Test
  void consumingAnUnknownTokenLeavesNoMarker() {
    String jti = newJti();

    assertThat(store.consume(USER_ID, jti, GRACE)).isFalse();

    assertThat(store.wasRecentlyRotated(USER_ID, jti)).isFalse();
  }

  @Test
  void aRejectedMarkerWriteLeavesTheTokenLive() {
    String jti = savedToken();

    // Redis rejects a zero expiry, so the marker SET fails inside the script.
    assertThatThrownBy(() -> store.consume(USER_ID, jti, Duration.ZERO))
        .isInstanceOf(DataAccessException.class);

    assertThat(store.exists(USER_ID, jti)).isTrue();
    assertThat(store.wasRecentlyRotated(USER_ID, jti)).isFalse();
  }

  @Test
  void concurrentConsumesOfOneTokenHaveOneWinnerAndEveryLoserFindsTheMarker() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        String jti = savedToken();
        CyclicBarrier together = new CyclicBarrier(THREADS);
        Callable<Attempt> attempt =
            () -> {
              together.await(10, TimeUnit.SECONDS);
              boolean consumed = store.consume(USER_ID, jti, GRACE);
              return new Attempt(consumed, store.wasRecentlyRotated(USER_ID, jti));
            };

        List<Attempt> attempts = new ArrayList<>();
        for (Future<Attempt> done : pool.invokeAll(Collections.nCopies(THREADS, attempt))) {
          attempts.add(done.get(15, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(Attempt::consumed).hasSize(1);
        assertThat(attempts).allMatch(Attempt::sawMarker);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private String savedToken() {
    String jti = newJti();
    store.save(USER_ID, jti, TTL);
    return jti;
  }

  private String newJti() {
    String jti = UUID.randomUUID().toString();
    keys.add("refresh:" + USER_ID + ":" + jti);
    keys.add(markerKey(jti));
    return jti;
  }

  private String markerKey(String jti) {
    return "refresh-rotated:" + USER_ID + ":" + jti;
  }
}
