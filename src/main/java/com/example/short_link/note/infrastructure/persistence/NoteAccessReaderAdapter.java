package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.note.domain.repository.NoteAccessReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class NoteAccessReaderAdapter implements NoteAccessReader {

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds) {
    if (restrictedIds.isEmpty() || viewerId == null) {
      return Set.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.id FROM note n WHERE n.id IN (:ids) AND " + NoteAccessSql.readable("n"))
            .setParameter("ids", restrictedIds)
            .setParameter("viewer", viewerId)
            .getResultList();
    Set<Long> visible = new HashSet<>();
    for (Object id : rows) {
      visible.add(((Number) id).longValue());
    }
    return visible;
  }

  @Override
  public Set<Long> readersAmong(Long noteId, Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return Set.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT u.id FROM users u JOIN note n ON n.id = :note WHERE u.id IN (:users) AND "
                    + NoteAccessSql.readable("n", "u.id"))
            .setParameter("note", noteId)
            .setParameter("users", userIds)
            .getResultList();
    Set<Long> readers = new HashSet<>();
    for (Object id : rows) {
      readers.add(((Number) id).longValue());
    }
    return readers;
  }
}
