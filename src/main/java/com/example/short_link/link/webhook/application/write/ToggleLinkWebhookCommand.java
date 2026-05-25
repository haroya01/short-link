package com.example.short_link.link.webhook.application.write;

public record ToggleLinkWebhookCommand(
    Long userId, String shortCode, Long webhookId, boolean enabled) {

  public ToggleLinkWebhookCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (webhookId == null) throw new IllegalArgumentException("webhookId required");
  }
}
