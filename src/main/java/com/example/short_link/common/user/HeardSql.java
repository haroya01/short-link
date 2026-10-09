package com.example.short_link.common.user;

// Whose writing a viewer hears, as SQL a statement can carry. A block either way hides a writer
// everywhere, even where the viewer opens them directly; a mute until it ends hides them only
// inside lists of other people's writing, as on Mastodon. The caller binds :viewer (-1 when
// anonymous, so nothing is left out), and :now wherever a mute is checked.
public final class HeardSql {

  public static final String UNBLOCKED_POST_AUTHOR =
      " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = :viewer AND hb.blocked_id = p.user_id)"
          + " AND NOT EXISTS (SELECT 1 FROM user_block hb"
          + " WHERE hb.blocker_id = p.user_id AND hb.blocked_id = :viewer)";

  private static final String MUTED_POST_AUTHOR =
      "EXISTS (SELECT 1 FROM user_mute hm"
          + " WHERE hm.user_id = :viewer AND hm.muted_user_id = p.user_id"
          + " AND (hm.expires_at IS NULL OR hm.expires_at > :now))";

  public static final String POST_AUTHOR = UNBLOCKED_POST_AUTHOR + " AND NOT " + MUTED_POST_AUTHOR;

  private HeardSql() {}

  public static String heard(String authorColumn) {
    return POST_AUTHOR.replace("p.user_id", authorColumn);
  }

  public static String unblocked(String authorColumn) {
    return UNBLOCKED_POST_AUTHOR.replace("p.user_id", authorColumn);
  }

  public static String muted(String authorColumn) {
    return MUTED_POST_AUTHOR.replace("p.user_id", authorColumn);
  }

  public static long viewer(Long viewerId) {
    return viewerId == null ? -1L : viewerId;
  }
}
