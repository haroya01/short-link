package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEditEntity;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.repository.NoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteRepositoryAdapter implements NoteRepository {

  private static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private static final List<NoteVisibility> SHAREABLE =
      List.of(NoteVisibility.PUBLIC, NoteVisibility.UNLISTED);

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
  public List<NoteEntity> findAllByIdIn(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllByIdIn(ids);
  }

  @Override
  public void delete(NoteEntity note) {
    jpa.delete(note);
  }

  @Override
  public List<NoteEntity> topLevel(int offset, int limit) {
    return em.createQuery(
            "select n from NoteEntity n where n.inReplyToId is null and n.visibility = :public"
                + " order by n.id desc",
            NoteEntity.class)
        .setParameter("public", NoteVisibility.PUBLIC)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // A profile shows what this viewer may read: public and unlisted to anyone, followers-only to
  // followers and mentioned members, and direct notes to no one but their author.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.user_id = :author AND n.in_reply_to_id IS NULL"
                + " AND (n.user_id = :viewer OR n.visibility IN ('PUBLIC', 'UNLISTED')"
                + " OR (n.visibility = 'PRIVATE' AND ("
                + "EXISTS (SELECT 1 FROM user_follow f"
                + " WHERE f.follower_id = :viewer AND f.following_id = n.user_id)"
                + " OR EXISTS (SELECT 1 FROM note_recipient r"
                + " WHERE r.note_id = n.id AND r.user_id = :viewer))))"
                + " ORDER BY n.pinned_at IS NULL, n.pinned_at DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setParameter("viewer", viewerId == null ? -1L : viewerId)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Of the given notes, those this viewer may read. Only restricted notes cost a lookup, so a page
  // of public notes asks nothing.
  @Override
  @SuppressWarnings("unchecked")
  public Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds) {
    if (restrictedIds.isEmpty() || viewerId == null) {
      return Set.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.id FROM note n WHERE n.id IN (:ids) AND (n.user_id = :viewer"
                    + " OR EXISTS (SELECT 1 FROM note_recipient r"
                    + " WHERE r.note_id = n.id AND r.user_id = :viewer)"
                    + " OR (n.visibility = 'PRIVATE' AND EXISTS (SELECT 1 FROM user_follow f"
                    + " WHERE f.follower_id = :viewer AND f.following_id = n.user_id)))")
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

  // Private mentions (Mastodon's direct column): direct notes the viewer wrote or was named in,
  // replies included, newest first.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> direct(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.visibility = 'DIRECT' AND (n.user_id = :viewer"
                + " OR EXISTS (SELECT 1 FROM note_recipient r"
                + " WHERE r.note_id = n.id AND r.user_id = :viewer))"
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("viewer", viewerId)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Top-level notes written in the window, by reactions from people other than the author: likes,
  // reposts, replies and likes or boosts from other servers. Ties fall back to newest first.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> trending(int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n"
                + " WHERE n.in_reply_to_id IS NULL AND n.visibility = 'PUBLIC'"
                + " AND n.created_at >= :since"
                + " ORDER BY ("
                + "(SELECT COUNT(*) FROM note_like l WHERE l.note_id = n.id"
                + " AND l.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note_repost r WHERE r.note_id = n.id"
                + " AND r.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note c WHERE c.in_reply_to_id = n.id"
                + " AND c.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note_remote_reaction x WHERE x.note_id = n.id)"
                + ") DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("since", Instant.now().minus(TRENDING_WINDOW))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Top-level notes by the authors and their reposts, one row per note at its newest activity; the
  // original wins a tie with a repost. A repost of someone the viewer blocked, or who blocked the
  // viewer, is left out, as are reposts by people whose reposts the viewer hid and every repost
  // when the viewer turned reposts off. Top-level public notes carrying a tag the viewer follows
  // (the blog's tag follows) join in as originals, again never from someone on either side of a
  // block. A followed author's direct note shows only to its recipients, and direct notes naming
  // the viewer join from anyone, as Mastodon's home timeline carries them.
  @Override
  public List<NoteFeedRow> following(
      Collection<Long> authorIds, Long viewerId, int offset, int limit) {
    if (authorIds.isEmpty()) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.*, x.reposter_id FROM ("
                    + "SELECT y.note_id, y.at, y.reposter_id FROM ("
                    + "SELECT t.note_id, t.at, t.reposter_id, ROW_NUMBER() OVER ("
                    + "PARTITION BY t.note_id ORDER BY t.at DESC, t.reposter_id IS NULL DESC"
                    + ") AS position FROM ("
                    + "SELECT o.id AS note_id, o.created_at AS at, NULL AS reposter_id FROM note o"
                    + " WHERE o.user_id IN (:authors) AND o.in_reply_to_id IS NULL"
                    + " AND (o.visibility <> 'DIRECT' OR o.user_id = :viewer"
                    + " OR EXISTS (SELECT 1 FROM note_recipient dr"
                    + " WHERE dr.note_id = o.id AND dr.user_id = :viewer))"
                    + " UNION ALL"
                    + " SELECT d.id, d.created_at, NULL FROM note_recipient dn"
                    + " JOIN note d ON d.id = dn.note_id"
                    + " WHERE dn.user_id = :viewer AND d.visibility = 'DIRECT'"
                    + " AND d.in_reply_to_id IS NULL"
                    + " UNION ALL"
                    + " SELECT r.note_id, r.created_at, r.user_id FROM note_repost r"
                    + " JOIN note s ON s.id = r.note_id"
                    + " WHERE r.user_id IN (:authors) AND s.visibility IN ('PUBLIC', 'UNLISTED')"
                    + " AND NOT EXISTS ("
                    + "SELECT 1 FROM user_block b"
                    + " WHERE (b.blocker_id = :viewer AND b.blocked_id = s.user_id)"
                    + " OR (b.blocker_id = s.user_id AND b.blocked_id = :viewer))"
                    + " AND NOT EXISTS (SELECT 1 FROM note_repost_mute m"
                    + " WHERE m.user_id = :viewer AND m.muted_user_id = r.user_id)"
                    + " AND NOT EXISTS (SELECT 1 FROM note_feed_preference p"
                    + " WHERE p.user_id = :viewer AND p.show_reposts = FALSE)"
                    + " UNION ALL"
                    + " SELECT g.note_id, gn.created_at, NULL FROM user_tag_pref f"
                    + " JOIN note_tag g ON g.tag = f.tag"
                    + " JOIN note gn ON gn.id = g.note_id"
                    + " WHERE f.user_id = :viewer AND f.kind = 'FOLLOW' AND gn.in_reply_to_id IS NULL"
                    + " AND gn.visibility = 'PUBLIC'"
                    + " AND NOT EXISTS (SELECT 1 FROM user_block b"
                    + " WHERE (b.blocker_id = :viewer AND b.blocked_id = gn.user_id)"
                    + " OR (b.blocker_id = gn.user_id AND b.blocked_id = :viewer))"
                    + ") t) y WHERE y.position = 1"
                    + ") x JOIN note n ON n.id = x.note_id"
                    + " ORDER BY x.at DESC, x.note_id DESC LIMIT :limit OFFSET :offset",
                NoteEntity.FEED_MAPPING)
            .setParameter("authors", authorIds)
            .setParameter("viewer", viewerId)
            .setParameter("limit", limit)
            .setParameter("offset", offset)
            .getResultList();
    List<NoteFeedRow> feed = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      feed.add(new NoteFeedRow((NoteEntity) cols[0], (Long) cols[1]));
    }
    return feed;
  }

  @Override
  public List<NoteEntity> replies(Long noteId, int limit) {
    return em.createQuery(
            "select n from NoteEntity n where n.inReplyToId = :noteId order by n.id asc",
            NoteEntity.class)
        .setParameter("noteId", noteId)
        .setMaxResults(limit)
        .getResultList();
  }

  // Replies, likes, reposts and quotes of a page in one statement; likes and boosts from other
  // servers add to likes and reposts.
  @Override
  public Map<Long, NoteStats> stats(Collection<Long> noteIds) {
    Map<Long, NoteStats> stats = new HashMap<>();
    if (noteIds.isEmpty()) {
      return stats;
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT s.note_id, s.kind, COUNT(*) FROM ("
                    + "SELECT in_reply_to_id AS note_id, 'REPLY' AS kind FROM note"
                    + " WHERE in_reply_to_id IN (:ids)"
                    + " UNION ALL SELECT quoted_note_id, 'QUOTE' FROM note"
                    + " WHERE quoted_note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'LIKE' FROM note_like WHERE note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'REPOST' FROM note_repost WHERE note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, IF(kind = 'LIKE', 'LIKE', 'REPOST')"
                    + " FROM note_remote_reaction WHERE note_id IN (:ids)"
                    + ") s GROUP BY s.note_id, s.kind")
            .setParameter("ids", noteIds)
            .getResultList();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      Long noteId = ((Number) cols[0]).longValue();
      long count = ((Number) cols[2]).longValue();
      NoteStats current = stats.getOrDefault(noteId, NoteStats.NONE);
      stats.put(
          noteId,
          switch (cols[1].toString()) {
            case "REPLY" ->
                new NoteStats(count, current.likes(), current.reposts(), current.quotes());
            case "LIKE" ->
                new NoteStats(current.replies(), count, current.reposts(), current.quotes());
            case "QUOTE" ->
                new NoteStats(current.replies(), current.likes(), current.reposts(), count);
            default -> new NoteStats(current.replies(), current.likes(), count, current.quotes());
          });
    }
    return stats;
  }

  @Override
  public NoteViewerMarks viewerMarks(Long userId, Collection<Long> noteIds) {
    if (userId == null || noteIds.isEmpty()) {
      return NoteViewerMarks.NONE;
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT note_id, 'LIKE' FROM note_like WHERE user_id = :user AND note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'REPOST' FROM note_repost"
                    + " WHERE user_id = :user AND note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'BOOKMARK' FROM note_bookmark"
                    + " WHERE user_id = :user AND note_id IN (:ids)")
            .setParameter("user", userId)
            .setParameter("ids", noteIds)
            .getResultList();
    Set<Long> liked = new HashSet<>();
    Set<Long> reposted = new HashSet<>();
    Set<Long> bookmarked = new HashSet<>();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      Long noteId = ((Number) cols[0]).longValue();
      switch (cols[1].toString()) {
        case "LIKE" -> liked.add(noteId);
        case "REPOST" -> reposted.add(noteId);
        default -> bookmarked.add(noteId);
      }
    }
    return new NoteViewerMarks(liked, reposted, bookmarked);
  }

  @Override
  public List<NoteEntity> quotesOf(Long noteId, int offset, int limit) {
    return em.createQuery(
            "select n from NoteEntity n where n.quotedNoteId = :noteId"
                + " and n.visibility in :shareable order by n.id desc",
            NoteEntity.class)
        .setParameter("noteId", noteId)
        .setParameter("shareable", SHAREABLE)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> tagged(String tag, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note_tag g JOIN note n ON n.id = g.note_id"
                + " WHERE g.tag = :tag AND n.visibility = 'PUBLIC'"
                + " ORDER BY g.note_id DESC LIMIT :limit OFFSET :offset",
            NoteEntity.class)
        .setParameter("tag", tag)
        .setParameter("limit", limit)
        .setParameter("offset", offset)
        .getResultList();
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
        .setParameter("shareable", SHAREABLE)
        .getResultList();
  }

  @Override
  public long countByAuthor(Long userId) {
    return jpa.countByUserId(userId);
  }
}
