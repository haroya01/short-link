package com.example.short_link.post.application.write;

public record UnpublishPostCommand(Long userId, Long postId) {

  public UnpublishPostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
  }
}
