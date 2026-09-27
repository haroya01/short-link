package com.example.short_link.post.application.write;

import java.util.Set;
import java.util.regex.Pattern;

final class PostMetadataValidation {
  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final Set<String> ALLOWED_LANGUAGES = Set.of("ko", "ja", "en");

  private PostMetadataValidation() {}

  static void requireSlug(String slug, String invalidFormatMessage) {
    if (slug.length() < 2 || slug.length() > 200) {
      throw new IllegalArgumentException("slug length 2~200");
    }
    if (!SLUG_PATTERN.matcher(slug).matches()) {
      throw new IllegalArgumentException(invalidFormatMessage);
    }
  }

  static void requireLanguage(String languageTag) {
    if (!ALLOWED_LANGUAGES.contains(languageTag)) {
      throw new IllegalArgumentException("languageTag must be one of: ko, ja, en");
    }
  }
}
