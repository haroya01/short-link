package com.example.short_link.note.application.access;

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
      if (note.getVisibility().readableBy(note.isOwnedBy(viewerId), false, () -> false)) {
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

  public boolean readable(NoteEntity note, Long viewerId) {
    return readable(List.of(note), viewerId).contains(note.getId());
  }
}
