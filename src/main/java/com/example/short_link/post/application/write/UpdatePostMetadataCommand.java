package com.example.short_link.post.application.write;

import java.util.List;

public record UpdatePostMetadataCommand(
    Long userId,
    Long postId,
    String title,
    String slug,
    String excerpt,
    String ogImageUrl,
    String ogImageKey,
    Boolean coverChosen,
    String languageTag,
    List<String> tags,
    Long baseVersion,
    boolean overwrite) {

  public UpdatePostMetadataCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (title != null && title.length() > 200) {
      throw new IllegalArgumentException("title max 200");
    }
    if (slug != null) {
      PostMetadataValidation.requireSlug(slug, "slug invalid format");
    }
    if (excerpt != null && excerpt.length() > 500) {
      throw new IllegalArgumentException("excerpt max 500");
    }
    if (languageTag != null && !languageTag.isBlank()) {
      PostMetadataValidation.requireLanguage(languageTag);
    }
  }
}
