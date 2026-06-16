package com.example.short_link.post.application.write;

public record CreateHighlightCommand(
    Long userId,
    Long postId,
    Integer blockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    String note) {

  public CreateHighlightCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (blockOrder == null || blockOrder < 0)
      throw new IllegalArgumentException("blockOrder required");
    if (startOffset == null || startOffset < 0)
      throw new IllegalArgumentException("startOffset required and non-negative");
    if (endOffset == null || endOffset <= startOffset)
      throw new IllegalArgumentException("endOffset must be greater than startOffset");
    if (quote == null || quote.isBlank()) throw new IllegalArgumentException("quote required");
  }
}
