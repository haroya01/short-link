package com.example.short_link.post.webhook.domain;

import java.net.URI;
import java.util.Locale;

/**
 * GENERIC sends signed JSON; DISCORD/SLACK use chat payloads without HMAC signatures. The format is
 * detected from the URL host at registration.
 */
public enum BlogWebhookFormat {
  GENERIC,
  DISCORD,
  SLACK;

  public static BlogWebhookFormat detect(String url) {
    String host = hostOf(url);
    if (host == null) {
      return GENERIC;
    }
    if (host.equals("discord.com")
        || host.equals("discordapp.com")
        || host.endsWith(".discord.com")) {
      return DISCORD;
    }
    if (host.equals("hooks.slack.com") || host.endsWith(".hooks.slack.com")) {
      return SLACK;
    }
    return GENERIC;
  }

  private static String hostOf(String url) {
    try {
      String host = URI.create(url).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
