package com.example.short_link.profile.application.email;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stored in {@code profile_block.content}. Null subtitle omits the slot so older records render
 * unchanged.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailFormConfig(
    String title, String subtitle, String placeholder, String successMessage) {

  private static final int TITLE_MAX = 60;

  private static final int SUBTITLE_MAX = 200;

  private static final int PLACEHOLDER_MAX = 60;
  private static final int SUCCESS_MAX = 120;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "email form: config required");
    }
    EmailFormConfig parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), EmailFormConfig.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "email form: malformed json");
    }
    String title = trimTo(parsed.title, TITLE_MAX);
    if (title == null || title.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "email form: title required");
    }
    String subtitle = trimTo(parsed.subtitle, SUBTITLE_MAX);
    String placeholder = trimTo(parsed.placeholder, PLACEHOLDER_MAX);
    String success = trimTo(parsed.successMessage, SUCCESS_MAX);
    EmailFormConfig out = new EmailFormConfig(title, subtitle, placeholder, success);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "email form: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }
}
