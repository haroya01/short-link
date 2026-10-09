package com.example.short_link.common.user;

// Whose writing a viewer hears, as SQL a statement can carry: not someone the viewer blocked, nor
// someone who blocked the viewer, nor someone the viewer muted until the mute ends, as on Mastodon.
// The caller binds :viewer (-1 when anonymous, so nothing is left out) and :now.
public final class HeardSql {

  public static final String POST_AUTHOR =
      " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = :viewer AND hb.blocked_id = p.user_id)"
          + " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = p.user_id AND hb.blocked_id = :viewer)"
          + " AND NOT EXISTS (SELECT 1 FROM user_mute hm"
          + " WHERE hm.user_id = :viewer AND hm.muted_user_id = p.user_id"
          + " AND (hm.expires_at IS NULL OR hm.expires_at > :now))";

  private HeardSql() {}

  public static String heard(String authorColumn) {
    return POST_AUTHOR.replace("p.user_id", authorColumn);
  }

  public static long viewer(Long viewerId) {
    return viewerId == null ? -1L : viewerId;
  }
}
