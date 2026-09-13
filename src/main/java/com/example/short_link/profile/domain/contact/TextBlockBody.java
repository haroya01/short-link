package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/**
 * Reads JSON {@code {body, layout?, accent?, icon?}} and legacy markdown strings; writes always
 * emit JSON. Legacy strings use inline layout with no accent or icon. Rendering and raw-HTML
 * filtering belong to the frontend.
 */
public final class TextBlockBody {

  private static final int BODY_MAX = 2000;

  private static final int ICON_MAX = 8;

  /**
   * Unknown layouts fall back to inline so a frontend deployed ahead of the backend does not reject
   * writes.
   */
  private static final Set<String> LAYOUT_IDS = Set.of("inline", "card", "quote");

  private static final Set<String> ACCENT_IDS = Set.of("blue", "amber", "green", "red", "violet");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TextBlockBody() {}

  /** {@code body} is required; visual hints are optional. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String body, String layout, String accent, String icon) {}

  private record PayloadOut(String body, String layout, String accent, String icon) {}

  public static String normalize(String raw) {
    if (raw == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block content required");
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block content required");
    }

    String body;
    String layout = "inline";
    String accent = null;
    String icon = null;

    // Try JSON after a leading brace; invalid JSON or missing body uses legacy markdown.
    if (trimmed.startsWith("{")) {
      try {
        Payload parsed = MAPPER.readValue(trimmed, Payload.class);
        if (parsed != null && parsed.body() != null) {
          body = parsed.body();
          layout = normalizeLayout(parsed.layout());
          accent = normalizeAccent(parsed.accent());
          icon = trimIcon(parsed.icon());
        } else {
          // Object with no body field — treat the whole raw string as legacy markdown.
          body = trimmed;
        }
      } catch (JsonProcessingException ex) {
        // Looked like JSON but didn't parse — treat as legacy markdown verbatim.
        body = trimmed;
      }
    } else {
      body = trimmed;
    }

    body = body.trim();
    if (body.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block content required");
    }
    if (body.length() > BODY_MAX) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block too long");
    }

    try {
      return MAPPER.writeValueAsString(new PayloadOut(body, layout, accent, icon));
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "text block: serialization failed");
    }
  }

  private static String normalizeLayout(String raw) {
    if (raw == null) return "inline";
    String t = raw.trim();
    return LAYOUT_IDS.contains(t) ? t : "inline";
  }

  private static String normalizeAccent(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    return ACCENT_IDS.contains(t) ? t : null;
  }

  private static String trimIcon(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    return t.length() <= ICON_MAX ? t : t.substring(0, ICON_MAX);
  }
}
