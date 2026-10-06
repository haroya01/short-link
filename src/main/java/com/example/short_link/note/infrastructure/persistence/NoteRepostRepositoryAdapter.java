package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteRepostEntity;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteRepostRepositoryAdapter implements NoteRepostRepository {

  private final JpaNoteRepostRepository jpa;

  @PersistenceContext private EntityManager em;

  // Empty when the repost already existed, so only a real change goes out to other servers.
  @Override
  public Optional<NoteRepostEntity> addIfAbsent(Long noteId, Long userId) {
    if (jpa.findByNoteIdAndUserId(noteId, userId).isPresent()) {
      return Optional.empty();
    }
    try {
      return Optional.of(jpa.saveAndFlush(new NoteRepostEntity(noteId, userId)));
    } catch (DataIntegrityViolationException alreadyReposted) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<NoteRepostEntity> delete(Long noteId, Long userId) {
    Optional<NoteRepostEntity> found = jpa.findByNoteIdAndUserId(noteId, userId);
    found.ifPresent(jpa::delete);
    return found;
  }

  @Override
  public List<Long> recentNoteIdsByUser(Long userId, int offset, int limit) {
    return em.createQuery(
            "select r.noteId from NoteRepostEntity r where r.userId = :userId"
                + " order by r.id desc",
            Long.class)
        .setParameter("userId", userId)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }
}
