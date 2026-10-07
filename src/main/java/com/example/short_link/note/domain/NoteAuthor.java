package com.example.short_link.note.domain;

public record NoteAuthor(Long id, String username, String avatarUrl, String displayName) {

  public NoteAuthor(Long id, String username, String avatarUrl) {
    this(id, username, avatarUrl, null);
  }
}
