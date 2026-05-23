package com.example.short_link.billing.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.stripe")
public record StripeProperties(
    String apiKey,
    String webhookSecret,
    String pricePro,
    String successUrl,
    String cancelUrl,
    String portalReturnUrl) {

  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank() && pricePro != null && !pricePro.isBlank();
  }
}
