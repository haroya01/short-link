package com.example.short_link.link.application;

import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-link webhook registration. Each link can hold up to {@link #MAX_PER_LINK} active hooks; when
 * the click pipeline records a hit, {@link LinkWebhookDispatcher} fires a signed POST to every
 * enabled URL. Owner-scoped: every operation verifies ownership of the link before touching the
 * row.
 */
@Service
@RequiredArgsConstructor
public class LinkWebhookService {

  public static final int MAX_PER_LINK = 5;
  private static final String SECRET_ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int SECRET_LENGTH = 48;

  private final LinkRepository linkRepository;
  private final LinkWebhookRepository repository;
  private final MeterRegistry meterRegistry;
  private final SecureRandom random = new SecureRandom();

  @Transactional(readOnly = true)
  public List<WebhookSummary> list(Long userId, String shortCode) {
    LinkEntity link = ownedLink(userId, shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional
  public IssuedWebhook register(Long userId, String shortCode, String url, String name) {
    LinkEntity link = ownedLink(userId, shortCode);
    if (!PublicHttpUrlGuard.isPublic(url)) {
      throw new InvalidWebhookUrlException();
    }
    if (repository.countByLinkId(link.getId()) >= MAX_PER_LINK) {
      throw new TooManyWebhooksException(MAX_PER_LINK);
    }
    String secret = generateSecret();
    WebhookFormat format = WebhookFormat.detect(url);
    LinkWebhookEntity saved =
        repository.save(
            new LinkWebhookEntity(link.getId(), url, secret, sanitizeName(name), format));
    meterRegistry
        .counter("link.webhook.registered", "format", format.name().toLowerCase())
        .increment();
    return new IssuedWebhook(
        saved.getId(), url, secret, sanitizeName(name), saved.getCreatedAt(), format);
  }

  @Transactional
  public WebhookSummary toggle(Long userId, String shortCode, Long webhookId, boolean enabled) {
    LinkWebhookEntity hook = ownedHook(userId, shortCode, webhookId);
    if (enabled) hook.enable();
    else hook.disable();
    return toSummary(hook);
  }

  @Transactional
  public WebhookSummary updateConfig(
      Long userId, String shortCode, Long webhookId, ConfigPatch patch) {
    LinkWebhookEntity hook = ownedHook(userId, shortCode, webhookId);
    Integer sampleRate = patch.sampleRate();
    if (sampleRate != null && (sampleRate < 1 || sampleRate > 100)) {
      throw new IllegalArgumentException("sampleRate must be between 1 and 100");
    }
    hook.updateConfig(
        patch.includeBots(),
        patch.sampleRate(),
        patch.batchEnabled(),
        patch.dailyQuota(),
        patch.referrerHostFilter(),
        patch.utmSourceFilter());
    return toSummary(hook);
  }

  @Transactional
  public void delete(Long userId, String shortCode, Long webhookId) {
    LinkWebhookEntity hook = ownedHook(userId, shortCode, webhookId);
    repository.delete(hook);
  }

  /**
   * Admin operation — re-runs {@link WebhookFormat#detect} on every persisted hook and reactivates
   * the ones that (a) were auto-disabled and (b) flipped to a non-GENERIC format. Mirrors what
   * {@code V54} did at migration time and gives ops a way to recover from the same situation
   * without another migration (e.g. a new receiver format added in code).
   *
   * <p>Rows that stay on {@code GENERIC} are not reactivated — their failures were not a
   * payload-shape mismatch we just fixed, so flipping them on would just re-trigger the
   * auto-disable.
   */
  @Transactional
  public ReDetectResult reDetectFormats() {
    int scanned = 0;
    int formatChanged = 0;
    int reactivated = 0;
    for (LinkWebhookEntity hook : repository.findAll()) {
      scanned++;
      WebhookFormat detected = WebhookFormat.detect(hook.getUrl());
      boolean autoDisabled = !hook.isEnabled() && hook.getAutoDisabledReason() != null;
      if (detected != hook.getFormat()) {
        hook.changeFormat(detected);
        formatChanged++;
        if (autoDisabled && detected != WebhookFormat.GENERIC) {
          hook.resetFailureState();
          reactivated++;
        }
      }
    }
    meterRegistry.counter("link.webhook.redetect", "result", "scanned").increment(scanned);
    if (formatChanged > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "format_changed")
          .increment(formatChanged);
    }
    if (reactivated > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "reactivated")
          .increment(reactivated);
    }
    return new ReDetectResult(scanned, formatChanged, reactivated);
  }

  private LinkEntity ownedLink(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return link;
  }

  private LinkWebhookEntity ownedHook(Long userId, String shortCode, Long webhookId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkWebhookEntity hook =
        repository.findById(webhookId).orElseThrow(WebhookNotFoundException::new);
    if (!hook.getLinkId().equals(link.getId())) throw new WebhookNotFoundException();
    return hook;
  }

  private WebhookSummary toSummary(LinkWebhookEntity h) {
    return new WebhookSummary(
        h.getId(),
        h.getUrl(),
        h.getName(),
        h.isEnabled(),
        h.getCreatedAt(),
        h.getLastCalledAt(),
        h.getLastStatusCode(),
        h.getLastError(),
        h.isIncludeBots(),
        h.getSampleRate(),
        h.isBatchEnabled(),
        h.getDailyQuota(),
        h.getConsecutiveFailures(),
        h.getAutoDisabledReason(),
        h.getReferrerHostFilter(),
        h.getUtmSourceFilter(),
        h.getFormat());
  }

  private String generateSecret() {
    StringBuilder sb = new StringBuilder(SECRET_LENGTH);
    for (int i = 0; i < SECRET_LENGTH; i++) {
      sb.append(SECRET_ALPHABET.charAt(random.nextInt(SECRET_ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String sanitizeName(String name) {
    if (name == null) return null;
    String trimmed = name.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
  }

  public record WebhookSummary(
      Long id,
      String url,
      String name,
      boolean enabled,
      Instant createdAt,
      Instant lastCalledAt,
      Integer lastStatusCode,
      String lastError,
      boolean includeBots,
      int sampleRate,
      boolean batchEnabled,
      Integer dailyQuota,
      int consecutiveFailures,
      String autoDisabledReason,
      String referrerHostFilter,
      String utmSourceFilter,
      WebhookFormat format) {}

  public record IssuedWebhook(
      Long id, String url, String secret, String name, Instant createdAt, WebhookFormat format) {}

  public record ReDetectResult(int scanned, int formatChanged, int reactivated) {}

  public record ConfigPatch(
      Boolean includeBots,
      Integer sampleRate,
      Boolean batchEnabled,
      Integer dailyQuota,
      String referrerHostFilter,
      String utmSourceFilter) {}
}
