package com.example.short_link.admin.application.helper;

import com.example.short_link.common.net.BrowserHost;

public final class BlockedDomainNormalizer {

  private BlockedDomainNormalizer() {}

  public static String normalize(String input) {
    if (input == null || input.isBlank()) return null;
    String trimmed = input.strip();
    return hostOf(trimmed.contains("://") ? trimmed : "https://" + trimmed);
  }

  public static String hostOf(String url) {
    if (url == null || url.isBlank()) return null;
    String host = BrowserHost.of(url);
    if (host == null) return null;
    return host.startsWith("www.") ? host.substring(4) : host;
  }
}
