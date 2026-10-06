package com.example.short_link.note.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NoteLikeRepository {

  void addIfAbsent(Long noteId, Long userId);

  void delete(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  Map<Long, Long> counts(Collection<Long> noteIds);

  List<Long> likedNoteIds(Long userId, Collection<Long> noteIds);

  void deleteAllByNoteId(Long noteId);
}
