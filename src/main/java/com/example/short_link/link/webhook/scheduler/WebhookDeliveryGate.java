package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebhookDeliveryGate {

  private final MeterRegistry meterRegistry;
  private final RedisWindowCounter counter;

  boolean shouldDeliver(LinkWebhookEntity hook, ClickRecordedEvent event) {
    if (!hook.isIncludeBots() && event.bot()) {
      meterRegistry.counter("webhook.delivery", "result", "skipped_bot").increment();
      return false;
    }
    if (hook.getReferrerHostFilter() != null && !hook.getReferrerHostFilter().isBlank()) {
      String filter = hook.getReferrerHostFilter().toLowerCase(Locale.ROOT);
      String referrerHost =
          event.referrerHost() == null ? "" : event.referrerHost().toLowerCase(Locale.ROOT);
      if (!referrerHost.contains(filter)) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_filter").increment();
        return false;
      }
    }
    if (hook.getUtmSourceFilter() != null && !hook.getUtmSourceFilter().isBlank()) {
      String filter = hook.getUtmSourceFilter().toLowerCase(Locale.ROOT);
      String src = event.utmSource() == null ? "" : event.utmSource().toLowerCase(Locale.ROOT);
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
      long used = counter.increment(key, Duration.ofDays(1));
      if (used > hook.getDailyQuota()) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_quota").increment();
        return false;
      }
    }
    return true;
  }
}
