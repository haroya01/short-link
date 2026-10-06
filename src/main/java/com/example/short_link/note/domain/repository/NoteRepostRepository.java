package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteRepostEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NoteRepostRepository {

  Optional<NoteRepostEntity> addIfAbsent(Long noteId, Long userId);

  Optional<NoteRepostEntity> delete(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  Map<Long, Long> counts(Collection<Long> noteIds);

  List<Long> repostedNoteIds(Long userId, Collection<Long> noteIds);

  List<Long> recentNoteIdsByUser(Long userId, int offset, int limit);
}
