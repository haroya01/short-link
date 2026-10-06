package com.example.short_link.note.application.write;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.note.RemoteNoteReactions;
import com.example.short_link.note.domain.repository.NoteRemoteReactionRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class RemoteReactionRecorder implements RemoteNoteReactions {

  private final NoteRemoteReactionRepository reactions;
  private final NoteRepository notes;
  private final ApplicationEventPublisher events;

  @Override
  @Transactional
  public void add(Long noteId, Long remoteActorId, Kind kind, String activityId) {
    if (!reactions.add(noteId, remoteActorId, kind, activityId)) {
      return;
    }
    notes
        .findById(noteId)
        .ifPresent(
            note ->
                events.publishEvent(
                    new NoteInteractionEvent(
                        kind == Kind.LIKE
                            ? NoteInteractionEvent.Type.LIKE
                            : NoteInteractionEvent.Type.REPOST,
                        note.getUserId(),
                        null,
                        remoteActorId,
                        note.getId(),
                        note.excerpt(),
                        null,
                        null)));
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
