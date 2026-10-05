package com.example.short_link.link.safety.scheduler;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.safety.application.LinkSafetyRescanProperties;
import com.example.short_link.link.safety.application.LinkSafetyRescanner;
import com.example.short_link.link.safety.application.UrlThreatLookupException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Walks every live link a batch at a time, so a destination that turns malicious after creation is
// still caught. The cursor survives restarts in Redis; losing it only restarts the walk.
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkSafetyRescanJob {

  static final String LOCK_KEY = "kurl:safety-rescan";
  static final String CURSOR_KEY = "kurl:safety-rescan:cursor";

  private final LinkSafetyRescanner rescanner;
  private final LinkSafetyRescanProperties props;
  private final SafeBrowsingProperties safeBrowsing;
  private final RedisDistributedLock lock;
  private final StringRedisTemplate redis;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  @Scheduled(cron = "${short-link.safety-rescan.cron:0 41 */6 * * *}", zone = "Asia/Seoul")
  public void run() {
    if (!props.enabled() || !safeBrowsing.enabled() || isBlank(safeBrowsing.apiKey())) {
      return;
    }
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(30))) {
      log.debug("safety rescan skipped — lock held");
      return;
    }
    try {
      LinkSafetyRescanner.Result result =
          rescanner.rescan(cursor(), props.batchSize(), clock.instant());
      redis
          .opsForValue()
          .set(CURSOR_KEY, Long.toString(result.reachedEnd() ? 0L : result.lastLinkId()));
      meterRegistry
          .counter("short_link.safety_rescan", "outcome", "scanned")
          .increment(result.scanned());
      meterRegistry
          .counter("short_link.safety_rescan", "outcome", "disabled")
          .increment(result.disabled());
      log.info(
          "safety rescan: scanned {} links, disabled {}, next cursor {}",
          result.scanned(),
          result.disabled(),
          result.reachedEnd() ? 0L : result.lastLinkId());
    } catch (UrlThreatLookupException failure) {
      meterRegistry.counter("short_link.safety_rescan", "outcome", "lookup_failed").increment();
      log.warn("safety rescan stopped without moving the cursor: {}", failure.kind());
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  private long cursor() {
    String value = redis.opsForValue().get(CURSOR_KEY);
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException corrupt) {
      return 0L;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
