package com.example.short_link.note.domain.repository;

import java.util.Collection;
import java.util.Set;

public interface NoteAccessReader {

  Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds);

  Set<Long> readersAmong(Long noteId, Collection<Long> userIds);
}
