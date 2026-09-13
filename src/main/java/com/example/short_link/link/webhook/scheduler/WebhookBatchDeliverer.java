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
 * REQUIRES_NEW gives each hook an independent commit boundary; the scheduler loop must not hold one
 * transaction across slow HTTP deliveries.
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
