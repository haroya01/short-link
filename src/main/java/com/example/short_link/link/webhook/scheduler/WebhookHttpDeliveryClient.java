package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records a link webhook's delivery state. The actual sign-and-POST is the shared {@link
 * WebhookSender}; this class only maps the outcome onto the hook's success/failure surface and
 * logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class WebhookHttpDeliveryClient {

  private final MeterRegistry meterRegistry;
  private final HttpFetcher httpFetcher;

  void deliver(LinkWebhookEntity hook, String body, String eventType) {
    boolean signed = hook.getFormat() == WebhookFormat.GENERIC;
    WebhookSender.Result result =
        WebhookSender.send(
            httpFetcher, meterRegistry, hook.getUrl(), hook.getSecret(), signed, body, eventType);
    switch (result.outcome()) {
      case OK -> {
        hook.recordSuccess(result.statusCode());
        log.debug(
            "webhook delivered: hookId={} status={} event={}",
            hook.getId(),
            result.statusCode(),
            eventType);
      }
      case NON_2XX -> {
        hook.recordFailure(result.statusCode(), result.error());
        log.warn(
            "webhook delivery returned non-2xx: hookId={} url={} status={}",
            hook.getId(),
            hook.getUrl(),
            result.statusCode());
      }
      case BLOCKED -> {
        hook.recordFailure(null, result.error());
        log.warn(
            "webhook delivery blocked by public-url guard: hookId={} url={}",
            hook.getId(),
            hook.getUrl());
      }
      default -> {
        hook.recordFailure(null, result.error());
        log.warn(
            "webhook delivery failed: hookId={} url={} reason={}",
            hook.getId(),
            hook.getUrl(),
            result.error());
      }
    }
  }
}
