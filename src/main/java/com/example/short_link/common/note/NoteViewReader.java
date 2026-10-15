package com.example.short_link.common.note;

import java.util.Optional;

// The note slice implements this so federation can answer with a note exactly as the note lists
// render it to that viewer, without importing that slice.
public interface NoteViewReader {

  Optional<Object> view(Long noteId, Long viewerId);
}
