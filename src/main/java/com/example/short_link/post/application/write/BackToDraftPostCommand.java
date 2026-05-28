package com.example.short_link.post.application.write;

public record BackToDraftPostCommand(Long userId, Long postId) {

  public BackToDraftPostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
  }
}
