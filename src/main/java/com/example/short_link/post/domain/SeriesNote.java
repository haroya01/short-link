package com.example.short_link.post.domain;

import java.time.Instant;

// A note as a series sees it: whose it is, whether everyone may read it, and enough to list it.
public record SeriesNote(
    Long id, Long authorId, String body, String contentWarning, Instant createdAt, boolean shared) {

  private static final int EXCERPT_LENGTH = 80;

  public String excerpt() {
    return excerptOf(body, contentWarning);
  }

  // A warned note is listed by its warning, never by the text the warning hides.
  public static String excerptOf(String body, String contentWarning) {
    String text = contentWarning != null && !contentWarning.isBlank() ? contentWarning : body;
    String flat = text == null ? "" : text.strip().replaceAll("\\s+", " ");
    if (flat.codePointCount(0, flat.length()) <= EXCERPT_LENGTH) {
      return flat;
    }
    return flat.substring(0, flat.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
  }
}
