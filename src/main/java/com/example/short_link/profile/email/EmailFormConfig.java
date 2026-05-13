package com.example.short_link.profile.email;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EMAIL_FORM block payload. Stored as JSON in {@code profile_block.content}; the editor controls
 * the visible copy (title / placeholder / success message) without us inventing per-field columns.
 * Lengths are capped to keep the rendered form sane on mobile.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailFormConfig(String title, String placeholder, String successMessage) {

  private static final int TITLE_MAX = 60;
  private static final int PLACEHOLDER_MAX = 60;
  private static final int SUCCESS_MAX = 120;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("email form: config required");
    }
    EmailFormConfig parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), EmailFormConfig.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("email form: malformed json");
    }
    String title = trimTo(parsed.title, TITLE_MAX);
    if (title == null || title.isEmpty()) {
      throw new InvalidUsernameException("email form: title required");
    }
    String placeholder = trimTo(parsed.placeholder, PLACEHOLDER_MAX);
    String success = trimTo(parsed.successMessage, SUCCESS_MAX);
    EmailFormConfig out = new EmailFormConfig(title, placeholder, success);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("email form: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }
}
