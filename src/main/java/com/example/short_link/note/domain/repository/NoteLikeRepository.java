package com.example.short_link.note.domain.repository;

import java.util.Collection;
import java.util.List;

public interface NoteLikeRepository {

  boolean addIfAbsent(Long noteId, Long userId);

  void delete(Long noteId, Long userId);

  List<Long> likedNoteIds(Long userId, Collection<Long> noteIds);

  void deleteAllByNoteId(Long noteId);
}
