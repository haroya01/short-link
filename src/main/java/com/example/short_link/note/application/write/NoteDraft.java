package com.example.short_link.note.application.write;

import java.util.List;

public record NoteDraft(
    String body, List<Image> images, Long quotedPostId, Long inReplyToId, Long quotedNoteId) {

  public NoteDraft(String body, List<Image> images, Long quotedPostId, Long inReplyToId) {
    this(body, images, quotedPostId, inReplyToId, null);
  }

  public record Image(String key, String altText) {}
}
