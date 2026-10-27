package com.example.short_link.federation.application;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// A note being sent looks up on their server the people it names that are not known here yet; a
// note being served reads only what is stored, so serving it never waits on another server.
@Component
@RequiredArgsConstructor
public class NoteAddressing {

  private final RemoteParents remoteParents;
  private final RemoteAccountFinder finder;

  public Addressing remoteNotes(NoteSnapshot note) {
    return remoteNotes(note.inReplyToId(), quotedElsewhere(note));
  }

  public Addressing remoteNotes(Long inReplyToId, Long quotedNoteId) {
    return new Addressing(remoteNote(inReplyToId), List.of(), remoteNote(quotedNoteId));
  }

  public List<RemoteActorEntity> lookedUp(List<String> handles) {
    return accounts(handles, finder::find);
  }

  public Addressing stored(NoteSnapshot note) {
    return remoteNotes(note).naming(accounts(Mentions.remote(note.body()), finder::known));
  }

  private RemoteParents.Parent remoteNote(Long noteId) {
    return noteId == null ? null : remoteParents.of(noteId).orElse(null);
  }

  private static List<RemoteActorEntity> accounts(
      List<String> handles, Function<String, Optional<RemoteActorEntity>> account) {
    List<RemoteActorEntity> named = new ArrayList<>();
    for (String handle : handles) {
      account.apply(handle).filter(person -> !person.onSuspendedServer()).ifPresent(named::add);
    }
    return named;
  }

  private static Long quotedElsewhere(NoteSnapshot note) {
    return note.quotedNote() != null && note.quotedNote().remote() && !note.visibility().direct()
        ? note.quotedNote().id()
        : null;
  }
}
