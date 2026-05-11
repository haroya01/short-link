package com.example.short_link.profile.application;

import java.util.List;
import java.util.Set;

/**
 * Whitelist + normalization for the user-selectable share channels rendered on the public profile.
 * The set is intentionally small — we surface these as branded buttons (each with its own SVG icon)
 * so adding a channel means a frontend asset too, not just a string.
 *
 * <p>Order in {@link #ALLOWED} is the order shown to the user in the editor. The user's chosen
 * order is preserved separately in the CSV.
 */
public final class ShareChannels {

  public static final List<String> ALLOWED = List.of("x", "line", "threads", "facebook", "kakao");

  private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED);

  public static final int MAX = 2;

  private ShareChannels() {}

  /**
   * Parse + validate a CSV from the API. Empty / null returns {@code ""} (= no channels). Order is
   * preserved from the caller's CSV. Throws if any entry is unknown or the count exceeds {@link
   * #MAX}; the controller turns that into a 400.
   */
  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return "";
    String[] parts = trimmed.split(",");
    if (parts.length > MAX) {
      throw new InvalidUsernameException("share channels: up to " + MAX + " allowed");
    }
    StringBuilder out = new StringBuilder();
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (String p : parts) {
      String v = p.trim().toLowerCase();
      if (v.isEmpty()) continue;
      if (!ALLOWED_SET.contains(v)) {
        throw new InvalidUsernameException("unknown share channel: " + v);
      }
      if (!seen.add(v)) continue; // dedupe
      if (out.length() > 0) out.append(',');
      out.append(v);
    }
    return out.toString();
  }

  /** Render the stored CSV as a list for DTO emission. {@code null}/empty maps to empty list. */
  public static List<String> toList(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    String[] parts = csv.split(",");
    return java.util.Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
