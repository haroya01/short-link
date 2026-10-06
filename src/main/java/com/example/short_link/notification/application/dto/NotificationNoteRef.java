package com.example.short_link.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Excerpts are write-time snapshots. sourceNoteId is the reply or quote that caused the notice.
public record NotificationNoteRef(
    Long noteId,
    String excerpt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long sourceNoteId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String sourceExcerpt)
    implements NotificationTarget {

  @Override
  public String pushSubtitle() {
    return sourceExcerpt != null ? sourceExcerpt : excerpt;
  }
}
