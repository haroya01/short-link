package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebhookDeliveryGate {

  private static final RedisScript<Long> QUOTA_INCR =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return c",
          Long.class);

  private final MeterRegistry meterRegistry;
  private final StringRedisTemplate redis;

  boolean shouldDeliver(LinkWebhookEntity hook, ClickRecordedEvent event) {
    if (!hook.isIncludeBots() && event.bot()) {
      meterRegistry.counter("webhook.delivery", "result", "skipped_bot").increment();
      return false;
    }
    if (hook.getReferrerHostFilter() != null && !hook.getReferrerHostFilter().isBlank()) {
      String filter = hook.getReferrerHostFilter().toLowerCase();
      String channel = event.channel() == null ? "" : event.channel().toLowerCase();
      if (!channel.contains(filter)) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_filter").increment();
        return false;
      }
    }
    if (hook.getUtmSourceFilter() != null && !hook.getUtmSourceFilter().isBlank()) {
      String filter = hook.getUtmSourceFilter().toLowerCase();
      String src = event.utmSource() == null ? "" : event.utmSource().toLowerCase();
      if (!src.contains(filter)) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_filter").increment();
        return false;
      }
    }
    if (hook.getSampleRate() < 100
        && ThreadLocalRandom.current().nextInt(100) >= hook.getSampleRate()) {
      meterRegistry.counter("webhook.delivery", "result", "skipped_sample").increment();
      return false;
    }
    if (hook.getDailyQuota() != null && hook.getDailyQuota() > 0) {
      String key = "webhook:quota:" + hook.getId() + ":" + LocalDate.now(ZoneOffset.UTC);
      Long used = redis.execute(QUOTA_INCR, List.of(key), "86400");
      if (used != null && used > hook.getDailyQuota()) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_quota").increment();
        return false;
      }
    }
    return true;
  }
}
