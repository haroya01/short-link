package com.example.short_link.note.domain;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.List;

// An account on another server keeps the id space apart with a negative id, so clients that key
// authors by id never mistake it for a member; remoteId and url say where it lives.
public record NoteAuthor(
    Long id,
    String username,
    String avatarUrl,
    String displayName,
    Long remoteId,
    String url,
    List<CustomEmoji> emojis) {

  public NoteAuthor {
    emojis = emojis == null ? List.of() : List.copyOf(emojis);
  }

  public NoteAuthor(Long id, String username, String avatarUrl) {
    this(id, username, avatarUrl, null, null, null, List.of());
  }

  public NoteAuthor(Long id, String username, String avatarUrl, String displayName) {
    this(id, username, avatarUrl, displayName, null, null, List.of());
  }

  public static NoteAuthor remote(
      Long remoteId, String acct, String avatarUrl, String displayName, String url) {
    return remote(remoteId, acct, avatarUrl, displayName, url, List.of());
  }

  public static NoteAuthor remote(
      Long remoteId,
      String acct,
      String avatarUrl,
      String displayName,
      String url,
      List<CustomEmoji> emojis) {
    return new NoteAuthor(-remoteId, acct, avatarUrl, displayName, remoteId, url, emojis);
  }
}
