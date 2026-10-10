package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.note.domain.NoteEditEntity;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.SelfReply;
import com.example.short_link.note.domain.TrendingLink;
import com.example.short_link.note.domain.TrendingTag;
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
  @SuppressWarnings("unchecked")
  public Optional<NoteEntity> findUnblocked(Long id, Long viewerId) {
    return em
        .createNativeQuery(
            "SELECT n.* FROM note n WHERE n.id = :id" + HeardSql.unblocked("n.user_id"),
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

  static String heard(String alias) {
    return HeardSql.heard(alias + ".user_id");
  }

  // As heard, for a note row: one from an account on a server the viewer blocked, or that this
  // server suspended, stays out too.
  static String heardNote(String alias) {
    return heard(alias)
        + " AND NOT EXISTS (SELECT 1 FROM user_domain_block hd"
        + " JOIN federation_remote_actor ha ON ha.domain = hd.domain"
        + " WHERE hd.user_id = :viewer AND ha.id = "
        + alias
        + ".remote_actor_id)"
        + " AND NOT "
        + ServerBlockSql.suspended(alias + ".remote_actor_id");
  }

  // What members discover (trending, tags) leaves out a limited server's notes, unless the viewer
  // follows the account.
  static String unlimited(String alias) {
    return " AND NOT " + ServerBlockSql.limitedFor(alias + ".remote_actor_id", ":viewer");
  }

  // Mastodon's filter languages: a viewer who chose languages sees notes in them, and notes whose
  // language nobody stated.
  static String inLanguages(String alias) {
    return " AND ("
        + alias
        + ".language IS NULL OR NOT EXISTS (SELECT 1 FROM note_feed_preference lp"
        + " WHERE lp.user_id = :viewer AND lp.languages IS NOT NULL"
        + " AND FIND_IN_SET("
        + alias
        + ".language, lp.languages) = 0))";
  }

  static long viewer(Long viewerId) {
    return HeardSql.viewer(viewerId);
  }

  // A reply whose parent is gone, or never reached this server, keeps reply set; the parent column
  // stays in the condition for idx_note_reply_created.
  static String notReply(String alias) {
    return alias + ".in_reply_to_id IS NULL AND NOT " + alias + ".reply";
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> topLevel(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE "
                + notReply("n")
                + " AND n.visibility = 'PUBLIC' AND n.remote_actor_id IS NULL"
                + heard("n")
                + inLanguages("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Mastodon's live feed of other servers: public notes this server received, newest first. A
  // limited server stays out unless the viewer follows the account, as on Mastodon.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> federated(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE "
                + notReply("n")
                + " AND n.visibility = 'PUBLIC' AND n.remote_actor_id IS NOT NULL"
                + heardNote("n")
                + unlimited("n")
                + inLanguages("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
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
            "SELECT n.* FROM note n WHERE n.user_id = :author AND "
                + notReply("n")
                + " AND (n.user_id = :viewer OR n.visibility IN ('PUBLIC', 'UNLISTED')"
                + " OR (n.visibility = 'PRIVATE' AND ("
                + "EXISTS (SELECT 1 FROM user_follow f"
                + " WHERE f.follower_id = :viewer AND f.following_id = n.user_id)"
                + " OR EXISTS (SELECT 1 FROM note_recipient r"
                + " WHERE r.note_id = n.id AND r.user_id = :viewer))))"
                + HeardSql.unblocked("n.user_id")
                + " ORDER BY n.pinned_at IS NULL, n.pinned_at DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setParameter("viewer", viewer(viewerId))
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
                    + " WHERE f.follower_id = :viewer AND f.following_id = n.user_id))"
                    + " OR (n.visibility = 'PRIVATE' AND EXISTS (SELECT 1 FROM federation_following ff"
                    + " WHERE ff.user_id = :viewer AND ff.remote_actor_id = n.remote_actor_id"
                    + " AND ff.accepted_at IS NOT NULL)))")
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
  // reposts, replies and likes or boosts from other servers. Ties fall back to newest first. A note
  // received from elsewhere joins once a member liked, reposted or answered it — Mastodon trends
  // such notes too, with a member's touch standing in for its moderator review.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> trending(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n"
                + " WHERE "
                + notReply("n")
                + " AND n.visibility = 'PUBLIC' AND n.created_at >= :since"
                + " AND (n.remote_actor_id IS NULL"
                + " OR EXISTS (SELECT 1 FROM note_like ml WHERE ml.note_id = n.id)"
                + " OR EXISTS (SELECT 1 FROM note_repost mr WHERE mr.note_id = n.id)"
                + " OR EXISTS (SELECT 1 FROM note mc WHERE mc.in_reply_to_id = n.id"
                + " AND mc.user_id IS NOT NULL))"
                + heardNote("n")
                + unlimited("n")
                + inLanguages("n")
                + " ORDER BY ("
                + "(SELECT COUNT(*) FROM note_like l WHERE l.note_id = n.id"
                + " AND NOT (l.user_id <=> n.user_id))"
                + " + (SELECT COUNT(*) FROM note_repost r WHERE r.note_id = n.id"
                + " AND NOT (r.user_id <=> n.user_id))"
                + " + (SELECT COUNT(*) FROM note c WHERE c.in_reply_to_id = n.id"
                + " AND c.reply_hidden_at IS NULL"
                + " AND NOT (c.user_id <=> n.user_id))"
                + " + (SELECT COUNT(*) FROM note_remote_reaction x WHERE x.note_id = n.id)"
                + ") DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("since", Instant.now().minus(TRENDING_WINDOW))
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
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
                    + " WHERE o.user_id IN (:authors) AND "
                    + notReply("o")
                    + heard("o")
                    + " AND (o.visibility <> 'DIRECT' OR o.user_id = :viewer"
                    + " OR EXISTS (SELECT 1 FROM note_recipient dr"
                    + " WHERE dr.note_id = o.id AND dr.user_id = :viewer))"
                    + " UNION ALL"
                    + " SELECT d.id, d.created_at, NULL FROM note_recipient dn"
                    + " JOIN note d ON d.id = dn.note_id"
                    + " WHERE dn.user_id = :viewer AND d.visibility = 'DIRECT' AND "
                    + notReply("d")
                    + " UNION ALL"
                    + " SELECT r.note_id, r.created_at, r.user_id FROM note_repost r"
                    + " JOIN note s ON s.id = r.note_id"
                    + " WHERE r.user_id IN (:authors) AND s.visibility IN ('PUBLIC', 'UNLISTED')"
                    + heardNote("s")
                    + heard("r")
                    + " AND NOT EXISTS (SELECT 1 FROM note_repost_mute m"
                    + " WHERE m.user_id = :viewer AND m.muted_user_id = r.user_id)"
                    + " AND NOT EXISTS (SELECT 1 FROM note_feed_preference p"
                    + " WHERE p.user_id = :viewer AND p.show_reposts = FALSE)"
                    + " UNION ALL"
                    + " SELECT ro.id, ro.created_at, NULL FROM federation_following ff"
                    + " JOIN note ro ON ro.remote_actor_id = ff.remote_actor_id"
                    + " WHERE ff.user_id = :viewer AND ff.accepted_at IS NOT NULL"
                    + " AND "
                    + notReply("ro")
                    + " AND ro.visibility <> 'DIRECT'"
                    + " UNION ALL"
                    + " SELECT g.note_id, gn.created_at, NULL FROM user_tag_pref f"
                    + " JOIN note_tag g ON g.tag = f.tag"
                    + " JOIN note gn ON gn.id = g.note_id"
                    + " WHERE f.user_id = :viewer AND f.kind = 'FOLLOW' AND "
                    + notReply("gn")
                    + " AND gn.visibility = 'PUBLIC'"
                    + heardNote("gn")
                    + ") t) y WHERE y.position = 1"
                    + ") x JOIN note n ON n.id = x.note_id"
                    + " ORDER BY x.at DESC, x.note_id DESC LIMIT :limit OFFSET :offset",
                NoteEntity.FEED_MAPPING)
            .setParameter("authors", authorIds)
            .setParameter("viewer", viewerId)
            .setParameter("now", Instant.now())
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
  public List<NoteEntity> replies(Long noteId, Long viewerId, int limit) {
    return answers(noteId, viewerId, limit, false);
  }

  @Override
  public List<NoteEntity> hiddenReplies(Long noteId, Long viewerId, int limit) {
    return answers(noteId, viewerId, limit, true);
  }

  @SuppressWarnings("unchecked")
  private List<NoteEntity> answers(Long noteId, Long viewerId, int limit, boolean hidden) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.in_reply_to_id = :noteId AND n.reply_hidden_at IS "
                + (hidden ? "NOT NULL" : "NULL")
                + heardNote("n")
                + " ORDER BY n.id ASC",
            NoteEntity.class)
        .setParameter("noteId", noteId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setMaxResults(limit)
        .getResultList();
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
                    + " WHERE q.note_id IN (:ids) AND p.status = 'PUBLISHED'"
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

  // An account elsewhere, as this viewer may read it: public and unlisted to anyone, followers-only
  // to members whose follow it accepted, direct to the members it named.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> byRemoteActor(Long remoteActorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.remote_actor_id = :actor AND "
                + notReply("n")
                + " AND (n.visibility IN ('PUBLIC', 'UNLISTED')"
                + " OR (n.visibility = 'PRIVATE' AND EXISTS (SELECT 1 FROM federation_following ff"
                + " WHERE ff.user_id = :viewer AND ff.remote_actor_id = n.remote_actor_id"
                + " AND ff.accepted_at IS NOT NULL))"
                + " OR EXISTS (SELECT 1 FROM note_recipient r"
                + " WHERE r.note_id = n.id AND r.user_id = :viewer))"
                + " AND NOT EXISTS (SELECT 1 FROM user_domain_block hd"
                + " JOIN federation_remote_actor ha ON ha.domain = hd.domain"
                + " WHERE hd.user_id = :viewer AND ha.id = n.remote_actor_id)"
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("actor", remoteActorId)
        .setParameter("viewer", viewer(viewerId))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public Optional<Long> idByUri(String uri) {
    List<?> rows =
        em.createNativeQuery("SELECT id FROM note WHERE uri = :uri")
            .setParameter("uri", uri)
            .getResultList();
    return rows.stream().findFirst().map(id -> ((Number) id).longValue());
  }

  // INSERT IGNORE on the uri key: a note delivered twice (shared and personal inbox, retries) is
  // stored once, and the second delivery reads back the first row.
  @Override
  public Optional<Long> insertRemote(RemoteNoteRow row) {
    int inserted =
        em.createNativeQuery(
                "INSERT IGNORE INTO note (remote_actor_id, uri, remote_url, body, created_at,"
                    + " content_warning, marked_sensitive, visibility, in_reply_to_id,"
                    + " conversation_id, reply, reply_policy, poll_multiple, language) VALUES"
                    + " (:actor, :uri, :url, :body, :createdAt, :warning, :sensitive, :visibility,"
                    + " :parent, :conversation, :reply, :replyPolicy, FALSE, :language)")
            .setParameter("actor", row.remoteActorId())
            .setParameter("uri", row.uri())
            .setParameter("url", row.url())
            .setParameter("body", row.body())
            .setParameter("createdAt", row.createdAt())
            .setParameter("warning", row.contentWarning())
            .setParameter("sensitive", row.sensitive())
            .setParameter("visibility", row.visibility().name())
            .setParameter("parent", row.inReplyToId())
            .setParameter("conversation", row.conversationId())
            .setParameter("reply", row.reply())
            .setParameter("replyPolicy", row.replyPolicy().name())
            .setParameter("language", row.language())
            .executeUpdate();
    return inserted == 0 ? Optional.empty() : idByUri(row.uri());
  }

  @Override
  public Optional<NoteEntity> findRemoteForUpdate(Long remoteActorId, Long noteId) {
    return jpa.findRemoteForUpdate(noteId, remoteActorId);
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

  @Override
  public int deleteRemote(Long remoteActorId, String uri) {
    return em.createNativeQuery("DELETE FROM note WHERE uri = :uri AND remote_actor_id = :actor")
        .setParameter("uri", uri)
        .setParameter("actor", remoteActorId)
        .executeUpdate();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> quotesOf(Long noteId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.quoted_note_id = :noteId"
                + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                + heardNote("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("noteId", noteId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> quotesOfPost(Long postId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.quoted_post_id = :postId"
                + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                + heardNote("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("postId", postId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public long countQuotesOfPost(Long postId, Long viewerId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM note n WHERE n.quoted_post_id = :postId"
                        + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                        + heardNote("n"))
                .setParameter("postId", postId)
                .setParameter("viewer", viewer(viewerId))
                .setParameter("now", Instant.now())
                .getSingleResult())
        .longValue();
  }

  // Trending hashtags in one statement: public notes of the window, from members or — as with
  // trending notes — from elsewhere once a member touched them, never from a limited or suspended
  // server. Ranked by how many accounts used the tag; each day's count rides along.
  @Override
  public List<TrendingTag> trendingTags(Instant now, int days, int minAccounts, int limit) {
    var query =
        em.createNativeQuery(
            "SELECT g.tag, COUNT(DISTINCT COALESCE(n.user_id, -n.remote_actor_id)) AS accounts,"
                + " COUNT(*) AS uses"
                + perDay(days)
                + " FROM note_tag g JOIN note n ON n.id = g.note_id"
                + " WHERE n.created_at >= :d0 AND n.visibility = 'PUBLIC'"
                + trendable("n")
                + " GROUP BY g.tag HAVING accounts >= :minAccounts"
                + " ORDER BY accounts DESC, uses DESC, g.tag LIMIT :limit");
    bindDays(query, now, days);
    List<?> rows =
        query.setParameter("minAccounts", minAccounts).setParameter("limit", limit).getResultList();
    List<TrendingTag> tags = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] columns = (Object[]) raw;
      tags.add(
          new TrendingTag(
              (String) columns[0],
              ((Number) columns[1]).longValue(),
              ((Number) columns[2]).longValue(),
              history(columns, 3, days)));
    }
    return tags;
  }

  // Mastodon's trending links: the link cards of public notes in the window, by how many accounts
  // shared them. A card's title and image are the latest fetched.
  @Override
  public List<TrendingLink> trendingLinks(Instant now, int days, int minAccounts, int limit) {
    var query =
        em.createNativeQuery(
            "SELECT p.url, MAX(p.title), MAX(p.description), MAX(p.image_url),"
                + " COUNT(DISTINCT COALESCE(n.user_id, -n.remote_actor_id)) AS accounts,"
                + " COUNT(*) AS uses"
                + perDay(days)
                + " FROM note_link_preview p JOIN note n ON n.id = p.note_id"
                + " WHERE n.created_at >= :d0 AND n.visibility = 'PUBLIC'"
                + trendable("n")
                + " GROUP BY p.url HAVING accounts >= :minAccounts"
                + " ORDER BY accounts DESC, uses DESC, p.url LIMIT :limit");
    bindDays(query, now, days);
    List<?> rows =
        query.setParameter("minAccounts", minAccounts).setParameter("limit", limit).getResultList();
    List<TrendingLink> links = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] columns = (Object[]) raw;
      links.add(
          new TrendingLink(
              (String) columns[0],
              (String) columns[1],
              (String) columns[2],
              (String) columns[3],
              ((Number) columns[4]).longValue(),
              ((Number) columns[5]).longValue(),
              history(columns, 6, days)));
    }
    return links;
  }

  // A member's public note trends; one received from elsewhere once a member liked, reposted or
  // answered it, and never from a server moderators blocked.
  private static String trendable(String alias) {
    return " AND ("
        + alias
        + ".remote_actor_id IS NULL"
        + " OR EXISTS (SELECT 1 FROM note_like ml WHERE ml.note_id = "
        + alias
        + ".id)"
        + " OR EXISTS (SELECT 1 FROM note_repost mr WHERE mr.note_id = "
        + alias
        + ".id)"
        + " OR EXISTS (SELECT 1 FROM note mc WHERE mc.in_reply_to_id = "
        + alias
        + ".id"
        + " AND mc.user_id IS NOT NULL))"
        + " AND NOT EXISTS (SELECT 1 FROM federation_remote_actor ta"
        + " JOIN federation_domain_block tb ON "
        + ServerBlockSql.covers("tb", "ta.domain")
        + " WHERE ta.id = "
        + alias
        + ".remote_actor_id)";
  }

  // The last day has no end: a note stamped a little ahead (another server's clock) is today's.
  private static String perDay(int days) {
    StringBuilder perDay = new StringBuilder();
    for (int day = 0; day < days; day++) {
      perDay.append(", SUM(n.created_at >= :d").append(day);
      if (day < days - 1) {
        perDay.append(" AND n.created_at < :d").append(day + 1);
      }
      perDay.append(")");
    }
    return perDay.toString();
  }

  private static void bindDays(jakarta.persistence.Query query, Instant now, int days) {
    for (int day = 0; day < days; day++) {
      query.setParameter("d" + day, now.minus(Duration.ofDays(days - day)));
    }
  }

  private static List<Long> history(Object[] columns, int from, int days) {
    List<Long> history = new ArrayList<>(days);
    for (int day = 0; day < days; day++) {
      Object count = columns[from + day];
      history.add(count == null ? 0L : ((Number) count).longValue());
    }
    return List.copyOf(history);
  }

  // Public notes that carried the link's card, newest first, as the viewer may read them.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> linked(String url, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note_link_preview p JOIN note n ON n.id = p.note_id"
                + " WHERE p.url = :url AND n.visibility = 'PUBLIC'"
                + heardNote("n")
                + unlimited("n")
                + " ORDER BY n.id DESC LIMIT :limit OFFSET :offset",
            NoteEntity.class)
        .setParameter("url", url)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setParameter("limit", limit)
        .setParameter("offset", offset)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> tagged(String tag, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note_tag g JOIN note n ON n.id = g.note_id"
                + " WHERE g.tag = :tag AND n.visibility = 'PUBLIC'"
                + heardNote("n")
                + unlimited("n")
                + " ORDER BY g.note_id DESC LIMIT :limit OFFSET :offset",
            NoteEntity.class)
        .setParameter("tag", tag)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setParameter("limit", limit)
        .setParameter("offset", offset)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> search(String match, String like, Long viewerId, int offset, int limit) {
    String term =
        match != null
            ? "MATCH(n.body) AGAINST(:term IN BOOLEAN MODE)"
            : "LOWER(n.body) LIKE :term ESCAPE '!'";
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.visibility = 'PUBLIC' AND n.remote_actor_id IS NULL AND "
                + term
                + heard("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("term", match != null ? match : like)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
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
