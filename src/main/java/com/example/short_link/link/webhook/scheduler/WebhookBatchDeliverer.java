package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-hook batch delivery in its own transaction. Split out from {@link LinkWebhookDispatcher} so
 * that a slow webhook receiver only holds its own DB connection — the surrounding scheduler loop
 * runs without a transaction, so other hooks' record-state writes are not serialized behind it.
 *
 * <p>{@code REQUIRES_NEW} is mandatory: the caller's loop must not share a transaction with us, or
 * a 5s HTTP read timeout on hook N stalls every subsequent hook's tx commit.
 */
@Component
@RequiredArgsConstructor
class WebhookBatchDeliverer {

  private final LinkWebhookRepository repository;
  private final WebhookBatchBuffer batchBuffer;
  private final WebhookHttpDeliveryClient deliveryClient;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deliverOne(Long hookId) {
    if (batchBuffer.isEmpty(hookId)) return;
    LinkWebhookEntity hook = repository.findById(hookId).orElse(null);
    if (hook == null || !hook.isEnabled() || !hook.isBatchEnabled()) {
      batchBuffer.clear(hookId);
      return;
    }
    List<Map<String, Object>> drained = batchBuffer.drain(hookId);
    if (drained.isEmpty()) return;
    deliveryClient.deliver(hook, new WebhookNotification.Batch(hook.getLinkId(), drained));
  }
}
