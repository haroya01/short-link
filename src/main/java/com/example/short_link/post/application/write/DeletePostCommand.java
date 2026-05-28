package com.example.short_link.post.application.write;

public record DeletePostCommand(Long userId, Long postId) {

  public DeletePostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
  }
}
