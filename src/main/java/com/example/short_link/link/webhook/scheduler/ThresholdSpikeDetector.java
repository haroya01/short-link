package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.webhook.application.helper.ThresholdSpikePayload;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Click-driven counterpart to {@link DailyWebhookSummaryJob}. On every committed click, finds hooks
 * subscribed to {@link WebhookDeliveryMode#THRESHOLD_SPIKE} (or {@link WebhookDeliveryMode#BOTH})
 * for this link, counts human clicks in the rolling {@code spikeWindowMinutes} window, and fires
 * one alert when the count crosses {@code spikeThreshold}.
 *
 * <p>Cooldown: once a hook fires, it can't fire again until {@code spikeWindowMinutes} elapses — a
 * sustained spike (e.g. 500 clicks over an hour with a 10-min/50-click rule) becomes one alert per
 * window, not one per click.
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW} mirrors {@link LinkWebhookDispatcher} so the count
 * query never sees the still-uncommitted click and the {@code markSpikeFired} write lands even
 * though the outer click-recording transaction has already closed.
 */
@Slf4j
@Component
public class ThresholdSpikeDetector {

  private final LinkWebhookRepository hooks;
  private final LinkRepository links;
  private final ClickTotalsReadRepository clickTotals;
  private final ClickAlertReadRepository clickAlerts;
  private final LinkWebhookDispatcher dispatcher;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  @Autowired
  public ThresholdSpikeDetector(
      LinkWebhookRepository hooks,
      LinkRepository links,
      ClickTotalsReadRepository clickTotals,
      ClickAlertReadRepository clickAlerts,
      LinkWebhookDispatcher dispatcher,
      JsonMapper jsonMapper) {
    this(hooks, links, clickTotals, clickAlerts, dispatcher, jsonMapper, Clock.systemUTC());
  }

  ThresholdSpikeDetector(
      LinkWebhookRepository hooks,
      LinkRepository links,
      ClickTotalsReadRepository clickTotals,
      ClickAlertReadRepository clickAlerts,
      LinkWebhookDispatcher dispatcher,
      JsonMapper jsonMapper,
      Clock clock) {
    this.hooks = hooks;
    this.links = links;
    this.clickTotals = clickTotals;
    this.clickAlerts = clickAlerts;
    this.dispatcher = dispatcher;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onClickRecorded(ClickRecordedEvent event) {
    if (event.bot()) return;
    List<LinkWebhookEntity> candidates =
        hooks.findAllEnabledByDeliveryMode(
            WebhookDeliveryMode.THRESHOLD_SPIKE, WebhookDeliveryMode.BOTH);
    if (candidates.isEmpty()) return;
    for (LinkWebhookEntity hook : candidates) {
      if (!hook.getLinkId().equals(event.linkId())) continue;
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
            link.getShortCode().value(), windowMinutes + "m", count, threshold, topReferrer);
    String body = jsonMapper.writeValueAsString(payload.toJsonMap());
    dispatcher.deliver(hook, body, "spike_alert");
    hook.markSpikeFired(now);
  }

  private static boolean inCooldown(LinkWebhookEntity hook, Instant now, int windowMinutes) {
    Instant last = hook.getSpikeLastFiredAt();
    if (last == null) return false;
    return Duration.between(last, now).toMinutes() < windowMinutes;
  }
}
