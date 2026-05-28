package com.example.short_link.post.application.write;

import java.util.Set;
import java.util.regex.Pattern;

public record CreatePostCommand(Long userId, String slug, String title, String languageTag) {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final Set<String> ALLOWED_LANGUAGES = Set.of("ko", "ja", "en");

  public CreatePostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug required");
    if (slug.length() < 2 || slug.length() > 200) {
      throw new IllegalArgumentException("slug length 2~200");
    }
    if (!SLUG_PATTERN.matcher(slug).matches()) {
      throw new IllegalArgumentException(
          "slug must be lowercase alphanumeric with single hyphens (e.g., my-first-post)");
    }
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
    if (title.length() > 200) throw new IllegalArgumentException("title max 200");
    if (languageTag == null || languageTag.isBlank()) {
      languageTag = "ko";
    }
    if (!ALLOWED_LANGUAGES.contains(languageTag)) {
      throw new IllegalArgumentException("languageTag must be one of: ko, ja, en");
    }
  }
}
