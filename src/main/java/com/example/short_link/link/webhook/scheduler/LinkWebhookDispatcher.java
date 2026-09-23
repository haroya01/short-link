package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkWebhookDispatcher {

  private final LinkWebhookRepository repository;
  private final WebhookDeliveryGate deliveryGate;
  private final WebhookBatchBuffer batchBuffer;
  private final WebhookHttpDeliveryClient deliveryClient;
  private final WebhookBatchDeliverer batchDeliverer;

  /**
   * AFTER_COMMIT prevents delivery of rolled-back clicks. No transaction is held across HTTP; the
   * delivery client records each outcome in its own short transaction. A dedicated executor
   * isolates slow receivers from OG fetches.
   */
  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClickRecorded(ClickRecordedEvent event) {
    List<LinkWebhookEntity> hooks =
        repository.findAllByLinkIdAndEnabledTrue(event.linkId().value());
    if (hooks.isEmpty()) {
      log.debug("webhook: no enabled hooks for linkId={}", event.linkId());
      return;
    }
    log.debug(
        "webhook: dispatching click for linkId={} to {} hook(s)", event.linkId(), hooks.size());
    Map<String, Object> payload = clickPayload(event);
    for (LinkWebhookEntity hook : hooks) {
      if (!deliveryGate.shouldDeliver(hook, event)) continue;
      if (hook.isBatchEnabled()) {
        batchBuffer.enqueue(hook.getId(), payload);
      } else {
        deliveryClient.deliver(hook, new WebhookNotification.Click(payload));
      }
    }
  }

  /**
   * Each hook flush commits independently in {@link WebhookBatchDeliverer}; a slow delivery does
   * not keep prior hooks' state changes uncommitted.
   */
  @Scheduled(fixedDelay = 5000)
  public void flushBatches() {
    for (Long hookId : batchBuffer.hookIds()) {
      batchDeliverer.deliverOne(hookId);
    }
  }

  private Map<String, Object> clickPayload(ClickRecordedEvent event) {
    return Map.of(
        "type",
        "click",
        "linkId",
        event.linkId(),
        "occurredAt",
        event.occurredAt().toString(),
        "countryCode",
        event.countryCode() == null ? "" : event.countryCode(),
        "deviceClass",
        event.deviceClass() == null ? "" : event.deviceClass(),
        "channel",
        event.referrerHost() == null ? "" : event.referrerHost(),
        "utmSource",
        event.utmSource() == null ? "" : event.utmSource(),
        "bot",
        event.bot());
  }
}
