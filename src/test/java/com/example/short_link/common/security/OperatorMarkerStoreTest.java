package com.example.short_link.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OperatorMarkerStoreTest {

  private static final Duration TTL = Duration.ofDays(14);
  private static final String ID = "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXIt";
  private static final String KEY = "operator-marker:" + ID;

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final OperatorMarkerStore store = new OperatorMarkerStore(redis);

  @Test
  void anIssuedMarkerIs192RandomBitsInBase64UrlKeptWithItsTtlAndNamesNoAccount() {
    when(redis.opsForValue()).thenReturn(values);

    String id = store.issue(TTL);

    assertThat(id).matches("[A-Za-z0-9_-]{32}");
    assertThat(Base64.getUrlDecoder().decode(id)).hasSize(24);
    verify(values).set("operator-marker:" + id, "1", TTL);
  }

  @Test
  void noTwoIssuedMarkersAreTheSame() {
    when(redis.opsForValue()).thenReturn(values);
    Set<String> issued = new HashSet<>();

    for (int i = 0; i < 200; i++) {
      issued.add(store.issue(TTL));
    }

    assertThat(issued).hasSize(200);
  }

  @Test
  void aMarkerRedisHoldsIsValid() {
    when(redis.hasKey(KEY)).thenReturn(true);

    assertThat(store.isValid(ID)).isTrue();
  }

  @Test
  void aMarkerRedisNoLongerHoldsIsNotValid() {
    when(redis.hasKey(KEY)).thenReturn(false);

    assertThat(store.isValid(ID)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "1",
        "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXI",
        "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXItA",
        "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXI*",
        "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXI ",
        "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXI\n",
        "operator-marker:Zm9yLXRoZS1vcGVy"
      })
  void aValueNotShapedLikeAMarkerNeverReachesRedis(String value) {
    assertThat(store.isValid(value)).isFalse();
    assertThat(store.extend(value, TTL)).isFalse();
    store.revoke(value);

    verifyNoInteractions(redis);
  }

  @Test
  void aRedisThatFailsMakesTheMarkerNotValidInsteadOfFailingTheVisit() {
    when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis is away"));

    assertThat(store.isValid(ID)).isFalse();
  }

  @Test
  void extendingAMarkerRedisHoldsMovesItsExpiryAndSaysItIsStillGood() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfPresent(KEY, "1", TTL)).thenReturn(true);

    assertThat(store.extend(ID, TTL)).isTrue();
  }

  @Test
  void extendingAMarkerRedisNoLongerHoldsSaysItIsGone() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfPresent(KEY, "1", TTL)).thenReturn(false);

    assertThat(store.extend(ID, TTL)).isFalse();
  }

  @Test
  void revokingAMarkerDeletesIt() {
    store.revoke(ID);

    verify(redis).delete(KEY);
  }
}
