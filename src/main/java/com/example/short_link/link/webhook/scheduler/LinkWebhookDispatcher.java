package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.webhook.application.helper.WebhookPayloadAdapter;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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
 * for that link. Each hook can opt into:
 *
 * <ul>
 *   <li>bot exclusion (default ON — most receivers don't want preview-crawler noise)
 *   <li>sample rate 1–100 (drop a percentage before send)
 *   <li>referrer host / utm source filters (only fire on matching clicks)
 *   <li>daily quota (hard cap via Redis counter, resets at UTC midnight)
 *   <li>batching (buffer up to 50 events, flush every 5s as one POST with type=click.batch)
 * </ul>
 *
 * <p>Failure handling: 5 consecutive non-2xx/exception results auto-disable the hook and stamp a
 * reason. Any 2xx resets the counter. Each request carries an HMAC-SHA256 signature in {@code
 * X-Kurl-Signature} computed over the raw body using the per-webhook secret.
 */
@Slf4j
@Component
public class LinkWebhookDispatcher {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final String USER_AGENT = "kurl-webhook/1.0 (+https://kurl.me)";
  private static final String SIGNATURE_HEADER = "X-Kurl-Signature";
  private static final String EVENT_HEADER = "X-Kurl-Event";
  private static final int BATCH_MAX_EVENTS_PER_FLUSH = 50;
  private static final int BATCH_QUEUE_HARD_CAP = 500;

  private static final RedisScript<Long> QUOTA_INCR =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return c",
          Long.class);

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private final LinkWebhookRepository repository;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;
  private final StringRedisTemplate redis;
  private final HttpFetcher httpFetcher;
  private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>> batchQueues =
      new ConcurrentHashMap<>();

  public LinkWebhookDispatcher(
      LinkWebhookRepository repository,
      JsonMapper jsonMapper,
      MeterRegistry meterRegistry,
      StringRedisTemplate redis,
      HttpFetcher httpFetcher) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
    this.meterRegistry = meterRegistry;
    this.redis = redis;
    this.httpFetcher = httpFetcher;
  }

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
    List<LinkWebhookEntity> hooks = repository.findAllByLinkIdAndEnabledTrue(event.linkId());
    if (hooks.isEmpty()) {
      log.debug("webhook: no enabled hooks for linkId={}", event.linkId());
      return;
    }
    log.debug(
        "webhook: dispatching click for linkId={} to {} hook(s)", event.linkId(), hooks.size());
    Map<String, Object> payload = clickPayload(event);
    for (LinkWebhookEntity hook : hooks) {
      if (!shouldDeliver(hook, event)) continue;
      if (hook.isBatchEnabled()) {
        enqueueBatch(hook.getId(), payload);
      } else {
        deliverSingle(hook, payload);
      }
    }
  }

  /**
   * Runs every 5 seconds to flush per-webhook batch queues. Drains up to {@code
   * BATCH_MAX_EVENTS_PER_FLUSH} events per hook per tick — anything beyond that waits for the next
   * tick, capped overall at {@code BATCH_QUEUE_HARD_CAP} so a runaway link with a slow receiver
   * can't OOM us.
   */
  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void flushBatches() {
    for (Map.Entry<Long, ConcurrentLinkedQueue<Map<String, Object>>> entry :
        batchQueues.entrySet()) {
      Long hookId = entry.getKey();
      ConcurrentLinkedQueue<Map<String, Object>> queue = entry.getValue();
      if (queue.isEmpty()) continue;
      LinkWebhookEntity hook = repository.findById(hookId).orElse(null);
      if (hook == null || !hook.isEnabled() || !hook.isBatchEnabled()) {
        queue.clear();
        continue;
      }
      List<Map<String, Object>> drained = new ArrayList<>();
      Map<String, Object> next;
      while (drained.size() < BATCH_MAX_EVENTS_PER_FLUSH && (next = queue.poll()) != null) {
        drained.add(next);
      }
      if (drained.isEmpty()) continue;
      Map<String, Object> body =
          WebhookPayloadAdapter.buildBatch(hook.getFormat(), hook.getLinkId(), drained);
      deliver(hook, jsonMapper.writeValueAsString(body), "batch");
    }
  }

  private boolean shouldDeliver(LinkWebhookEntity hook, ClickRecordedEvent event) {
    if (!hook.isIncludeBots() && event.bot()) {
      meterRegistry.counter("webhook.delivery", "result", "skipped_bot").increment();
      return false;
    }
    if (hook.getReferrerHostFilter() != null && !hook.getReferrerHostFilter().isBlank()) {
      String filter = hook.getReferrerHostFilter().toLowerCase();
      String channel = event.channel() == null ? "" : event.channel().toLowerCase();
      if (!channel.contains(filter)) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_filter").increment();
        return false;
      }
    }
    if (hook.getUtmSourceFilter() != null && !hook.getUtmSourceFilter().isBlank()) {
      String filter = hook.getUtmSourceFilter().toLowerCase();
      String src = event.utmSource() == null ? "" : event.utmSource().toLowerCase();
      if (!src.contains(filter)) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_filter").increment();
        return false;
      }
    }
    if (hook.getSampleRate() < 100) {
      if (ThreadLocalRandom.current().nextInt(100) >= hook.getSampleRate()) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_sample").increment();
        return false;
      }
    }
    if (hook.getDailyQuota() != null && hook.getDailyQuota() > 0) {
      String key = "webhook:quota:" + hook.getId() + ":" + LocalDate.now(ZoneOffset.UTC);
      Long used = redis.execute(QUOTA_INCR, List.of(key), "86400");
      if (used != null && used > hook.getDailyQuota()) {
        meterRegistry.counter("webhook.delivery", "result", "skipped_quota").increment();
        return false;
      }
    }
    return true;
  }

  private void enqueueBatch(Long hookId, Map<String, Object> payload) {
    ConcurrentLinkedQueue<Map<String, Object>> queue =
        batchQueues.computeIfAbsent(hookId, k -> new ConcurrentLinkedQueue<>());
    if (queue.size() >= BATCH_QUEUE_HARD_CAP) {
      meterRegistry.counter("webhook.delivery", "result", "dropped_overflow").increment();
      return;
    }
    queue.add(payload);
  }

  private void deliverSingle(LinkWebhookEntity hook, Map<String, Object> payload) {
    Map<String, Object> adapted = WebhookPayloadAdapter.buildClick(hook.getFormat(), payload);
    deliver(hook, jsonMapper.writeValueAsString(adapted), "click");
  }

  void deliver(LinkWebhookEntity hook, String body, String eventType) {
    Resolved resolved = PublicHttpUrlGuard.resolve(hook.getUrl()).orElse(null);
    if (resolved == null) {
      // Most common cause here is DNS lookup failure on the receiver host (intermittent network /
      // misconfigured CNAME), not the user changing the URL. Surface that so 5 of these don't
      // silently auto-disable a hook the user hasn't touched.
      hook.recordFailure(
          null, "url is not reachable as a public host (DNS or scheme check failed)");
      meterRegistry.counter("webhook.delivery", "result", "blocked").increment();
      log.warn(
          "webhook delivery blocked by public-url guard: hookId={} url={}",
          hook.getId(),
          hook.getUrl());
      return;
    }
    // Discord/Slack don't accept a kurl HMAC header — they own the message contract and will not
    // call back to verify anything. Skip the signature for those receivers and only stamp it on
    // generic endpoints that opted into the kurl signing scheme on their side.
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
    // Timer captures wall-clock latency of the actual HTTP call (including DNS, TLS, server
    // processing, body discard). Tagged with result so the dashboard can split p95 of healthy
    // deliveries from p95 of degraded ones — receivers that 5xx slowly are otherwise invisible
    // in the existing counter view.
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

  private static String sign(String body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(sig.length * 2);
    for (byte b : sig) hex.append(String.format("%02x", b));
    return hex.toString();
  }
}
