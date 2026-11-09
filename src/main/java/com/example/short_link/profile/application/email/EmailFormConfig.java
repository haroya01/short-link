package com.example.short_link.profile.application.email;

import static com.example.short_link.profile.domain.contact.BlockConfig.trimTo;

import com.example.short_link.profile.domain.contact.BlockConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Stored in profile_block.content. Null subtitle omits the slot so older records render unchanged.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailFormConfig(
    String title, String subtitle, String placeholder, String successMessage) {

  private static final int TITLE_MAX = 60;

  private static final int SUBTITLE_MAX = 200;

  private static final int PLACEHOLDER_MAX = 60;
  private static final int SUCCESS_MAX = 120;

  public static String normalize(String raw) {
    EmailFormConfig parsed = BlockConfig.read(raw, EmailFormConfig.class, "email form");
    String title = trimTo(parsed.title, TITLE_MAX);
    if (title == null || title.isEmpty()) {
      throw BlockConfig.invalid("email form: title required");
    }
    String subtitle = trimTo(parsed.subtitle, SUBTITLE_MAX);
    String placeholder = trimTo(parsed.placeholder, PLACEHOLDER_MAX);
    String success = trimTo(parsed.successMessage, SUCCESS_MAX);
    EmailFormConfig out = new EmailFormConfig(title, subtitle, placeholder, success);
    return BlockConfig.write(out, "email form");
  }
}
