package com.example.short_link.link.access.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Turnstile (bot challenge) config for the password gate. Both keys default to empty —
 * when unset the widget is not rendered and verification is skipped, so the gate works unchanged.
 * Set {@code short-link.turnstile.site-key} and {@code .secret} (Cloudflare dashboard) to enable.
 */
@ConfigurationProperties(prefix = "short-link.turnstile")
public record TurnstileProperties(String siteKey, String secret) {

  public TurnstileProperties {
    siteKey = siteKey == null ? "" : siteKey.trim();
    secret = secret == null ? "" : secret.trim();
  }

  /** Render the widget on the prompt only when a site key is configured. */
  public boolean widgetEnabled() {
    return !siteKey.isBlank();
  }

  /** Verify the token on unlock only when a secret is configured. */
  public boolean verifyEnabled() {
    return !secret.isBlank();
  }
}
