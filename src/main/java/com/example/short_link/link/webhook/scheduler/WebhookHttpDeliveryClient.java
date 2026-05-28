package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class WebhookHttpDeliveryClient {

  private static final String USER_AGENT = "kurl-webhook/1.0 (+https://kurl.me)";
  private static final String SIGNATURE_HEADER = "X-Kurl-Signature";
  private static final String EVENT_HEADER = "X-Kurl-Event";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private final MeterRegistry meterRegistry;
  private final HttpFetcher httpFetcher;

  void deliver(LinkWebhookEntity hook, String body, String eventType) {
    Resolved resolved = PublicHttpUrlGuard.resolve(hook.getUrl()).orElse(null);
    if (resolved == null) {
      hook.recordFailure(
          null, "url is not reachable as a public host (DNS or scheme check failed)");
      meterRegistry.counter("webhook.delivery", "result", "blocked").increment();
      log.warn(
          "webhook delivery blocked by public-url guard: hookId={} url={}",
          hook.getId(),
          hook.getUrl());
      return;
    }

    boolean signed = hook.getFormat() == WebhookFormat.GENERIC;
    String signature = null;
    if (signed) {
      try {
        signature = sign(body, hook.getSecret());
      } catch (Exception e) {
        hook.recordFailure(null, "signature failed: " + e.getMessage());
        meterRegistry.counter("webhook.delivery", "result", "sign_error").increment();
        log.warn("webhook signing failed: hookId={}", hook.getId(), e);
        return;
      }
    }

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", USER_AGENT);
    headers.put(EVENT_HEADER, eventType);
    if (signed) {
      headers.put(SIGNATURE_HEADER, "sha256=" + signature);
    }

    Timer.Sample sample = Timer.start(meterRegistry);
    String resultTag = "exception";
    try {
      HttpFetcher.Response response =
          httpFetcher.fetch(
              HttpFetcher.Request.post(
                  resolved,
                  headers,
                  body.getBytes(StandardCharsets.UTF_8),
                  "application/json",
                  CONNECT_TIMEOUT,
                  READ_TIMEOUT,
                  0));
      int status = response.status();
      if (status / 100 == 2) {
        hook.recordSuccess(status);
        meterRegistry.counter("webhook.delivery", "result", "ok").increment();
        resultTag = "ok";
        log.debug(
            "webhook delivered: hookId={} status={} event={}", hook.getId(), status, eventType);
      } else {
        hook.recordFailure(status, "non-2xx response (" + status + ")");
        meterRegistry.counter("webhook.delivery", "result", "non_2xx").increment();
        resultTag = "non_2xx";
        log.warn(
            "webhook delivery returned non-2xx: hookId={} url={} status={}",
            hook.getId(),
            hook.getUrl(),
            status);
      }
    } catch (Exception e) {
      hook.recordFailure(null, e.getClass().getSimpleName() + ": " + e.getMessage());
      meterRegistry.counter("webhook.delivery", "result", "exception").increment();
      log.warn(
          "webhook delivery failed: hookId={} url={} reason={}",
          hook.getId(),
          hook.getUrl(),
          e.toString());
    } finally {
      sample.stop(meterRegistry.timer("outbound.http", "client", "webhook", "result", resultTag));
    }
  }

  private static String sign(String body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(sig.length * 2);
    for (byte b : sig) hex.append(String.format("%02x", b));
    return hex.toString();
  }
}
