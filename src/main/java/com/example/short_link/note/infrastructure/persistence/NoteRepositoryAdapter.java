package com.example.short_link.note.infrastructure.persistence;

import static com.example.short_link.note.infrastructure.persistence.NoteSql.viewer;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.note.domain.NoteEditEntity;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.repository.NoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteRepositoryAdapter implements NoteRepository {

  private final JpaNoteRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public NoteEntity save(NoteEntity note) {
    return jpa.save(note);
  }

  @Override
  public Optional<NoteEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<NoteEntity> findUnblocked(Long id, Long viewerId) {
    return em
        .createNativeQuery(
            "SELECT n.* FROM note n WHERE n.id = :id"
                + HeardSql.unblocked("n.user_id")
                + HeardSql.unblockedRemote("n.remote_actor_id"),
            NoteEntity.class)
        .setParameter("id", id)
        .setParameter("viewer", viewer(viewerId))
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<NoteEntity> findAllByIdIn(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllByIdIn(ids);
  }

  @Override
  public void delete(NoteEntity note) {
    jpa.delete(note);
  }

  @Override
  public void addRecipients(Long noteId, Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return;
    }
    StringBuilder sql =
        new StringBuilder("INSERT IGNORE INTO note_recipient (note_id, user_id) VALUES ");
    int i = 0;
    for (Long ignored : userIds) {
      sql.append(i == 0 ? "" : ", ").append("(:note, :user").append(i).append(')');
      i++;
    }
    var query = em.createNativeQuery(sql.toString()).setParameter("note", noteId);
    i = 0;
    for (Long userId : userIds) {
      query.setParameter("user" + i, userId);
      i++;
    }
    query.executeUpdate();
  }

  @Override
  public void applyReplyPolicy(Long rootId, NoteReplyPolicy policy) {
    em.createNativeQuery(
            "UPDATE note SET reply_policy = :policy WHERE id = :root OR conversation_id = :root")
        .setParameter("policy", policy.name())
        .setParameter("root", rootId)
        .executeUpdate();
  }

  @Override
  public void muteConversation(Long userId, Long conversationId, Instant at) {
    em.createNativeQuery(
            "INSERT IGNORE INTO note_conversation_mute (user_id, conversation_id, created_at)"
                + " VALUES (:user, :conversation, :at)")
        .setParameter("user", userId)
        .setParameter("conversation", conversationId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public void unmuteConversation(Long userId, Long conversationId) {
    em.createNativeQuery(
            "DELETE FROM note_conversation_mute WHERE user_id = :user"
                + " AND conversation_id = :conversation")
        .setParameter("user", userId)
        .setParameter("conversation", conversationId)
        .executeUpdate();
  }

  // INSERT IGNORE: the column's collation folds more than lower-casing does (accents, widths), so
  // two tags the extractor keeps apart can still be one key here.
  @Override
  public void tag(Long noteId, List<String> tags) {
    if (tags.isEmpty()) {
      return;
    }
    StringBuilder sql = new StringBuilder("INSERT IGNORE INTO note_tag (note_id, tag) VALUES ");
    for (int i = 0; i < tags.size(); i++) {
      sql.append(i == 0 ? "" : ", ").append("(:note, :tag").append(i).append(')');
    }
    var query = em.createNativeQuery(sql.toString()).setParameter("note", noteId);
    for (int i = 0; i < tags.size(); i++) {
      query.setParameter("tag" + i, tags.get(i));
    }
    query.executeUpdate();
  }

  @Override
  public void retag(Long noteId, List<String> tags) {
    em.createNativeQuery("DELETE FROM note_tag WHERE note_id = :note")
        .setParameter("note", noteId)
        .executeUpdate();
    tag(noteId, tags);
  }

  @Override
  public void recordVersion(Long noteId, NoteVersion version) {
    em.persist(new NoteEditEntity(noteId, version));
  }

  @Override
  public List<NoteVersion> versions(Long noteId) {
    return em
        .createQuery(
            "select e from NoteEditEntity e where e.noteId = :note order by e.id desc",
            NoteEditEntity.class)
        .setParameter("note", noteId)
        .getResultList()
        .stream()
        .map(NoteEditEntity::version)
        .toList();
  }

  @Override
  public long countPinned(Long userId) {
    return em.createQuery(
            "select count(n) from NoteEntity n where n.userId = :userId and n.pinnedAt is not null",
            Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
  }

  @Override
  public List<Long> pinnedIds(Long userId) {
    return em.createQuery(
            "select n.id from NoteEntity n where n.userId = :userId and n.pinnedAt is not null"
                + " and n.visibility in :shareable order by n.pinnedAt desc",
            Long.class)
        .setParameter("userId", userId)
        .setParameter("shareable", NoteVisibility.where(NoteVisibility::shareable))
        .getResultList();
  }

  @Override
  public long countForFollowersByAuthor(Long userId) {
    return jpa.countByUserIdAndVisibilityIn(
        userId, NoteVisibility.where(NoteVisibility::followersRead));
  }
}
