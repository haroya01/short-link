package com.example.short_link.link.application.dto;

import com.example.short_link.link.application.helper.WebhookFormat;
import com.example.short_link.link.domain.LinkWebhookEntity;
import java.time.Instant;

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
    WebhookFormat format) {

  public static WebhookSummary from(LinkWebhookEntity h) {
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
}
