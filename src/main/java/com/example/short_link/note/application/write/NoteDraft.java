package com.example.short_link.note.application.write;

import java.util.List;

public record NoteDraft(
    String body,
    List<Image> images,
    Long quotedPostId,
    Long inReplyToId,
    Long quotedNoteId,
    String contentWarning,
    boolean sensitive) {

  public NoteDraft(String body, List<Image> images, Long quotedPostId, Long inReplyToId) {
    this(body, images, quotedPostId, inReplyToId, null);
  }

  public NoteDraft(
      String body, List<Image> images, Long quotedPostId, Long inReplyToId, Long quotedNoteId) {
    this(body, images, quotedPostId, inReplyToId, quotedNoteId, null, false);
  }

  public record Image(String key, String altText) {}
}
