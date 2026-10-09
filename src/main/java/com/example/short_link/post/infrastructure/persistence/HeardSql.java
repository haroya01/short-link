package com.example.short_link.post.infrastructure.persistence;

// The note lists' heard() rule, kept in step with it: the caller binds :viewer (-1 when anonymous)
// and :now.
final class HeardSql {

  static final String POST_AUTHOR =
      " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = :viewer AND hb.blocked_id = p.user_id)"
          + " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = p.user_id AND hb.blocked_id = :viewer)"
          + " AND NOT EXISTS (SELECT 1 FROM user_mute hm"
          + " WHERE hm.user_id = :viewer AND hm.muted_user_id = p.user_id"
          + " AND (hm.expires_at IS NULL OR hm.expires_at > :now))";

  private HeardSql() {}

  static String authoredBy(String alias) {
    return POST_AUTHOR.replace("p.user_id", alias + ".user_id");
  }

  static long viewer(Long viewerId) {
    return viewerId == null ? -1L : viewerId;
  }
}
