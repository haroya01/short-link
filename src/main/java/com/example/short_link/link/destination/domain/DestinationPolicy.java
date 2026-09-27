package com.example.short_link.link.destination.domain;

import java.util.Locale;

public final class DestinationPolicy {
  public static final int MIN_WEIGHT = 1;
  public static final int MAX_WEIGHT = 100;

  private DestinationPolicy() {}

  public static boolean isValidUrl(String url) {
    if (url == null) return false;
    String trimmed = url.trim();
    if (trimmed.isEmpty()) return false;
    String lower = trimmed.toLowerCase(Locale.ROOT);
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  public static int clampWeight(Integer weight) {
    if (weight == null) return MIN_WEIGHT;
    return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
  }

  public static String sanitizeLabel(String label) {
    if (label == null) return null;
    String trimmed = label.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
  }
}
