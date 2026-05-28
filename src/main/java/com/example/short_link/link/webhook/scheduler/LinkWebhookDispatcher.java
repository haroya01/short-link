package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.webhook.application.helper.WebhookPayloadAdapter;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Listens for {@link ClickRecordedEvent} and POSTs a signed JSON payload to every enabled webhook
 * for that link. Filtering/quota, batching, and HTTP delivery live in focused collaborators so this
 * class stays limited to event orchestration and repository transaction boundaries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkWebhookDispatcher {

  private final LinkWebhookRepository repository;
  private final JsonMapper jsonMapper;
  private final WebhookDeliveryGate deliveryGate;
  private final WebhookBatchBuffer batchBuffer;
  private final WebhookHttpDeliveryClient deliveryClient;

  /**
   * Fires only after the click row is committed. A plain {@link
   * org.springframework.context.event.EventListener} would invoke us mid-transaction; if the click
   * insert later rolled back we'd have already POSTed a phantom event. Async runs on the dedicated
   * {@code webhookExecutor} so a slow receiver can't starve OG-fetch and vice versa.
   *
   * <p>{@code REQUIRES_NEW} is mandatory here — {@code TransactionalEventListener} fires after the
   * outer click-recording transaction has already committed, so we need our own tx for the
   * downstream {@code recordSuccess/recordFailure} dirty-check writes to land.
   */
  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        deliverSingle(hook, payload);
      }
    }
  }

  /**
   * Runs every 5 seconds to flush per-webhook batch queues. Drains up to 50 events per hook per
   * tick; anything beyond that waits for the next tick, and the buffer enforces a hard per-hook
   * queue cap.
   */
  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void flushBatches() {
    for (Long hookId : batchBuffer.hookIds()) {
      if (batchBuffer.isEmpty(hookId)) continue;
      LinkWebhookEntity hook = repository.findById(hookId).orElse(null);
      if (hook == null || !hook.isEnabled() || !hook.isBatchEnabled()) {
        batchBuffer.clear(hookId);
        continue;
      }
      List<Map<String, Object>> drained = batchBuffer.drain(hookId);
      if (drained.isEmpty()) continue;
      Map<String, Object> body =
          WebhookPayloadAdapter.buildBatch(hook.getFormat(), hook.getLinkId(), drained);
      deliver(hook, jsonMapper.writeValueAsString(body), "batch");
    }
  }

  void deliver(LinkWebhookEntity hook, String body, String eventType) {
    deliveryClient.deliver(hook, body, eventType);
  }

  private void deliverSingle(LinkWebhookEntity hook, Map<String, Object> payload) {
    Map<String, Object> adapted = WebhookPayloadAdapter.buildClick(hook.getFormat(), payload);
    deliver(hook, jsonMapper.writeValueAsString(adapted), "click");
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
        event.channel() == null ? "" : event.channel(),
        "utmSource",
        event.utmSource() == null ? "" : event.utmSource(),
        "bot",
        event.bot());
  }
}
