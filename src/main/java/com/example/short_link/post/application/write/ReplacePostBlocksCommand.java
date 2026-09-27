package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;

public record ReplacePostBlocksCommand(Long userId, Long postId, List<BlockInput> blocks) {

  public ReplacePostBlocksCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (blocks == null) throw new IllegalArgumentException("blocks required (can be empty list)");
    // 본문 한도 오류는 클라이언트가 사유를 표시할 수 있도록 PostException의 ProblemDetail로 전달한다.
    if (blocks.size() > 500) {
      throw new PostException(PostErrorCode.BODY_LIMIT, "본문이 너무 깁니다 — 블록(문단·이미지 등)은 글당 최대 500개예요");
    }
    for (BlockInput b : blocks) {
      if (b == null) throw new IllegalArgumentException("block entry cannot be null");
      if (b.type() == null) throw new IllegalArgumentException("block type required");
      if (b.content() != null && b.content().length() > 100_000) {
        throw new PostException(PostErrorCode.BODY_LIMIT, "블록 하나가 너무 깁니다 — 블록당 최대 100,000자예요");
      }
    }
  }

  public record BlockInput(PostBlockType type, String content) {}
}
