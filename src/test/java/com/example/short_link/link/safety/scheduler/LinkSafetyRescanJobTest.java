package com.example.short_link.link.safety.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.safety.application.LinkSafetyRescanProperties;
import com.example.short_link.link.safety.application.LinkSafetyRescanner;
import com.example.short_link.link.safety.application.UrlThreatLookupException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LinkSafetyRescanJobTest {

  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

  private final LinkSafetyRescanner rescanner = mock(LinkSafetyRescanner.class);
  private final RedisDistributedLock lock = mock(RedisDistributedLock.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  @BeforeEach
  void wireRedis() {
    when(redis.opsForValue()).thenReturn(values);
  }

  private LinkSafetyRescanJob job(String apiKey) {
    return new LinkSafetyRescanJob(
        rescanner,
        new LinkSafetyRescanProperties(true, 100),
        new SafeBrowsingProperties(true, apiKey, Duration.ofHours(1), Duration.ofSeconds(2)),
        lock,
        redis,
        new SimpleMeterRegistry(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void withoutAnApiKeyThereIsNothingToAsk() {
    job(" ").run();

    verifyNoInteractions(lock, rescanner);
  }

  @Test
  void picksUpWhereTheLastRunStoppedAndMovesTheCursor() {
    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    when(values.get(LinkSafetyRescanJob.CURSOR_KEY)).thenReturn("40");
    when(rescanner.rescan(40L, 100, NOW))
        .thenReturn(new LinkSafetyRescanner.Result(100, 1, 140L, false));

    job("key").run();

    verify(values).set(LinkSafetyRescanJob.CURSOR_KEY, "140");
    verify(lock).release(LinkSafetyRescanJob.LOCK_KEY);
  }

  @Test
  void startsOverAfterTheLastLink() {
    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    when(values.get(LinkSafetyRescanJob.CURSOR_KEY)).thenReturn("not-a-number");
    when(rescanner.rescan(0L, 100, NOW)).thenReturn(new LinkSafetyRescanner.Result(3, 0, 9L, true));

    job("key").run();

    verify(values).set(LinkSafetyRescanJob.CURSOR_KEY, "0");
  }

  @Test
  void aFailedLookupLeavesTheCursorWhereItWas() {
    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    when(rescanner.rescan(anyLong(), anyInt(), any()))
        .thenThrow(UrlThreatLookupException.unavailable(new RuntimeException("down")));

    job("key").run();

    verify(values, never()).set(anyString(), anyString());
    verify(lock).release(LinkSafetyRescanJob.LOCK_KEY);
  }

  @Test
  void anotherRunHoldingTheLockMeansThisOneWaits() {
    when(lock.tryAcquire(anyString(), any())).thenReturn(false);

    job("key").run();

    verifyNoInteractions(rescanner);
  }
}
