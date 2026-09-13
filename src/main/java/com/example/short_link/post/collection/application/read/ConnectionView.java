package com.example.short_link.post.collection.application.read;

import java.time.Instant;

/**
 * {@code blockType}에 따라 필드를 해석한다: POST는 title/excerpt/slug/username, HIGHLIGHT는 quote와 원문 정보, NOTE는
 * body. {@code why}는 큐레이터가 적은 연결 이유다.
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
