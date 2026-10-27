package com.example.short_link.note.infrastructure.persistence;

import static com.example.short_link.note.infrastructure.persistence.NoteSql.heard;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.heardNote;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.inLanguages;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.notReply;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.unlimited;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.viewer;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.common.seen.Unseen;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.note.domain.LocalPoolRow;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.RemotePoolRow;
import com.example.short_link.note.domain.repository.NoteFeedReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
class NoteFeedReaderAdapter implements NoteFeedReader {

  @PersistenceContext private EntityManager em;

  // Without the forced indexes MySQL walks the primary key backwards through every reply and
  // received note above the page, since a small LIMIT makes it prefer the id order. Each source
  // stays its own branch: an OR across them leaves no index to read in id order, and MySQL scans
  // the whole table. A branch can hold no more than offset + limit of the page.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> topLevel(Long viewerId, int offset, int limit, Unseen unseen) {
    String hidden = unseen == null ? "" : Unseen.sql("n", Unseen.NOTE);
    Query query =
        em.createNativeQuery(
            "SELECT u.* FROM ((SELECT n.* FROM note n FORCE INDEX (idx_note_local_top_level) WHERE "
                + notReply("n")
                + " AND "
                + NoteAccessSql.listed("n")
                + " AND n.remote_actor_id IS NULL"
                + heard("n")
                + inLanguages("n")
                + hidden
                + " ORDER BY n.id DESC LIMIT :reach) UNION ALL (SELECT n.* FROM note n"
                + " FORCE INDEX (idx_note_room) WHERE "
                + NoteAccessSql.inFeedRooms("n")
                + " AND "
                + notReply("n")
                + heard("n")
                + inLanguages("n")
                + hidden
                + " ORDER BY n.id DESC LIMIT :reach)) u ORDER BY u.id DESC",
            NoteEntity.class);
    if (unseen != null) {
      unseen.bind(query, Unseen.NOTE);
    }
    return query
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setParameter("reach", (int) Math.min((long) offset + limit, Integer.MAX_VALUE))
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
                + " AND "
                + NoteAccessSql.listed("n")
                + " AND n.remote_actor_id IS NOT NULL"
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

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> federatedBefore(Long viewerId, Long beforeId, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE "
                + notReply("n")
                + " AND "
                + NoteAccessSql.listed("n")
                + " AND n.remote_actor_id IS NOT NULL"
                + " AND n.id < :before"
                + heardNote("n")
                + unlimited("n")
                + inLanguages("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("before", beforeId == null ? Long.MAX_VALUE : beforeId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setMaxResults(limit)
        .getResultList();
  }

  // STRAIGHT_JOIN walks federation_relayed_note's primary key down from the cursor, so a page reads
  // about as many rows as it shows. Left to itself MySQL starts from note's reply index and sorts
  // every kept note for each page.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> latestBefore(Long viewerId, Long beforeId, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM federation_relayed_note r STRAIGHT_JOIN note n ON n.id = r.note_id"
                + " WHERE r.note_id < :before AND NOT r.trended AND "
                + notReply("n")
                + " AND "
                + NoteAccessSql.listed("n")
                + heardNote("n")
                + unlimited("n")
                + inLanguages("n")
                + " ORDER BY r.note_id DESC",
            NoteEntity.class)
        .setParameter("before", beforeId == null ? Long.MAX_VALUE : beforeId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RemotePoolRow> remotePool(
      Long viewerId, Instant since, Instant until, int limit, boolean withFollowed) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT n.id, n.remote_actor_id, a.domain, n.language, n.created_at, n.uri,"
                    + " r.received_at"
                    + " FROM federation_relayed_note r STRAIGHT_JOIN note n ON n.id = r.note_id"
                    + " JOIN federation_remote_actor a ON a.id = n.remote_actor_id"
                    + " WHERE r.received_at >= :since AND r.received_at <= :until"
                    + " AND n.created_at >= :since AND "
                    + notReply("n")
                    + " AND "
                    + NoteAccessSql.listed("n")
                    + heardNote("n")
                    + unlimited("n")
                    + inLanguages("n")
                    + " AND n.content_warning IS NULL AND NOT n.marked_sensitive"
                    + (withFollowed
                        ? ""
                        : " AND NOT EXISTS (SELECT 1 FROM federation_following ff"
                            + " WHERE ff.user_id = :viewer"
                            + " AND ff.remote_actor_id = n.remote_actor_id"
                            + " AND ff.accepted_at IS NOT NULL)")
                    + " ORDER BY r.note_id DESC")
            .setParameter("since", since)
            .setParameter("until", until)
            .setParameter("viewer", viewer(viewerId))
            .setParameter("now", Instant.now())
            .setMaxResults(limit)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("remote_actor_id", Long.class)
            .addScalar("domain", String.class)
            .addScalar("language", String.class)
            .addScalar("created_at", Instant.class)
            .addScalar("uri", String.class)
            .addScalar("received_at", Instant.class)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new RemotePoolRow(
                    (Long) row[0],
                    (Long) row[1],
                    (String) row[2],
                    (String) row[3],
                    (Instant) row[4],
                    (String) row[5],
                    (Instant) row[6]))
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<LocalPoolRow> localPool(Long viewerId, Instant since, Instant until, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT n.id, n.user_id, n.language, n.created_at FROM note n"
                    + " FORCE INDEX (idx_note_local_top_level) WHERE "
                    + notReply("n")
                    + " AND "
                    + NoteAccessSql.listed("n")
                    + " AND n.remote_actor_id IS NULL"
                    + " AND n.created_at >= :since AND n.created_at <= :until"
                    + heard("n")
                    + inLanguages("n")
                    + " ORDER BY n.id DESC")
            .setParameter("since", since)
            .setParameter("until", until)
            .setParameter("viewer", viewer(viewerId))
            .setParameter("now", Instant.now())
            .setMaxResults(limit)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("user_id", Long.class)
            .addScalar("language", String.class)
            .addScalar("created_at", Instant.class)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new LocalPoolRow((Long) row[0], (Long) row[1], (String) row[2], (Instant) row[3]))
        .toList();
  }

  static String onProfile(String alias) {
    return " AND " + NoteAccessSql.onProfile(alias) + HeardSql.unblocked(alias + ".user_id");
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.user_id = :author AND "
                + notReply("n")
                + onProfile("n")
                + " ORDER BY n.pinned_at IS NULL, n.pinned_at DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setParameter("viewer", viewer(viewerId))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // A reply to the author's own note is a part of their thread and shows under its root instead.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> repliesByAuthor(Long authorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n LEFT JOIN note p ON p.id = n.in_reply_to_id"
                + " WHERE n.user_id = :author AND NOT ("
                + notReply("n")
                + ") AND NOT (p.user_id <=> n.user_id)"
                + onProfile("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setParameter("viewer", viewer(viewerId))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> withMediaByAuthor(Long authorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.user_id = :author"
                + " AND EXISTS (SELECT 1 FROM note_media m WHERE m.note_id = n.id)"
                + onProfile("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setParameter("viewer", viewer(viewerId))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> inRoom(Long roomId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.room_id = :room AND "
                + notReply("n")
                + " AND "
                + NoteAccessSql.readable("n")
                + heard("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("room", roomId)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Without the join order and index pinned, MySQL walks the primary key backwards through every
  // note on the server until the member's page fills.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> inRoomsOf(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM room_member m STRAIGHT_JOIN note n FORCE INDEX (idx_note_room)"
                + " ON n.room_id = m.room_id WHERE m.user_id = :viewer AND "
                + notReply("n")
                + " AND "
                + NoteAccessSql.readable("n")
                + heard("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> roomNotesBy(Long authorId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.user_id = :author AND "
                + NoteAccessSql.inRoom("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("author", authorId)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Private mentions (Mastodon's direct column): direct notes the viewer wrote or was named in,
  // replies included, newest first.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> direct(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE "
                + NoteAccessSql.direct("n")
                + " AND "
                + NoteAccessSql.readable("n")
                + HeardSql.unblockedRemote("n.remote_actor_id")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("viewer", viewerId)
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
  // the viewer join from anyone, as Mastodon's home timeline carries them. The notes of a series
  // the viewer subscribes to join as originals where the series page shows them, unless the
  // viewer does not hear their writer.
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
                    + " AND "
                    + NoteAccessSql.readable("o")
                    + " AND "
                    + NoteAccessSql.outsideRooms("o")
                    + " UNION ALL"
                    + " SELECT rn.id, rn.created_at, NULL FROM note rn WHERE "
                    + NoteAccessSql.inFeedRooms("rn")
                    + " AND "
                    + notReply("rn")
                    + heard("rn")
                    + " UNION ALL"
                    + " SELECT d.id, d.created_at, NULL FROM note_recipient dn"
                    + " JOIN note d ON d.id = dn.note_id"
                    + " WHERE dn.user_id = :viewer AND "
                    + NoteAccessSql.direct("d")
                    + " AND "
                    + notReply("d")
                    + " UNION ALL"
                    + " SELECT r.note_id, r.created_at, r.user_id FROM note_repost r"
                    + " JOIN note s ON s.id = r.note_id"
                    + " WHERE r.user_id IN (:authors) AND "
                    + NoteAccessSql.shareable("s")
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
                    + " AND "
                    + NoteAccessSql.readable("ro")
                    + HeardSql.heardRemote("ro.remote_actor_id")
                    + " UNION ALL"
                    + " SELECT g.note_id, gn.created_at, NULL FROM user_tag_pref f"
                    + " JOIN note_tag g ON g.tag = f.tag"
                    + " JOIN note gn ON gn.id = g.note_id"
                    + " WHERE f.user_id = :viewer AND f.kind = 'FOLLOW' AND "
                    + notReply("gn")
                    + " AND "
                    + NoteAccessSql.listed("gn")
                    + heardNote("gn")
                    + " UNION ALL"
                    + " SELECT sn.id, sn.created_at, NULL FROM series_subscription ss"
                    + " JOIN series_item si ON si.series_id = ss.series_id AND si.item_type = 'NOTE'"
                    + " JOIN note sn ON sn.id = si.ref_id"
                    + " WHERE ss.user_id = :viewer AND "
                    + NoteAccessSql.shareable("sn")
                    + heard("sn")
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

  // An account elsewhere, as this viewer may read it: public and unlisted to anyone, followers-only
  // to members whose follow it accepted, direct to the members it named.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> byRemoteActor(Long remoteActorId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.remote_actor_id = :actor AND "
                + notReply("n")
                + " AND "
                + NoteAccessSql.readable("n")
                + " AND NOT EXISTS (SELECT 1 FROM user_domain_block hd"
                + " JOIN federation_remote_actor ha ON ha.domain = hd.domain"
                + " WHERE hd.user_id = :viewer AND ha.id = n.remote_actor_id)"
                + HeardSql.unblockedRemote("n.remote_actor_id")
                + " ORDER BY n.created_at DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("actor", remoteActorId)
        .setParameter("viewer", viewer(viewerId))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> quotesOf(Long noteId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n WHERE n.quoted_note_id = :noteId AND "
                + NoteAccessSql.shareable("n")
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
            "SELECT n.* FROM note n WHERE n.quoted_post_id = :postId AND "
                + NoteAccessSql.shareable("n")
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
                    "SELECT COUNT(*) FROM note n WHERE n.quoted_post_id = :postId AND "
                        + NoteAccessSql.shareable("n")
                        + heardNote("n"))
                .setParameter("postId", postId)
                .setParameter("viewer", viewer(viewerId))
                .setParameter("now", Instant.now())
                .getSingleResult())
        .longValue();
  }

  // Public notes that carried the link's card, newest first, as the viewer may read them.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> linked(String url, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note_link_preview p JOIN note n ON n.id = p.note_id"
                + " WHERE p.url = :url AND "
                + NoteAccessSql.listed("n")
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
                + " WHERE g.tag = :tag AND "
                + NoteAccessSql.listed("n")
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
            "SELECT n.* FROM note n WHERE "
                + NoteAccessSql.listed("n")
                + " AND "
                + term
                + " AND (n.remote_actor_id IS NULL OR EXISTS (SELECT 1 FROM federation_remote_actor ia"
                + " WHERE ia.id = n.remote_actor_id AND ia.indexable))"
                + heardNote("n")
                + unlimited("n")
                + " ORDER BY n.id DESC",
            NoteEntity.class)
        .setParameter("term", match != null ? match : like)
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }
}
