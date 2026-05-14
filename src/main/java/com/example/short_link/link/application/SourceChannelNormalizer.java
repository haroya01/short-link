package com.example.short_link.link.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Trims and validates the {@code ?src=...} channel hint that the short URL carries. Anything
 * non-alphanumeric (plus {@code . - _}) is rejected so we don't store junk or potential XSS
 * payloads — a URL like {@code /abc?src=<script>} just gets a null channel.
 */
public final class SourceChannelNormalizer {

  public static final int MAX_LENGTH = 40;
  private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._-]{1,40}$");

  private SourceChannelNormalizer() {}

  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH);
    if (!ALLOWED.matcher(trimmed).matches()) return null;
    return trimmed.toLowerCase(Locale.ROOT);
  }
}
