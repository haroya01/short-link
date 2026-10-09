package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.note.domain.NoteRepostEntity;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
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
  @SuppressWarnings("unchecked")
  public List<Long> recentNoteIdsByUser(Long userId, Long viewerId, int offset, int limit) {
    List<Number> ids =
        em.createNativeQuery(
                "SELECT r.note_id FROM note_repost r JOIN note n ON n.id = r.note_id"
                    + " WHERE r.user_id = :userId"
                    + HeardSql.unblocked("r.user_id")
                    + HeardSql.unblocked("n.user_id")
                    + " AND (n.user_id = r.user_id OR NOT "
                    + HeardSql.muted("n.user_id")
                    + ")"
                    + " ORDER BY r.id DESC")
            .setParameter("userId", userId)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", Instant.now())
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    return ids.stream().map(Number::longValue).toList();
  }
}
