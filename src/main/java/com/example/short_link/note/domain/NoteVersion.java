package com.example.short_link.note.domain;

import java.time.Instant;

public record NoteVersion(String body, String contentWarning, boolean sensitive, Instant at) {

  public static NoteVersion of(NoteEntity note) {
    return new NoteVersion(
        note.getBody(),
        note.getContentWarning(),
        note.isSensitive(),
        note.getEditedAt() == null ? note.getCreatedAt() : note.getEditedAt());
  }
}
