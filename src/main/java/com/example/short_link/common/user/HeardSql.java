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

  public static String remoteBlocked(String viewerExpr, String remoteActorExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_block hrb WHERE hrb.user_id = "
        + viewerExpr
        + " AND hrb.remote_actor_id = "
        + remoteActorExpr
        + ")";
  }

  public static String remoteMuted(String viewerExpr, String remoteActorExpr) {
    return "EXISTS (SELECT 1 FROM federation_remote_mute hrm WHERE hrm.user_id = "
        + viewerExpr
        + " AND hrm.remote_actor_id = "
        + remoteActorExpr
        + " AND (hrm.expires_at IS NULL OR hrm.expires_at > :now))";
  }

  public static String remoteSilenced(String recipientExpr, String remoteActorExpr) {
    return "("
        + remoteBlocked(recipientExpr, remoteActorExpr)
        + " OR EXISTS (SELECT 1 FROM federation_remote_mute hrn WHERE hrn.user_id = "
        + recipientExpr
        + " AND hrn.remote_actor_id = "
        + remoteActorExpr
        + " AND hrn.hide_notifications"
        + " AND (hrn.expires_at IS NULL OR hrn.expires_at > :now)))";
  }

  public static String memberSilenced(String recipientExpr, String actorExpr) {
    return "(EXISTS (SELECT 1 FROM user_block hsb WHERE hsb.blocker_id = "
        + recipientExpr
        + " AND hsb.blocked_id = "
        + actorExpr
        + ") OR EXISTS (SELECT 1 FROM user_mute hsm WHERE hsm.user_id = "
        + recipientExpr
        + " AND hsm.muted_user_id = "
        + actorExpr
        + " AND hsm.hide_notifications"
        + " AND (hsm.expires_at IS NULL OR hsm.expires_at > :now)))";
  }

  public static String memberSilencedEitherWay(String recipientExpr, String actorExpr) {
    return "("
        + memberSilenced(recipientExpr, actorExpr)
        + " OR EXISTS (SELECT 1 FROM user_block hab WHERE hab.blocker_id = "
        + actorExpr
        + " AND hab.blocked_id = "
        + recipientExpr
        + "))";
  }

  public static String unblockedRemote(String remoteActorColumn) {
    return " AND NOT " + remoteBlocked(":viewer", remoteActorColumn);
  }

  public static String heardRemote(String remoteActorColumn) {
    return unblockedRemote(remoteActorColumn)
        + " AND NOT "
        + remoteMuted(":viewer", remoteActorColumn);
  }

  public static long viewer(Long viewerId) {
    return viewerId == null ? -1L : viewerId;
  }
}
