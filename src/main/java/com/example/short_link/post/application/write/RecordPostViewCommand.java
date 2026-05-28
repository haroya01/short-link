package com.example.short_link.post.application.write;

public record RecordPostViewCommand(String username, String slug) {

  public RecordPostViewCommand {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("username required");
    }
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("slug required");
    }
  }
}
