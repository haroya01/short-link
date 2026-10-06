package com.example.short_link.note.application.write;

import com.example.short_link.common.note.RemoteNoteReactions;
import com.example.short_link.note.domain.repository.NoteRemoteReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RemoteReactionRecorder implements RemoteNoteReactions {

  private final NoteRemoteReactionRepository reactions;

  @Override
  public void add(Long noteId, Long remoteActorId, Kind kind, String activityId) {
    reactions.put(noteId, remoteActorId, kind, activityId);
  }

  @Override
  public void remove(Long noteId, Long remoteActorId, Kind kind) {
    reactions.delete(noteId, remoteActorId, kind);
  }

  @Override
  public void removeByActivity(Long remoteActorId, String activityId) {
    reactions.deleteByActivity(remoteActorId, activityId);
  }
}
