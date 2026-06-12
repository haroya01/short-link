package com.example.short_link.post.note.application.read;

import java.time.Instant;

/** 노트 피드 한 줄 — 작성자 셀과 좋아요 수까지 한 번에(클라이언트 N+1 금지). */
public record NoteRow(Long id, String body, Instant createdAt, long likeCount, AuthorRef author) {

  public record AuthorRef(Long id, String username, String avatarUrl) {}

  /** JPQL 생성자 표현식용 — 평면 인자를 받아 중첩 author 로 접는다. */
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
