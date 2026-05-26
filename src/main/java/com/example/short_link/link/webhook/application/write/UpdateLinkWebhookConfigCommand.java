package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;

public record UpdateLinkWebhookConfigCommand(
    Long userId,
    ShortCode shortCode,
    Long webhookId,
    Boolean includeBots,
    Integer sampleRate,
    Boolean batchEnabled,
    Integer dailyQuota,
    String referrerHostFilter,
    String utmSourceFilter,
    WebhookDeliveryMode deliveryMode,
    Integer summaryHourOfDay,
    Integer spikeThreshold,
    Integer spikeWindowMinutes) {

  public UpdateLinkWebhookConfigCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (webhookId == null) throw new IllegalArgumentException("webhookId required");
    if (sampleRate != null && (sampleRate < 1 || sampleRate > 100)) {
      throw new IllegalArgumentException("sampleRate must be between 1 and 100");
    }
    if (summaryHourOfDay != null && (summaryHourOfDay < 0 || summaryHourOfDay > 23)) {
      throw new IllegalArgumentException("summaryHourOfDay must be 0..23");
    }
    if (spikeThreshold != null && spikeThreshold < 1) {
      throw new IllegalArgumentException("spikeThreshold must be >= 1");
    }
    if (spikeWindowMinutes != null && spikeWindowMinutes < 1) {
      throw new IllegalArgumentException("spikeWindowMinutes must be >= 1");
    }
  }
}
