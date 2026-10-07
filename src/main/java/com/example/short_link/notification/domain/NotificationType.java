package com.example.short_link.notification.domain;

// REPLY targets the thread author, distinct from the post-owner COMMENT notice. MENTION is
// deduplicated against COMMENT/REPLY. CONNECTED targets the connected work's author; PATH_GREW
// targets prior contributors excluding that author and the curator. Both graph notices open the
// collection.
public enum NotificationType {
  LIKE,
  COMMENT,
  FOLLOW,
  SERIES_SUBSCRIBE,
  REPLY,
  NEW_POST,
  MENTION,
  CONNECTED,
  PATH_GREW,
  NOTE_LIKE,
  NOTE_REPOST,
  NOTE_REPLY,
  NOTE_QUOTE,
  REMOTE_FOLLOW,
  NOTE_MENTION,
  NOTE_POLL,
  NOTE_POST,
  NOTE_EDIT,
  FOLLOW_REQUEST;

  public boolean grouped() {
    return this == NOTE_LIKE || this == NOTE_REPOST;
  }

  // Notices a person causes, which the notification policy may keep aside or drop (Mastodon's
  // filterable types). Ones the member subscribed to (new posts and notes, polls they voted in,
  // edits of notes they shared) and the reading graph's notices always arrive.
  public boolean filterable() {
    return switch (this) {
      case LIKE,
              COMMENT,
              FOLLOW,
              SERIES_SUBSCRIBE,
              REPLY,
              MENTION,
              NOTE_LIKE,
              NOTE_REPOST,
              NOTE_REPLY,
              NOTE_QUOTE,
              REMOTE_FOLLOW,
              NOTE_MENTION,
              FOLLOW_REQUEST ->
          true;
      case NEW_POST, CONNECTED, PATH_GREW, NOTE_POLL, NOTE_POST, NOTE_EDIT -> false;
    };
  }
}
