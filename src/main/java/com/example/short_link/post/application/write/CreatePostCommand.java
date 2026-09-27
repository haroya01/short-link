package com.example.short_link.post.application.write;

public record CreatePostCommand(Long userId, String slug, String title, String languageTag) {

  public CreatePostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug required");
    PostMetadataValidation.requireSlug(
        slug, "slug must be lowercase alphanumeric with single hyphens (e.g., my-first-post)");
    // Title may be blank while drafting; it's required only at publish (PostEntity.publish()).
    if (title == null) title = "";
    if (title.length() > 200) throw new IllegalArgumentException("title max 200");
    if (languageTag == null || languageTag.isBlank()) {
      languageTag = "ko";
    }
    PostMetadataValidation.requireLanguage(languageTag);
  }
}
