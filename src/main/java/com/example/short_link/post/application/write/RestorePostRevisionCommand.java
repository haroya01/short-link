package com.example.short_link.post.application.write;

public record RestorePostRevisionCommand(Long userId, Long postId, Integer versionNumber) {

  public RestorePostRevisionCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (versionNumber == null || versionNumber < 1) {
      throw new IllegalArgumentException("versionNumber required (>= 1)");
    }
  }
}
