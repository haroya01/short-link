package com.example.short_link.note.infrastructure.persistence;

import static com.example.short_link.note.infrastructure.persistence.NoteSql.heardNote;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.viewer;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.SelfReply;
import com.example.short_link.note.domain.repository.NotePageReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
class NotePageReaderAdapter implements NotePageReader {

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, List<String>> tagsOf(Collection<Long> noteIds) {
    if (noteIds.isEmpty()) {
      return Map.of();
    }
    List<Object[]> rows =
        em.createNativeQuery("SELECT note_id, tag FROM note_tag WHERE note_id IN (:ids)")
            .setParameter("ids", noteIds)
            .unwrap(NativeQuery.class)
            .addScalar("note_id", Long.class)
            .addScalar("tag", String.class)
            .getResultList();
    Map<Long, List<String>> tags = new HashMap<>();
    rows.forEach(
        row -> tags.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]));
    return tags;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> heardAmong(Collection<Long> ids, Long viewerId) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.id IN (:ids)" + heardNote("n"), NoteEntity.class)
        .setParameter("ids", ids)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ReplyPreview> latestReplies(Collection<Long> noteIds, Long viewerId, int perNote) {
    if (noteIds.isEmpty()) {
      return List.of();
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT x.in_reply_to_id, x.id, x.user_id, x.username, x.display_name,"
                    + " x.avatar_url, x.body FROM (SELECT n.in_reply_to_id, n.id, n.body,"
                    + " u.id AS user_id, u.username, u.display_name, u.avatar_url, ROW_NUMBER()"
                    + " OVER (PARTITION BY n.in_reply_to_id ORDER BY n.id DESC) AS rn FROM note n"
                    + " JOIN users u ON u.id = n.user_id AND u.deleted_at IS NULL"
                    + " WHERE n.in_reply_to_id IN (:ids) AND n.reply_hidden_at IS NULL AND "
                    + NoteAccessSql.readable("n")
                    + heardNote("n")
                    + ") x WHERE x.rn <= :per ORDER BY x.in_reply_to_id, x.rn DESC")
            .setParameter("ids", noteIds)
            .setParameter("per", perNote)
            .setParameter("viewer", viewer(viewerId))
            .setParameter("now", Instant.now())
            .getResultList();
    List<ReplyPreview> previews = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      previews.add(
          new ReplyPreview(
              ((Number) row[0]).longValue(),
              ((Number) row[1]).longValue(),
              new ReplyPreview.Author(
                  ((Number) row[2]).longValue(), (String) row[3], (String) row[4], (String) row[5]),
              (String) row[6]));
    }
    return previews;
  }

  // The anchor's parent column is the root itself only to fix the column's type; depth 0 is
  // dropped.
  @Override
  public List<SelfReply> selfReplies(Collection<Long> rootIds, int depth) {
    if (rootIds.isEmpty()) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "WITH RECURSIVE chain (root_id, parent_id, id, user_id, remote_actor_id, depth) AS ("
                    + "SELECT n.id, n.id, n.id, n.user_id, n.remote_actor_id, 0 FROM note n"
                    + " WHERE n.id IN (:ids)"
                    + " UNION ALL SELECT c.root_id, c.id, r.id, r.user_id, r.remote_actor_id,"
                    + " c.depth + 1 FROM note r JOIN chain c ON r.in_reply_to_id = c.id"
                    + " AND r.user_id <=> c.user_id AND r.remote_actor_id <=> c.remote_actor_id"
                    + " WHERE c.depth < :depth)"
                    + " SELECT root_id, parent_id, id FROM chain WHERE depth > 0")
            .setParameter("ids", rootIds)
            .setParameter("depth", depth)
            .getResultList();
    List<SelfReply> replies = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      replies.add(
          new SelfReply(
              ((Number) cols[0]).longValue(),
              ((Number) cols[1]).longValue(),
              ((Number) cols[2]).longValue()));
    }
    return replies;
  }

  // Replies, likes, reposts and quotes of a page in one statement; likes and boosts from other
  // servers add to likes and reposts, and published posts that carry a note as a card add to its
  // quotes.
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
                    + " WHERE in_reply_to_id IN (:ids) AND reply_hidden_at IS NULL"
                    + " UNION ALL SELECT quoted_note_id, 'QUOTE' FROM note"
                    + " WHERE quoted_note_id IN (:ids)"
                    + " UNION ALL SELECT q.note_id, 'QUOTE' FROM post_note_quote q"
                    + " JOIN posts p ON p.id = q.post_id"
                    + " WHERE q.note_id IN (:ids) AND "
                    + PostAccessSql.CARD
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
                    + " WHERE user_id = :user AND note_id IN (:ids)"
                    + " UNION ALL SELECT n.id, 'MUTED' FROM note n"
                    + " JOIN note_conversation_mute c"
                    + " ON c.conversation_id = COALESCE(n.conversation_id, n.id)"
                    + " WHERE c.user_id = :user AND n.id IN (:ids)")
            .setParameter("user", userId)
            .setParameter("ids", noteIds)
            .getResultList();
    Set<Long> liked = new HashSet<>();
    Set<Long> reposted = new HashSet<>();
    Set<Long> bookmarked = new HashSet<>();
    Set<Long> muted = new HashSet<>();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      Long noteId = ((Number) cols[0]).longValue();
      switch (cols[1].toString()) {
        case "LIKE" -> liked.add(noteId);
        case "REPOST" -> reposted.add(noteId);
        case "MUTED" -> muted.add(noteId);
        default -> bookmarked.add(noteId);
      }
    }
    return new NoteViewerMarks(liked, reposted, bookmarked, muted);
  }
}
