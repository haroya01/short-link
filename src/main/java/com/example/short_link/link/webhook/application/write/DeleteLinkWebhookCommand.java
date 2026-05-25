package com.example.short_link.link.webhook.application.write;

public record DeleteLinkWebhookCommand(Long userId, String shortCode, Long webhookId) {

  public DeleteLinkWebhookCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (webhookId == null) throw new IllegalArgumentException("webhookId required");
  }
}
