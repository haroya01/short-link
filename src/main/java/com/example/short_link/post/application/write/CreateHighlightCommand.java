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
    // endBlockOrder 가 비면 단일 블록 하이라이트로 본다. 이때만 offset 들이 같은 블록 안에 있어
    // endOffset 이 startOffset 보다 커야 한다. 여러 블록에 걸치면 offset 은 서로 다른 블록 기준이라
    // 이 비교가 성립하지 않는다.
    if ((endBlockOrder == null || endBlockOrder.equals(blockOrder)) && endOffset <= startOffset)
      throw new IllegalArgumentException("endOffset must be greater than startOffset");
    if (quote == null || quote.isBlank()) throw new IllegalArgumentException("quote required");
  }
}
