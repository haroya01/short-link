package com.example.short_link.common.note;

import java.util.List;
import java.util.Optional;

// The note slice implements this so federation can answer with a note exactly as the note lists
// render it to that viewer, without importing that slice.
public interface NoteViewReader {

  Optional<Object> view(Long noteId, Long viewerId);

  record Listed(Long id, Object view) {}

  List<Listed> received(Long viewerId, Long beforeId, int limit);

  List<Listed> latest(Long viewerId, Long beforeId, int limit);
}
