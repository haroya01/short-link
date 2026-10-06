package com.example.short_link.common.note;

import java.util.Collection;
import java.util.Map;

// The note slice implements this so collections can show note blocks without importing it.
public interface NoteBodyReader {

  Map<Long, NoteBlock> blocksByIds(Collection<Long> noteIds);

  boolean exists(Long noteId);
}
