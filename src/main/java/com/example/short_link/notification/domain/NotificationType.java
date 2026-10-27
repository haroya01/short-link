package com.example.short_link.notification.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

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
  FOLLOW_REQUEST,
  POST_QUOTE,
  NOTE_EMBED,
  COMMENT_LIKE,
  HIGHLIGHT,
  POST_FIRST_READER,
  POST_READ_MILESTONE;

  public boolean grouped() {
    return switch (this) {
      case LIKE, COMMENT_LIKE, HIGHLIGHT, NOTE_EMBED, NOTE_LIKE, NOTE_REPOST -> true;
      default -> false;
    };
  }

  public boolean inMentions() {
    return switch (this) {
      case MENTION, NOTE_MENTION, REPLY, NOTE_REPLY, COMMENT -> true;
      default -> false;
    };
  }

  // Which words of a note notice someone else wrote, for the recipient's keyword filters: the reply
  // or quote that caused it, the note itself, or none (the note is the recipient's own).
  public KeywordText keywordText() {
    return switch (this) {
      case NOTE_REPLY, NOTE_QUOTE -> KeywordText.SOURCE_EXCERPT;
      case NOTE_MENTION, POST_QUOTE, NOTE_POST, NOTE_EDIT, NOTE_POLL -> KeywordText.EXCERPT;
      default -> KeywordText.NONE;
    };
  }

  public enum KeywordText {
    SOURCE_EXCERPT,
    EXCERPT,
    NONE
  }

  // Whether the post or note the push opens is the actor's rather than the recipient's, when the
  // payload names no owner of its own.
  public boolean opensTheActorsWork() {
    return switch (this) {
      case NEW_POST, NOTE_POLL, NOTE_POST, NOTE_EDIT, POST_QUOTE -> true;
      default -> false;
    };
  }

  // A mention the notification policy may treat as a private mention, judged by the mentioning
  // note's visibility.
  public boolean mayBePrivateMention() {
    return this == NOTE_MENTION;
  }

  public static Set<NotificationType> mentionTypes() {
    return EnumSet.copyOf(Arrays.stream(values()).filter(NotificationType::inMentions).toList());
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
          FOLLOW_REQUEST,
          POST_QUOTE,
          NOTE_EMBED,
          COMMENT_LIKE,
          HIGHLIGHT ->
          true;
      case NEW_POST,
          CONNECTED,
          PATH_GREW,
          NOTE_POLL,
          NOTE_POST,
          NOTE_EDIT,
          POST_FIRST_READER,
          POST_READ_MILESTONE ->
          false;
    };
  }
}
