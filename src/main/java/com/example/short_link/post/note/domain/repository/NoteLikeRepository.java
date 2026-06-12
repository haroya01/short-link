package com.example.short_link.post.note.domain.repository;

import com.example.short_link.post.note.domain.NoteLikeEntity;
import java.util.Collection;
import java.util.List;

public interface NoteLikeRepository {

  boolean exists(Long noteId, Long userId);

  void save(NoteLikeEntity like);

  void delete(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  List<Long> likedNoteIds(Long userId, Collection<Long> noteIds);

  void deleteAllByNoteId(Long noteId);
}
