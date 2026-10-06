package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteLikeEntity;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteLikeRepositoryAdapter implements NoteLikeRepository {

  private final JpaNoteLikeRepository jpa;

  @Override
  public boolean addIfAbsent(Long noteId, Long userId) {
    if (jpa.existsByNoteIdAndUserId(noteId, userId)) {
      return false;
    }
    try {
      jpa.saveAndFlush(new NoteLikeEntity(noteId, userId));
      return true;
    } catch (DataIntegrityViolationException alreadyLiked) {
      return false;
    }
  }

  @Override
  public void delete(Long noteId, Long userId) {
    jpa.findByNoteIdAndUserId(noteId, userId).ifPresent(jpa::delete);
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
