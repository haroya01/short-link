package com.example.short_link.post.webhook.application.dto;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import java.time.Instant;
import java.util.Set;

/** A blog webhook as shown in the settings list — never includes the secret. */
public record BlogWebhookSummary(
    Long id,
    String url,
    String name,
    boolean enabled,
    BlogWebhookFormat format,
    Set<BlogInteractionType> events,
    Instant createdAt,
    Instant lastCalledAt,
    Integer lastStatusCode,
    String lastError,
    int consecutiveFailures,
    String autoDisabledReason) {

  public static BlogWebhookSummary from(BlogWebhookEntity h) {
    return new BlogWebhookSummary(
        h.getId(),
        h.getUrl(),
        h.getName(),
        h.isEnabled(),
        h.getFormat(),
        h.events(),
        h.getCreatedAt(),
        h.getLastCalledAt(),
        h.getLastStatusCode(),
        h.getLastError(),
        h.getConsecutiveFailures(),
        h.getAutoDisabledReason());
  }
}
