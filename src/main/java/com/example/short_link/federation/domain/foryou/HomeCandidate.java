package com.example.short_link.federation.domain.foryou;

import java.time.Instant;
import java.util.Set;

public record HomeCandidate(
    Source source, ForYouCandidate item, Long noteId, Long owner, Instant receivedAt) {

  public enum Source {
    NOTE,
    POST,
    REMOTE
  }

  public static final String LOCAL_HOST = "";

  public static HomeCandidate note(
      long id,
      long owner,
      String language,
      Set<String> tags,
      Instant createdAt,
      long likes,
      long reposts,
      long replies) {
    return local(Source.NOTE, id, id, owner, language, tags, createdAt, likes, reposts, replies);
  }

  public static HomeCandidate post(
      long id,
      long owner,
      String language,
      Set<String> tags,
      Instant publishedAt,
      long likes,
      long reposts,
      long replies) {
    return local(
        Source.POST, id, null, owner, language, tags, publishedAt, likes, reposts, replies);
  }

  public static HomeCandidate remote(ForYouCandidate item, Long noteId, Instant receivedAt) {
    return new HomeCandidate(Source.REMOTE, item, noteId, null, receivedAt);
  }

  private static HomeCandidate local(
      Source source,
      long id,
      Long noteId,
      long owner,
      String language,
      Set<String> tags,
      Instant createdAt,
      long likes,
      long reposts,
      long replies) {
    return new HomeCandidate(
        source,
        new ForYouCandidate(
            id,
            "kurl:" + (source == Source.NOTE ? "note" : "post") + ":" + id,
            "u" + owner,
            LOCAL_HOST,
            language,
            tags,
            createdAt,
            likes,
            reposts,
            replies,
            id),
        noteId,
        owner,
        null);
  }

  public String key() {
    return item.uri();
  }

  public boolean local() {
    return source != Source.REMOTE;
  }

  public Instant received() {
    return receivedAt == null ? item.createdAt() : receivedAt;
  }
}
