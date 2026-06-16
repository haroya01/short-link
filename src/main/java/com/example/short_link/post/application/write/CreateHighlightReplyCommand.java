package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostHighlightReplyEntity;

public record CreateHighlightReplyCommand(Long userId, Long highlightId, String body) {

  public CreateHighlightReplyCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (highlightId == null) throw new IllegalArgumentException("highlightId required");
    if (body == null || body.isBlank()) throw new IllegalArgumentException("body required");
    if (body.length() > PostHighlightReplyEntity.MAX_BODY) {
      throw new IllegalArgumentException("body too long");
    }
  }
}
