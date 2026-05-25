package com.example.short_link.profile.application;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whitelist + normalization for the user's social links shown at the bottom of the public profile.
 * Each entry pairs a {@link Social#channel} from {@link #ALLOWED} with the user's own URL —
 * visitors click the X button and land on the owner's X profile, not on a tweet-intent page. Capped
 * at {@link #MAX} for visual balance.
 *
 * <p>Persisted shape is a compact JSON array (e.g. {@code
 * [{"channel":"x","url":"https://x.com/foo"}]}) stored in {@code users.socials}.
 */
public final class Socials {

  public static final List<String> ALLOWED =
      List.of("x", "line", "threads", "facebook", "kakao", "instagram", "linkedin");

  private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED);

  public static final int MAX = 2;

  private static final int URL_MAX = 256;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<List<Social>> LIST_TYPE = new TypeReference<>() {};

  private Socials() {}

  public record Social(String channel, String url) {}

  /**
   * Parse + validate JSON from the API. {@code null}/blank returns null (= no socials). Order is
   * preserved from the caller; duplicates by channel are dropped (first wins). Throws if any entry
   * has an unknown channel / invalid URL or the count exceeds {@link #MAX}; the controller turns
   * that into a 400.
   */
  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    List<Social> parsed;
    try {
      parsed = MAPPER.readValue(trimmed, LIST_TYPE);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: malformed json");
    }
    if (parsed == null) return null;
    if (parsed.size() > MAX) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: up to " + MAX + " allowed");
    }
    List<Social> out = new ArrayList<>(parsed.size());
    Set<String> seen = new HashSet<>();
    for (Social s : parsed) {
      if (s == null) continue;
      String channel = s.channel() == null ? "" : s.channel().trim().toLowerCase();
      String url = s.url() == null ? "" : s.url().trim();
      if (channel.isEmpty() || url.isEmpty()) continue;
      if (!ALLOWED_SET.contains(channel)) {
        throw new ProfileException(
            ProfileErrorCode.INVALID_USERNAME, "socials: unknown channel: " + channel);
      }
      if (url.length() > URL_MAX) {
        throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url too long");
      }
      validateUrl(url);
      if (!seen.add(channel)) continue;
      out.add(new Social(channel, url));
    }
    if (out.isEmpty()) return null;
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: serialization failed");
    }
  }

  /** Render the stored JSON as a list for DTO emission. {@code null}/blank maps to empty list. */
  public static List<Social> toList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      List<Social> parsed = MAPPER.readValue(json, LIST_TYPE);
      return parsed == null ? List.of() : parsed;
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  private static void validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url missing host");
    }
  }
}
