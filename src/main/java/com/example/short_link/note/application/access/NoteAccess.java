package com.example.short_link.note.application.access;

import com.example.short_link.common.note.NoteVisibility.Reader;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Who may read a note in hand; NoteAccessSql.readable answers the same for lists. A batch costs at
// most one lookup, and none when the visibility or authorship already settles every note.
@Component
@RequiredArgsConstructor
public class NoteAccess {

  private final NoteRepository notes;

  public Set<Long> readable(Collection<NoteEntity> candidates, Long viewerId) {
    Set<Long> readable = new HashSet<>();
    Set<Long> unsettled = new HashSet<>();
    for (NoteEntity note : candidates) {
      boolean owner = note.isOwnedBy(viewerId);
      if (note.getVisibility().readableBy(reader -> settledInHand(reader, owner))) {
        readable.add(note.getId());
      } else {
        unsettled.add(note.getId());
      }
    }
    if (!unsettled.isEmpty()) {
      readable.addAll(notes.visibleTo(viewerId, unsettled));
    }
    return readable;
  }

  private static boolean settledInHand(Reader reader, boolean owner) {
    return switch (reader) {
      case ANYONE -> true;
      case AUTHOR -> owner;
      case NAMED, FOLLOWER, MEMBER -> false;
    };
  }

  public Set<Long> readersAmong(NoteEntity note, Collection<Long> userIds) {
    if (userIds.isEmpty() || note.getVisibility().shareable()) {
      return Set.copyOf(userIds);
    }
    return notes.readersAmong(note.getId(), userIds);
  }

  public boolean readable(NoteEntity note, Long viewerId) {
    return readable(List.of(note), viewerId).contains(note.getId());
  }
}
