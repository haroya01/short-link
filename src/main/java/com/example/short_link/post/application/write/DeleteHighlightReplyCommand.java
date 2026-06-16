package com.example.short_link.post.application.write;

public record DeleteHighlightReplyCommand(Long userId, Long replyId) {

  public DeleteHighlightReplyCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (replyId == null) throw new IllegalArgumentException("replyId required");
  }
}
