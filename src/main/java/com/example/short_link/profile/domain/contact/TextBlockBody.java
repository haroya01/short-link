package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

// Reads JSON {body, layout?, accent?, icon?} and legacy markdown strings; writes always emit JSON.
// Legacy strings use inline layout with no accent or icon. Rendering and raw-HTML filtering belong
// to the frontend.
public final class TextBlockBody {

  private static final int BODY_MAX = 2000;

  private static final int ICON_MAX = 8;

  // Unknown layouts fall back to inline so a frontend deployed ahead of the backend does not reject
  // writes.
  private static final Set<String> LAYOUT_IDS = Set.of("inline", "card", "quote");

  private static final Set<String> ACCENT_IDS = Set.of("blue", "amber", "green", "red", "violet");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TextBlockBody() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String body, String layout, String accent, String icon) {}

  private record PayloadOut(String body, String layout, String accent, String icon) {}

  public static String normalize(String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block content required");
    }
    PayloadOut read = read(trimmed);
    String body = read.body().trim();
    if (body.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block content required");
    }
    if (body.length() > BODY_MAX) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "text block too long");
    }
    return BlockConfig.write(
        new PayloadOut(body, read.layout(), read.accent(), read.icon()), "text block");
  }

  // Try JSON after a leading brace; invalid JSON or missing body uses legacy markdown.
  private static PayloadOut read(String trimmed) {
    Payload parsed = trimmed.startsWith("{") ? payload(trimmed) : null;
    if (parsed == null || parsed.body() == null) {
      return new PayloadOut(trimmed, "inline", null, null);
    }
    return new PayloadOut(
        parsed.body(),
        normalizeLayout(parsed.layout()),
        normalizeAccent(parsed.accent()),
        trimIcon(parsed.icon()));
  }

  private static Payload payload(String json) {
    try {
      return MAPPER.readValue(json, Payload.class);
    } catch (JsonProcessingException ex) {
      return null;
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
