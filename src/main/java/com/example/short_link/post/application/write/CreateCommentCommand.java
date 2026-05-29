package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.CommentEntity;

public record CreateCommentCommand(Long userId, Long postId, Long parentId, String body) {

  public CreateCommentCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (body == null || body.isBlank()) throw new IllegalArgumentException("body required");
    if (body.length() > CommentEntity.MAX_BODY) {
      throw new IllegalArgumentException("body too long");
    }
  }
}
