package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.webhook.application.dto.WebhookReDetectResult;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin bulk operation — re-runs {@link WebhookFormat#detect} on every persisted hook and
 * reactivates rows that (a) were auto-disabled and (b) flipped to a non-GENERIC format. Mirrors
 * what {@code V54} did at migration time so ops can recover without another migration when a new
 * receiver format is added in code.
 *
 * <p>Rows that stay on {@code GENERIC} are not reactivated — their failures weren't a payload-shape
 * mismatch we just fixed, so flipping them on would re-trigger the auto-disable.
 */
@Service
@RequiredArgsConstructor
public class ReDetectWebhookFormatsUseCase {

  private final LinkWebhookRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public WebhookReDetectResult execute() {
    int scanned = 0;
    int formatChanged = 0;
    int reactivated = 0;
    for (LinkWebhookEntity hook : repository.findAll()) {
      scanned++;
      WebhookFormat detected = WebhookFormat.detect(hook.getUrl());
      boolean autoDisabled = !hook.isEnabled() && hook.getAutoDisabledReason() != null;
      if (detected != hook.getFormat()) {
        hook.changeFormat(detected);
        formatChanged++;
        if (autoDisabled && detected != WebhookFormat.GENERIC) {
          hook.resetFailureState();
          reactivated++;
        }
      }
    }
    meterRegistry.counter("link.webhook.redetect", "result", "scanned").increment(scanned);
    if (formatChanged > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "format_changed")
          .increment(formatChanged);
    }
    if (reactivated > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "reactivated")
          .increment(reactivated);
    }
    return new WebhookReDetectResult(scanned, formatChanged, reactivated);
  }
}
