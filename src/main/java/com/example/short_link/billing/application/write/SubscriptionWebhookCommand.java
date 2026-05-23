package com.example.short_link.billing.application.write;

public record SubscriptionWebhookCommand(String payload, String signature) {

  public SubscriptionWebhookCommand {
    if (payload == null) throw new IllegalArgumentException("payload required");
    if (signature == null) throw new IllegalArgumentException("signature required");
  }
}
