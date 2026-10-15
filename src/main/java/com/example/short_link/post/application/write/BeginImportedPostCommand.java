package com.example.short_link.post.application.write;

public record BeginImportedPostCommand(
    Long userId, Long importSourceId, String platform, String sourceUrl, Long resumePostId) {

  public BeginImportedPostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (importSourceId == null) throw new IllegalArgumentException("importSourceId required");
    if (platform == null || platform.isBlank()) {
      throw new IllegalArgumentException("platform required");
    }
    if (sourceUrl == null || sourceUrl.isBlank() || sourceUrl.length() > 2048) {
      throw new IllegalArgumentException("sourceUrl required, max 2048");
    }
  }
}
