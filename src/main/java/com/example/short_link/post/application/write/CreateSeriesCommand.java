package com.example.short_link.post.application.write;

import java.util.regex.Pattern;

public record CreateSeriesCommand(Long userId, String slug, String title) {

  static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public CreateSeriesCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (slug == null || slug.length() < 2 || slug.length() > 200) {
      throw new IllegalArgumentException("slug length 2~200");
    }
    if (!SLUG_PATTERN.matcher(slug).matches()) {
      throw new IllegalArgumentException("slug invalid format");
    }
    if (title == null || title.isBlank() || title.length() > 200) {
      throw new IllegalArgumentException("title required, max 200");
    }
  }
}
