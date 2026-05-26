package com.example.short_link.admin.application.helper;

import java.net.URI;
import java.util.Locale;

public final class BlockedDomainNormalizer {

  private BlockedDomainNormalizer() {}

  public static String normalize(String input) {
    if (input == null) return null;
    String trimmed = input.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) return null;
    String stripped = trimmed.replaceFirst("^https?://", "");
    int slash = stripped.indexOf('/');
    if (slash >= 0) stripped = stripped.substring(0, slash);
    if (stripped.startsWith("www.")) stripped = stripped.substring(4);
    return stripped.isEmpty() ? null : stripped;
  }

  public static String hostOf(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      String host = URI.create(url).getHost();
      if (host == null) return null;
      String lower = host.toLowerCase(Locale.ROOT);
      return lower.startsWith("www.") ? lower.substring(4) : lower;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
