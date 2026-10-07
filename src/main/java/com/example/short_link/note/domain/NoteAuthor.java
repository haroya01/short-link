package com.example.short_link.note.domain;

// An account on another server keeps the id space apart with a negative id, so clients that key
// authors by id never mistake it for a member; remoteId and url say where it lives.
public record NoteAuthor(
    Long id, String username, String avatarUrl, String displayName, Long remoteId, String url) {

  public NoteAuthor(Long id, String username, String avatarUrl) {
    this(id, username, avatarUrl, null, null, null);
  }

  public NoteAuthor(Long id, String username, String avatarUrl, String displayName) {
    this(id, username, avatarUrl, displayName, null, null);
  }

  public static NoteAuthor remote(
      Long remoteId, String acct, String avatarUrl, String displayName, String url) {
    return new NoteAuthor(-remoteId, acct, avatarUrl, displayName, remoteId, url);
  }
}
