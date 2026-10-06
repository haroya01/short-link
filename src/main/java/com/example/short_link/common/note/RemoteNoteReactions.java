package com.example.short_link.common.note;

// The note slice implements this so federation can record likes and boosts from other servers
// without importing that slice. Repeats are idempotent: one row per note, remote actor and kind.
public interface RemoteNoteReactions {

  enum Kind {
    LIKE,
    ANNOUNCE
  }

  void add(Long noteId, Long remoteActorId, Kind kind, String activityId);

  void remove(Long noteId, Long remoteActorId, Kind kind);

  void removeByActivity(Long remoteActorId, String activityId);
}
