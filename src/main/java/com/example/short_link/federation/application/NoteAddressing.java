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

  public Addressing lookedUp(NoteSnapshot note) {
    return lookedUp(note.inReplyToId(), Mentions.remote(note.body()), quotedElsewhere(note));
  }

  public Addressing lookedUp(Long inReplyToId, List<String> handles, Long quotedNoteId) {
    return addressing(inReplyToId, handles, finder::find, quotedNoteId);
  }

  public Addressing stored(NoteSnapshot note) {
    return addressing(
        note.inReplyToId(), Mentions.remote(note.body()), finder::known, quotedElsewhere(note));
  }

  private Addressing addressing(
      Long inReplyToId,
      List<String> handles,
      Function<String, Optional<RemoteActorEntity>> account,
      Long quotedNoteId) {
    RemoteParents.Parent parent =
        inReplyToId == null ? null : remoteParents.of(inReplyToId).orElse(null);
    List<RemoteActorEntity> named = new ArrayList<>();
    for (String handle : handles) {
      account.apply(handle).filter(person -> !person.onSuspendedServer()).ifPresent(named::add);
    }
    RemoteParents.Parent quoted =
        quotedNoteId == null ? null : remoteParents.of(quotedNoteId).orElse(null);
    return new Addressing(parent, named, quoted);
  }

  private static Long quotedElsewhere(NoteSnapshot note) {
    return note.quotedNote() != null && note.quotedNote().remote() && !note.visibility().direct()
        ? note.quotedNote().id()
        : null;
  }
}
