package com.example.short_link.link.webhook.domain;

import java.net.URI;
import java.util.Locale;

/**
 * Persist the detected receiver format at registration so host changes do not silently alter live
 * payloads. {@link #GENERIC} retains the kurl JSON and HMAC contract.
 */
public enum WebhookFormat {
  GENERIC,
  DISCORD,
  SLACK;

  /**
   * Falls back to {@link #GENERIC} on parse failure; URL safety validation belongs to {@code
   * PublicHttpUrlGuard}.
   */
  public static WebhookFormat detect(String url) {
    if (url == null) return GENERIC;
    try {
      String host = URI.create(url).getHost();
      if (host == null) return GENERIC;
      String h = host.toLowerCase(Locale.ROOT);
      if (h.equals("discord.com") || h.equals("discordapp.com") || h.endsWith(".discord.com")) {
        return DISCORD;
      }
      if (h.equals("hooks.slack.com") || h.endsWith(".hooks.slack.com")) {
        return SLACK;
      }
      return GENERIC;
    } catch (IllegalArgumentException e) {
      return GENERIC;
    }
  }
}
