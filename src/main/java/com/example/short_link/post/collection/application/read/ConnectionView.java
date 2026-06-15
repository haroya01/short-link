package com.example.short_link.post.collection.application.read;

import java.time.Instant;

/**
 * 연결된 한 블록의 해석된 뷰 — {@code blockType} 으로 갈라 읽는다(클라가 종류별 다른 실루엣으로 렌더). 공통 필드를 종류 간 재사용한다: POST 는
 * title/excerpt/slug/username, HIGHLIGHT 는 quote + 원문 title/slug/username, NOTE 는 body. {@code why}
 * 는 큐레이터의 한 줄.
 */
public record ConnectionView(
    Long id,
    String blockType,
    String why,
    Instant connectedAt,
    String title,
    String excerpt,
    String slug,
    String username,
    String quote,
    String body) {

  public static ConnectionView post(
      Long id, String why, Instant at, String title, String excerpt, String slug, String username) {
    return new ConnectionView(id, "POST", why, at, title, excerpt, slug, username, null, null);
  }

  public static ConnectionView highlight(
      Long id,
      String why,
      Instant at,
      String quote,
      String postTitle,
      String slug,
      String username) {
    return new ConnectionView(
        id, "HIGHLIGHT", why, at, postTitle, null, slug, username, quote, null);
  }

  public static ConnectionView note(Long id, String why, Instant at, String body) {
    return new ConnectionView(id, "NOTE", why, at, null, null, null, null, null, body);
  }
}
