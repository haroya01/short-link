package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.webhook.application.helper.ThresholdSpikePayload;
import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts committed human clicks in the rolling window. A fired hook stays in cooldown for {@code
 * spikeWindowMinutes}. AFTER_COMMIT avoids uncommitted counts; REQUIRES_NEW persists {@code
 * markSpikeFired} after the click transaction closes.
 */
@Slf4j
@Component
public class ThresholdSpikeDetector {

  private final LinkWebhookRepository hooks;
  private final LinkRepository links;
  private final ClickTotalsReadRepository clickTotals;
  private final ClickAlertReadRepository clickAlerts;
  private final WebhookHttpDeliveryClient deliveryClient;
  private final Clock clock;

  public ThresholdSpikeDetector(
      LinkWebhookRepository hooks,
      LinkRepository links,
      ClickTotalsReadRepository clickTotals,
      ClickAlertReadRepository clickAlerts,
      WebhookHttpDeliveryClient deliveryClient,
      Clock clock) {
    this.hooks = hooks;
    this.links = links;
    this.clickTotals = clickTotals;
    this.clickAlerts = clickAlerts;
    this.deliveryClient = deliveryClient;
    this.clock = clock;
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onClickRecorded(ClickRecordedEvent event) {
    if (event.bot()) return;
    List<LinkWebhookEntity> candidates =
        hooks.findAllByLinkIdAndEnabledTrue(event.linkId().value());
    for (LinkWebhookEntity hook : candidates) {
      if (!hook.getDeliveryMode().sendsSpikeAlert()) {
        continue;
      }
      tryFire(hook);
    }
  }

  void tryFire(LinkWebhookEntity hook) {
    Integer threshold = hook.getSpikeThreshold();
    Integer windowMinutes = hook.getSpikeWindowMinutes();
    if (threshold == null || windowMinutes == null) return;
    Instant now = clock.instant();
    if (inCooldown(hook, now, windowMinutes)) return;
    Instant since = now.minus(Duration.ofMinutes(windowMinutes));
    long count = clickTotals.countSinceByLinkId(hook.getLinkId(), since);
    if (count < threshold) return;
    LinkEntity link = links.findById(hook.getLinkId()).orElse(null);
    if (link == null) return;
    String topReferrer =
        clickAlerts
            .findTopReferrerHostByLinkIdSince(hook.getLinkId(), since)
            .map(r -> r.getHost())
            .orElse(null);
    ThresholdSpikePayload payload =
        new ThresholdSpikePayload(
            link.getShortCode(), windowMinutes + "m", count, threshold, topReferrer);
    deliveryClient.deliver(hook, new WebhookNotification.SpikeAlert(payload));
    hook.markSpikeFired(now);
  }

  private static boolean inCooldown(LinkWebhookEntity hook, Instant now, int windowMinutes) {
    Instant last = hook.getSpikeLastFiredAt();
    if (last == null) return false;
    return Duration.between(last, now).toMinutes() < windowMinutes;
  }
}
