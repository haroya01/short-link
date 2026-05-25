package com.example.short_link.link.webhook.domain;

import java.net.URI;
import java.util.Locale;

/**
 * Wire format for a webhook POST. Receivers like Discord/Slack reject anything that doesn't match
 * their own contract ({@code content}/{@code text}+{@code blocks}), so the dispatcher needs to
 * branch on this before serializing the payload. {@link #GENERIC} keeps the original kurl JSON +
 * HMAC signature for self-hosted endpoints.
 *
 * <p>Detection is host-based (Discord/Slack publish hooks under fixed domains) and runs once at
 * registration time — the result is persisted on the row so a single domain rename doesn't silently
 * flip the format under live traffic.
 */
public enum WebhookFormat {
  GENERIC,
  DISCORD,
  SLACK;

  /**
   * Pick a format from the receiver URL. Falls back to {@link #GENERIC} on parse failure — the URL
   * has already passed {@code PublicHttpUrlGuard}, so this is just a sniff for the well-known
   * managed receivers.
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
