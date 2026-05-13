package com.example.short_link.profile.email;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EMAIL_FORM block payload. Stored as JSON in {@code profile_block.content}; the editor controls
 * the visible copy (title / subtitle / placeholder / success message) without us inventing
 * per-field columns. Lengths are capped to keep the rendered form sane on mobile.
 *
 * <p>{@code subtitle} carries the "why should I leave my email?" value-prop sentence that sits
 * between the title and the input — added (PR #...) when the form felt anonymous without it. Null
 * when the seller doesn't write one; renderer skips the slot entirely so legacy records render
 * identically.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailFormConfig(
    String title, String subtitle, String placeholder, String successMessage) {

  private static final int TITLE_MAX = 60;

  /**
   * Cap is larger than the success-message slot (success is a one-liner) but smaller than a full
   * markdown body — this is a single short paragraph, not a sales page.
   */
  private static final int SUBTITLE_MAX = 200;

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
    String subtitle = trimTo(parsed.subtitle, SUBTITLE_MAX);
    String placeholder = trimTo(parsed.placeholder, PLACEHOLDER_MAX);
    String success = trimTo(parsed.successMessage, SUCCESS_MAX);
    EmailFormConfig out = new EmailFormConfig(title, subtitle, placeholder, success);
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
