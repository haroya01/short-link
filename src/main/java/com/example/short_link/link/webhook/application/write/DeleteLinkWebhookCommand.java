package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.domain.ShortCode;

public record DeleteLinkWebhookCommand(Long userId, ShortCode shortCode, Long webhookId) {

  public DeleteLinkWebhookCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (webhookId == null) throw new IllegalArgumentException("webhookId required");
  }
}
