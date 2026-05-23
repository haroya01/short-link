package com.example.short_link.link.application.write;

public record UpdateLinkWebhookConfigCommand(
    Long userId,
    String shortCode,
    Long webhookId,
    Boolean includeBots,
    Integer sampleRate,
    Boolean batchEnabled,
    Integer dailyQuota,
    String referrerHostFilter,
    String utmSourceFilter) {

  public UpdateLinkWebhookConfigCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (webhookId == null) throw new IllegalArgumentException("webhookId required");
    if (sampleRate != null && (sampleRate < 1 || sampleRate > 100)) {
      throw new IllegalArgumentException("sampleRate must be between 1 and 100");
    }
  }
}
