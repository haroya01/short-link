package com.example.short_link.post.application.write;

public record CreateHighlightCommand(
    Long userId,
    Long postId,
    Integer blockOrder,
    Integer endBlockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    String note) {

  public CreateHighlightCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (blockOrder == null || blockOrder < 0)
      throw new IllegalArgumentException("blockOrder required");
    if (endBlockOrder != null && endBlockOrder < blockOrder)
      throw new IllegalArgumentException("endBlockOrder must be >= blockOrder");
    if (startOffset == null || startOffset < 0)
      throw new IllegalArgumentException("startOffset required and non-negative");
    if (endOffset == null || endOffset < 0)
      throw new IllegalArgumentException("endOffset required and non-negative");
    // 단일 블록에서만 두 offset을 비교할 수 있다. 여러 블록이면 각 offset의 기준 블록이 다르다.
    if ((endBlockOrder == null || endBlockOrder.equals(blockOrder)) && endOffset <= startOffset)
      throw new IllegalArgumentException("endOffset must be greater than startOffset");
    if (quote == null || quote.isBlank()) throw new IllegalArgumentException("quote required");
  }
}
