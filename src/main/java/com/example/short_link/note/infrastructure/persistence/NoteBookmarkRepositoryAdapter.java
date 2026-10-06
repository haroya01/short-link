package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteBookmarkEntity;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteBookmarkRepositoryAdapter implements NoteBookmarkRepository {

  private final JpaNoteBookmarkRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public void addIfAbsent(Long noteId, Long userId) {
    if (jpa.existsByNoteIdAndUserId(noteId, userId)) {
      return;
    }
    try {
      jpa.saveAndFlush(new NoteBookmarkEntity(noteId, userId));
    } catch (DataIntegrityViolationException alreadyBookmarked) {
      return;
    }
  }

  @Override
  public void delete(Long noteId, Long userId) {
    jpa.deleteByNoteIdAndUserId(noteId, userId);
  }

  @Override
  public List<Long> recentNoteIdsByUser(Long userId, int offset, int limit) {
    return em.createQuery(
            "select b.noteId from NoteBookmarkEntity b where b.userId = :userId order by b.id desc",
            Long.class)
        .setParameter("userId", userId)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }
}
