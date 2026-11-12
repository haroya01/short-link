package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.link.webhook.scheduler.WebhookDeliveryLedger.ClaimedDelivery;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkWebhookDeliveryWorker {

  static final int EVENTS_PER_BATCH = 50;
  private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

  private final WebhookDeliveryLedger ledger;
  private final LinkWebhookRepository hooks;
  private final WebhookHttpDeliveryClient deliveryClient;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${short-link.webhook.delivery.interval:PT2S}")
  public void deliverDue() {
    Map<Long, List<ClaimedDelivery>> byHook =
        ledger.claim(clock.instant()).stream()
            .collect(
                Collectors.groupingBy(
                    ClaimedDelivery::hookId, LinkedHashMap::new, Collectors.toList()));
    byHook.forEach(this::deliverForHook);
  }

  @Scheduled(cron = "${short-link.webhook.delivery.purge-cron:0 40 4 * * *}", zone = "Asia/Seoul")
  public void purgeFinished() {
    int purged = ledger.purge(clock.instant());
    if (purged > 0) log.info("webhook deliveries purged: {}", purged);
  }

  private void deliverForHook(Long hookId, List<ClaimedDelivery> claimed) {
    try {
      LinkWebhookEntity hook = hooks.findById(hookId).orElse(null);
      if (hook == null || !hook.isEnabled()) {
        ledger.giveUp(claimed, "webhook disabled");
        meterRegistry.counter("webhook.delivery.queue", "outcome", "dropped_disabled").increment();
        return;
      }
      if (hook.isBatchEnabled()) {
        for (int from = 0; from < claimed.size(); from += EVENTS_PER_BATCH) {
          List<ClaimedDelivery> chunk =
              claimed.subList(from, Math.min(from + EVENTS_PER_BATCH, claimed.size()));
          send(
              hook,
              chunk,
              new WebhookNotification.Batch(
                  hook.getLinkId(), chunk.stream().map(this::payload).toList()));
        }
      } else {
        for (ClaimedDelivery delivery : claimed) {
          send(hook, List.of(delivery), new WebhookNotification.Click(payload(delivery)));
        }
      }
    } catch (RuntimeException e) {
      log.warn(
          "webhook deliveries for hookId={} left for the next lease: {}", hookId, e.toString());
    }
  }

  private void send(
      LinkWebhookEntity hook, List<ClaimedDelivery> chunk, WebhookNotification notification) {
    WebhookSender.Result result = deliveryClient.deliver(hook, notification);
    ledger.record(chunk, result, clock.instant());
    meterRegistry
        .counter("webhook.delivery.queue", "outcome", result.ok() ? "delivered" : "failed")
        .increment(chunk.size());
  }

  private Map<String, Object> payload(ClaimedDelivery delivery) {
    return jsonMapper.readValue(delivery.payload(), PAYLOAD);
  }
}
