package com.example.short_link.link.application;

import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Listens for {@link ClickRecordedEvent} and POSTs a signed JSON payload to every enabled webhook
 * for that link. Best-effort — single attempt, 5s timeout, no retry queue. The result (status code
 * or error) is written back to the row so owners can see in the UI which hooks are healthy.
 *
 * <p>Each request carries an HMAC-SHA256 signature in {@code X-Kurl-Signature} computed over the
 * raw body using the per-webhook secret, matching the GitHub/Stripe pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkWebhookDispatcher {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final String USER_AGENT = "kurl-webhook/1.0 (+https://kurl.me)";
  private static final String SIGNATURE_HEADER = "X-Kurl-Signature";
  private static final String EVENT_HEADER = "X-Kurl-Event";

  private final LinkWebhookRepository repository;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  @Async
  @EventListener
  @Transactional
  public void onClickRecorded(ClickRecordedEvent event) {
    List<LinkWebhookEntity> hooks = repository.findAllByLinkIdAndEnabledTrue(event.linkId());
    if (hooks.isEmpty()) return;
    String body = serialize(event);
    for (LinkWebhookEntity hook : hooks) {
      deliver(hook, body);
    }
  }

  void deliver(LinkWebhookEntity hook, String body) {
    if (!PublicHttpUrlGuard.isPublic(hook.getUrl())) {
      hook.recordFailure(null, "url no longer points to a public host");
      meterRegistry.counter("webhook.delivery", "result", "blocked").increment();
      return;
    }
    String signature;
    try {
      signature = sign(body, hook.getSecret());
    } catch (Exception e) {
      hook.recordFailure(null, "signature failed: " + e.getMessage());
      meterRegistry.counter("webhook.delivery", "result", "sign_error").increment();
      return;
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(hook.getUrl()))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header(EVENT_HEADER, "click")
            .header(SIGNATURE_HEADER, "sha256=" + signature)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      int status = response.statusCode();
      if (status / 100 == 2) {
        hook.recordSuccess(status);
        meterRegistry.counter("webhook.delivery", "result", "ok").increment();
      } else {
        hook.recordFailure(status, "non-2xx response");
        meterRegistry.counter("webhook.delivery", "result", "non_2xx").increment();
      }
    } catch (Exception e) {
      hook.recordFailure(null, e.getClass().getSimpleName() + ": " + e.getMessage());
      meterRegistry.counter("webhook.delivery", "result", "exception").increment();
      log.warn("webhook delivery failed for hook {} url {}", hook.getId(), hook.getUrl(), e);
    }
  }

  private String serialize(ClickRecordedEvent event) {
    return jsonMapper.writeValueAsString(
        java.util.Map.of(
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
            "bot",
            event.bot()));
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
