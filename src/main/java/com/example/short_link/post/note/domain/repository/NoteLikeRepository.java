package com.example.short_link.post.note.domain.repository;

import java.util.Collection;
import java.util.List;

public interface NoteLikeRepository {

  void addIfAbsent(Long noteId, Long userId);

  void delete(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  List<Long> likedNoteIds(Long userId, Collection<Long> noteIds);

  void deleteAllByNoteId(Long noteId);
}
