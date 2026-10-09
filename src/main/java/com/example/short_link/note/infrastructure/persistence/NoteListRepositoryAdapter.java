package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteListEntity;
import com.example.short_link.note.domain.NoteListSummary;
import com.example.short_link.note.domain.repository.NoteListRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class NoteListRepositoryAdapter implements NoteListRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public List<NoteListSummary> summaries(Long userId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT l.id, l.title, COUNT(m.member_id) FROM note_list l"
                    + " LEFT JOIN note_list_member m ON m.list_id = l.id"
                    + " WHERE l.user_id = :user GROUP BY l.id, l.title ORDER BY l.id")
            .setParameter("user", userId)
            .getResultList();
    List<NoteListSummary> summaries = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      summaries.add(
          new NoteListSummary(
              ((Number) cols[0]).longValue(), (String) cols[1], ((Number) cols[2]).longValue()));
    }
    return summaries;
  }

  @Override
  public Optional<NoteListEntity> owned(Long listId, Long userId) {
    return em.createQuery(
            "select l from NoteListEntity l where l.id = :id and l.userId = :user",
            NoteListEntity.class)
        .setParameter("id", listId)
        .setParameter("user", userId)
        .getResultStream()
        .findFirst();
  }

  @Override
  public long countLists(Long userId) {
    return em.createQuery(
            "select count(l) from NoteListEntity l where l.userId = :user", Long.class)
        .setParameter("user", userId)
        .getSingleResult();
  }

  @Override
  public NoteListEntity save(NoteListEntity list) {
    em.persist(list);
    return list;
  }

  @Override
  public void delete(NoteListEntity list) {
    em.remove(list);
  }

  @Override
  public List<Long> memberIds(Long listId) {
    return em
        .createNativeQuery(
            "SELECT member_id FROM note_list_member WHERE list_id = :list ORDER BY created_at DESC")
        .setParameter("list", listId)
        .getResultList()
        .stream()
        .map(id -> ((Number) id).longValue())
        .toList();
  }

  @Override
  public long countMembers(Long listId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM note_list_member WHERE list_id = :list")
                .setParameter("list", listId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public boolean addMember(Long listId, Long memberId) {
    return em.createNativeQuery(
                "INSERT IGNORE INTO note_list_member (list_id, member_id, created_at)"
                    + " VALUES (:list, :member, NOW(6))")
            .setParameter("list", listId)
            .setParameter("member", memberId)
            .executeUpdate()
        > 0;
  }

  @Override
  public void removeMember(Long listId, Long memberId) {
    em.createNativeQuery(
            "DELETE FROM note_list_member WHERE list_id = :list AND member_id = :member")
        .setParameter("list", listId)
        .setParameter("member", memberId)
        .executeUpdate();
  }

  @Override
  public List<Long> listsContaining(Long userId, Long memberId) {
    return em
        .createNativeQuery(
            "SELECT l.id FROM note_list l JOIN note_list_member m ON m.list_id = l.id"
                + " WHERE l.user_id = :user AND m.member_id = :member ORDER BY l.id")
        .setParameter("user", userId)
        .setParameter("member", memberId)
        .getResultList()
        .stream()
        .map(id -> ((Number) id).longValue())
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> feed(Long listId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n JOIN note_list_member m"
                + " ON m.member_id = n.user_id AND m.list_id = :list"
                + " WHERE "
                + NoteRepositoryAdapter.notReply("n")
                + " AND (n.visibility IN ('PUBLIC', 'UNLISTED')"
                + " OR (n.visibility = 'PRIVATE' AND EXISTS (SELECT 1 FROM user_follow f"
                + " WHERE f.follower_id = :viewer AND f.following_id = n.user_id))"
                + " OR EXISTS (SELECT 1 FROM note_recipient r"
                + " WHERE r.note_id = n.id AND r.user_id = :viewer))"
                + NoteRepositoryAdapter.heard("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("list", listId)
        .setParameter("viewer", viewerId)
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }
}
