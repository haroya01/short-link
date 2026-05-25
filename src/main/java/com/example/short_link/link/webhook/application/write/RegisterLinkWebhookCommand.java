package com.example.short_link.link.webhook.application.write;

public record RegisterLinkWebhookCommand(Long userId, String shortCode, String url, String name) {

  public RegisterLinkWebhookCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
    if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
  }
}
