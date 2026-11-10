package com.example.short_link.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class OperatorMarkerStoreIntegrationTest {

  private static final String NEVER_ISSUED = "bmV2ZXItaXNzdWVkLW1hcmtlci1pZC0x";

  @Autowired private OperatorMarkerStore store;
  @Autowired private StringRedisTemplate redis;

  private final List<String> issued = new ArrayList<>();

  @AfterEach
  void forget() {
    issued.forEach(store::revoke);
  }

  private String issue(Duration ttl) {
    String id = store.issue(ttl);
    issued.add(id);
    return id;
  }

  @Test
  void anIssuedMarkerIsValidUntilItIsRevokedAndRedisKeepsOnlyAFlagForIt() {
    String id = issue(Duration.ofMinutes(5));

    assertThat(store.isValid(id)).isTrue();
    assertThat(redis.opsForValue().get("operator-marker:" + id)).isEqualTo("1");
    assertThat(redis.getExpire("operator-marker:" + id, TimeUnit.SECONDS)).isBetween(290L, 300L);

    store.revoke(id);

    assertThat(store.isValid(id)).isFalse();
  }

  @Test
  void aMarkerStopsBeingValidWhenItsTtlRunsOut() {
    String id = issue(Duration.ofMillis(100));

    await().atMost(Duration.ofSeconds(5)).until(() -> !store.isValid(id));
  }

  @Test
  void extendingAHeldMarkerMovesItsExpiry() {
    String id = issue(Duration.ofSeconds(30));

    assertThat(store.extend(id, Duration.ofDays(14))).isTrue();

    assertThat(redis.getExpire("operator-marker:" + id, TimeUnit.SECONDS))
        .isGreaterThan(1_209_000L);
  }

  @Test
  void extendingAMarkerThatWasNeverIssuedRefusesAndDoesNotCreateIt() {
    assertThat(store.extend(NEVER_ISSUED, Duration.ofDays(14))).isFalse();

    assertThat(store.isValid(NEVER_ISSUED)).isFalse();
  }
}
