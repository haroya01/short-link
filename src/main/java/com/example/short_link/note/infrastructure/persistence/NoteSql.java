package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.common.user.HeardSql;
import java.time.Duration;

final class NoteSql {

  static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private NoteSql() {}

  static String heard(String alias) {
    return HeardSql.heard(alias + ".user_id");
  }

  // As heard, for a note row: one from an account on a server the viewer blocked, or that this
  // server suspended, stays out too.
  static String heardNote(String alias) {
    return heard(alias)
        + HeardSql.heardRemote(alias + ".remote_actor_id")
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

  static String trendingSql(String columns, String filters) {
    return "SELECT "
        + columns
        + " FROM note o JOIN ("
        + "SELECT a.id, a.score, a.round, ROW_NUMBER() OVER ("
        + "PARTITION BY a.round, a.remote ORDER BY a.score DESC, a.id DESC) AS turn"
        + " FROM (SELECT s.id, s.score, s.remote, CEIL(ROW_NUMBER() OVER ("
        + "PARTITION BY s.author ORDER BY s.score DESC, s.id DESC) / :perAuthor) AS round"
        + " FROM (SELECT n.id, n.remote_actor_id IS NOT NULL AS remote,"
        + " COALESCE(n.user_id, -n.remote_actor_id) AS author, ("
        + "(SELECT COUNT(*) FROM note_like l WHERE l.note_id = n.id"
        + " AND NOT (l.user_id <=> n.user_id))"
        + " + (SELECT COUNT(*) FROM note_repost r WHERE r.note_id = n.id"
        + " AND NOT (r.user_id <=> n.user_id))"
        + " + (SELECT COUNT(*) FROM note c WHERE c.in_reply_to_id = n.id"
        + " AND c.reply_hidden_at IS NULL"
        + " AND NOT (c.user_id <=> n.user_id))"
        + " + (SELECT COUNT(*) FROM note_remote_reaction x WHERE x.note_id = n.id)"
        + ") AS score FROM note n"
        + " WHERE "
        + notReply("n")
        + " AND "
        + NoteAccessSql.listed("n")
        + " AND n.created_at >= :since"
        + " AND (n.remote_actor_id IS NULL"
        + " OR EXISTS (SELECT 1 FROM note_like ml WHERE ml.note_id = n.id)"
        + " OR EXISTS (SELECT 1 FROM note_repost mr WHERE mr.note_id = n.id)"
        + " OR EXISTS (SELECT 1 FROM note mc WHERE mc.in_reply_to_id = n.id"
        + " AND mc.user_id IS NOT NULL))"
        + heardNote("n")
        + filters
        + ") s) a) t ON t.id = o.id"
        + " ORDER BY t.round, t.turn, t.score DESC, t.id DESC";
  }
}
