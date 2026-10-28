package com.example.short_link.post.application.write;

public record CreatePostCommand(
    Long userId, String slug, String title, String languageTag, PostAccessChange access) {

  public CreatePostCommand(Long userId, String slug, String title, String languageTag) {
    this(userId, slug, title, languageTag, PostAccessChange.NONE);
  }

  public CreatePostCommand {
    if (access == null) access = PostAccessChange.NONE;
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug required");
    PostMetadataValidation.requireSlug(
        slug, "slug must be lowercase alphanumeric with single hyphens (e.g., my-first-post)");
    // Title may be blank while drafting; PostEntity.publish() and schedule() require it.
    if (title == null) title = "";
    if (title.length() > 200) throw new IllegalArgumentException("title max 200");
    if (languageTag == null || languageTag.isBlank()) {
      languageTag = "ko";
    }
    PostMetadataValidation.requireLanguage(languageTag);
  }
}
