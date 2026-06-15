package com.example.short_link.post.collection.application.write;

import com.example.short_link.post.collection.domain.ConnectionBlockType;

public record ConnectBlockCommand(
    Long userId, Long collectionId, ConnectionBlockType blockType, Long refId, String why) {

  public ConnectBlockCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (collectionId == null) throw new IllegalArgumentException("collectionId required");
    if (blockType == null) throw new IllegalArgumentException("blockType required");
    if (refId == null) throw new IllegalArgumentException("refId required");
  }
}
