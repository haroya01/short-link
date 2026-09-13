package com.example.short_link.post.note.domain;

import java.time.Instant;

public record NoteRow(Long id, String body, Instant createdAt, long likeCount, AuthorRef author) {

  public record AuthorRef(Long id, String username, String avatarUrl) {}

  /** JPQL constructor projection for the nested author field. */
  public NoteRow(
      Long id,
      String body,
      Instant createdAt,
      long likeCount,
      Long authorId,
      String username,
      String avatarUrl) {
    this(id, body, createdAt, likeCount, new AuthorRef(authorId, username, avatarUrl));
  }
}
