package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

// A deleted comment that still has replies stays as their parent: id, place and time only.
public record CommentView(
    Long id,
    Long parentId,
    PublicAuthorView author,
    String body,
    Instant createdAt,
    long likeCount,
    List<String> mentions,
    boolean deleted) {

  public CommentView(
      Long id,
      Long parentId,
      PublicAuthorView author,
      String body,
      Instant createdAt,
      long likeCount,
      List<String> mentions) {
    this(id, parentId, author, body, createdAt, likeCount, mentions, false);
  }

  static CommentView tombstone(Long id, Long parentId, Instant createdAt) {
    return new CommentView(id, parentId, null, null, createdAt, 0L, List.of(), true);
  }
}
