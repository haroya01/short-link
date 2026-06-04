package com.example.short_link.post.webhook.domain;

import java.net.URI;
import java.util.Locale;

/**
 * How a blog webhook's body is shaped for its receiver. GENERIC sends signed raw JSON (self-hosted
 * endpoints verify the HMAC); DISCORD/SLACK send the chat-native shapes those hosts expect (and
 * skip the signature, which they don't support). Auto-detected once at registration from the URL
 * host.
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
