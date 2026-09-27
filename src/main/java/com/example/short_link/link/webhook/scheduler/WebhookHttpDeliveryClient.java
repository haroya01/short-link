package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.application.helper.WebhookPayloadAdapter;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class WebhookHttpDeliveryClient {

  private final MeterRegistry meterRegistry;
  private final HttpFetcher httpFetcher;
  private final SecretCipher cipher;
  private final JsonMapper jsonMapper;
  private final LinkWebhookRepository repository;
  private final TransactionTemplate transaction;

  void deliver(LinkWebhookEntity hook, WebhookNotification notification) {
    String body =
        jsonMapper.writeValueAsString(WebhookPayloadAdapter.build(hook.getFormat(), notification));
    String eventType = notification.eventType();
    boolean signed = hook.getFormat() == WebhookFormat.GENERIC;
    WebhookSender.Result result =
        WebhookSender.send(
            httpFetcher,
            meterRegistry,
            hook.getUrl(),
            cipher.decrypt(hook.getSecret()),
            signed,
            body,
            eventType);
    switch (result.outcome()) {
      case OK -> {
        log.debug(
            "webhook delivered: hookId={} status={} event={}",
            hook.getId(),
            result.statusCode(),
            eventType);
      }
      case NON_2XX -> {
        log.warn(
            "webhook delivery returned non-2xx: hookId={} url={} status={}",
            hook.getId(),
            hook.getUrl(),
            result.statusCode());
      }
      case BLOCKED -> {
        log.warn(
            "webhook delivery blocked by public-url guard: hookId={} url={}",
            hook.getId(),
            hook.getUrl());
      }
      default -> {
        log.warn(
            "webhook delivery failed: hookId={} url={} reason={}",
            hook.getId(),
            hook.getUrl(),
            result.error());
      }
    }
    transaction.executeWithoutResult(
        status -> repository.findById(hook.getId()).ifPresent(current -> record(current, result)));
  }

  private static void record(LinkWebhookEntity hook, WebhookSender.Result result) {
    switch (result.outcome()) {
      case OK -> hook.recordSuccess(result.statusCode());
      case NON_2XX -> hook.recordFailure(result.statusCode(), result.error());
      default -> hook.recordFailure(null, result.error());
    }
  }
}
