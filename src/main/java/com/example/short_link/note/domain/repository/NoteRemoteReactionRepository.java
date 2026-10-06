package com.example.short_link.note.domain.repository;

import com.example.short_link.common.note.RemoteNoteReactions.Kind;

public interface NoteRemoteReactionRepository {

  boolean add(Long noteId, Long remoteActorId, Kind kind, String activityId);

  void delete(Long noteId, Long remoteActorId, Kind kind);

  void deleteByActivity(Long remoteActorId, String activityId);
}
