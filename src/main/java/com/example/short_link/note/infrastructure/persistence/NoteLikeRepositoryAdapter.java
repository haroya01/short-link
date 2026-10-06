package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteLikeEntity;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteLikeRepositoryAdapter implements NoteLikeRepository {

  private final JpaNoteLikeRepository jpa;

  @Override
  public void addIfAbsent(Long noteId, Long userId) {
    if (jpa.existsByNoteIdAndUserId(noteId, userId)) {
      return;
    }
    try {
      jpa.saveAndFlush(new NoteLikeEntity(noteId, userId));
    } catch (DataIntegrityViolationException alreadyLiked) {
      // a concurrent request stored the same like first
    }
  }

  @Override
  public void delete(Long noteId, Long userId) {
    jpa.findByNoteIdAndUserId(noteId, userId).ifPresent(jpa::delete);
  }

  @Override
  public long countByNoteId(Long noteId) {
    return jpa.countByNoteId(noteId);
  }

  @Override
  public Map<Long, Long> counts(Collection<Long> noteIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (noteIds.isEmpty()) {
      return counts;
    }
    for (Object[] row : jpa.counts(noteIds)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  @Override
  public List<Long> likedNoteIds(Long userId, Collection<Long> noteIds) {
    return userId == null || noteIds.isEmpty() ? List.of() : jpa.likedNoteIds(userId, noteIds);
  }

  @Override
  public void deleteAllByNoteId(Long noteId) {
    jpa.deleteAllByNoteId(noteId);
  }
}
