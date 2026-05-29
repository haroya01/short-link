package com.example.short_link.post.application.write;

public record DeleteCommentCommand(Long userId, Long commentId) {

  public DeleteCommentCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (commentId == null) throw new IllegalArgumentException("commentId required");
  }
}
