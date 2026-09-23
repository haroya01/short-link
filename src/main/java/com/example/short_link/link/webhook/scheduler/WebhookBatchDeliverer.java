package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Holds no transaction across HTTP; each hook's outcome commits on its own after delivery, so the
 * scheduler loop never keeps a connection open for a slow receiver.
 */
@Component
@RequiredArgsConstructor
class WebhookBatchDeliverer {

  private final LinkWebhookRepository repository;
  private final WebhookBatchBuffer batchBuffer;
  private final WebhookHttpDeliveryClient deliveryClient;

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
