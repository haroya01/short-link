package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockType;
import java.util.List;

public record ReplacePostBlocksCommand(Long userId, Long postId, List<BlockInput> blocks) {

  public ReplacePostBlocksCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (blocks == null) throw new IllegalArgumentException("blocks required (can be empty list)");
    if (blocks.size() > 500) {
      throw new IllegalArgumentException("blocks max 500 per post");
    }
    for (BlockInput b : blocks) {
      if (b == null) throw new IllegalArgumentException("block entry cannot be null");
      if (b.type() == null) throw new IllegalArgumentException("block type required");
      if (b.content() != null && b.content().length() > 100_000) {
        throw new IllegalArgumentException("block content max 100,000 chars");
      }
    }
  }

  public record BlockInput(PostBlockType type, String content) {}
}
