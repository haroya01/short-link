package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.domain.ShortCode;

public record RegisterLinkWebhookCommand(
    Long userId, ShortCode shortCode, String url, String name) {

  public RegisterLinkWebhookCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
  }
}
