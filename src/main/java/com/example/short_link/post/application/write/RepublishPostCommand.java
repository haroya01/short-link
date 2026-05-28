package com.example.short_link.post.application.write;

public record RepublishPostCommand(Long userId, Long postId) {

  public RepublishPostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
  }
}
