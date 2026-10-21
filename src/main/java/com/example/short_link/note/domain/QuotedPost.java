package com.example.short_link.note.domain;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

public record QuotedPost(
    Long id,
    String title,
    String slug,
    String authorUsername,
    @JsonIgnore Long authorId,
    String audience,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean regionRestricted,
    @JsonInclude(JsonInclude.Include.NON_NULL) Locked locked) {

  public QuotedPost(Long id, String title, String slug, String authorUsername, Long authorId) {
    this(id, title, slug, authorUsername, authorId, PostAudience.DEFAULT, false);
  }

  public QuotedPost(
      Long id,
      String title,
      String slug,
      String authorUsername,
      Long authorId,
      PostAudience audience,
      boolean regionRestricted) {
    this(
        id,
        title,
        slug,
        authorUsername,
        authorId,
        audience.name(),
        regionRestricted ? Boolean.TRUE : null,
        audience.lock() == null ? null : new Locked(audience.lock()));
  }

  public record Locked(PostLock reason) {}
}
