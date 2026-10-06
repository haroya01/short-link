package com.example.short_link.note.domain.repository;

import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import java.util.Collection;
import java.util.Map;

public interface NoteRemoteReactionRepository {

  boolean add(Long noteId, Long remoteActorId, Kind kind, String activityId);

  void delete(Long noteId, Long remoteActorId, Kind kind);

  void deleteByActivity(Long remoteActorId, String activityId);

  long count(Long noteId, Kind kind);

  Map<Kind, Map<Long, Long>> counts(Collection<Long> noteIds);
}
