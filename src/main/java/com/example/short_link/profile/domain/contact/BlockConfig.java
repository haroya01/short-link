package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public final class BlockConfig {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BlockConfig() {}

  public static <T> T read(String raw, Class<T> type, String block) {
    requirePresent(raw, block);
    try {
      return MAPPER.readValue(raw.trim(), type);
    } catch (JsonProcessingException ex) {
      throw invalid(block + ": malformed json");
    }
  }

  public static <T> T read(String raw, TypeReference<T> type, String block) {
    requirePresent(raw, block);
    try {
      return MAPPER.readValue(raw.trim(), type);
    } catch (JsonProcessingException ex) {
      throw invalid(block + ": malformed json");
    }
  }

  public static String write(Object config, String block) {
    try {
      return MAPPER.writeValueAsString(config);
    } catch (JsonProcessingException ex) {
      throw invalid(block + ": serialization failed");
    }
  }

  public static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  public static void requireHttpUrl(String url, String subject) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw invalid(subject + " malformed");
    }
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw invalid(subject + " must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw invalid(subject + " missing host");
    }
  }

  public static Optional<URI> httpUri(String url) {
    if (url == null || url.isBlank()) return Optional.empty();
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) return Optional.empty();
    if (uri.getHost() == null || uri.getHost().isBlank()) return Optional.empty();
    return Optional.of(uri);
  }

  public static ProfileException invalid(String message) {
    return new ProfileException(ProfileErrorCode.INVALID_USERNAME, message);
  }

  private static void requirePresent(String raw, String block) {
    if (raw == null || raw.isBlank()) {
      throw invalid(block + ": config required");
    }
  }
}
