package com.example.short_link.common.post;

import java.util.function.Predicate;
import java.util.stream.Collectors;

// The list half of PostAccessPolicy: each fragment is built from the PostAudience question its Java
// twin asks, so the two cannot drift. The caller binds :viewer (-1 when anonymous) wherever
// PROFILE, a room fragment or said() is used.
public final class PostAccessSql {

  public static final boolean REGION_RESTRICTED_IN_DISCOVERY = true;

  // Spelled out because annotation queries need constants; PostAccessSqlTest holds each to the
  // fragment the audience questions build.
  public static final String LISTED = "p.status = 'PUBLISHED' AND p.audience IN ('PUBLIC')";

  public static final String OPEN =
      "p.status = 'PUBLISHED' AND p.audience IN ('PUBLIC') AND p.blocked_countries IS NULL";

  public static final String CARD =
      "p.status = 'PUBLISHED' AND p.audience IN ('PUBLIC', 'FOLLOWERS', 'PASSWORD')";

  public static final String LISTED_AS_Q = "q.status = 'PUBLISHED' AND q.audience IN ('PUBLIC')";

  public static final String PROFILE =
      "p.status = 'PUBLISHED' AND (p.audience IN ('PUBLIC', 'FOLLOWERS', 'PASSWORD')"
          + " OR (p.audience IN ('PUBLIC', 'UNLISTED', 'FOLLOWERS', 'PASSWORD')"
          + " AND p.user_id = :viewer))";

  private PostAccessSql() {}

  public static String listed(String alias) {
    return published(alias)
        + " AND "
        + in(alias, PostAudience::listed)
        + (REGION_RESTRICTED_IN_DISCOVERY ? "" : " AND " + alias + ".blocked_countries IS NULL");
  }

  public static String open(String alias) {
    return published(alias)
        + " AND "
        + in(alias, PostAudience::listed)
        + " AND "
        + alias
        + ".blocked_countries IS NULL";
  }

  public static String profile(String alias) {
    return published(alias)
        + " AND ("
        + in(alias, audience -> audience.onProfile(false))
        + " OR ("
        + in(alias, audience -> audience.onProfile(true))
        + " AND "
        + alias
        + ".user_id = :viewer))";
  }

  public static String card(String alias) {
    return published(alias) + " AND " + in(alias, PostAudience::shownAsCard);
  }

  public static String inRoomsOf(String alias) {
    return inRooms(alias)
        + " AND EXISTS (SELECT 1 FROM room_member pm WHERE pm.room_id = "
        + alias
        + ".room_id AND pm.user_id = :viewer)";
  }

  public static String inRoom(String alias, String roomParam) {
    return inRoomsOf(alias) + " AND " + alias + ".room_id = " + roomParam;
  }

  public static String inFeedRooms(String alias) {
    return inRooms(alias)
        + " AND "
        + alias
        + ".room_id IN (SELECT fr.room_id FROM room_member fr"
        + " WHERE fr.user_id = :viewer AND NOT fr.hidden_from_feed)";
  }

  // What was said on a post while it was in a room (a comment, a highlight) stays with that room's
  // members after the post leaves it.
  public static String said(String alias) {
    return "("
        + alias
        + ".room_id IS NULL OR EXISTS (SELECT 1 FROM room_member sm WHERE sm.room_id = "
        + alias
        + ".room_id AND sm.user_id = :viewer))";
  }

  public static String saidOpenly(String alias) {
    return alias + ".room_id IS NULL";
  }

  private static String inRooms(String alias) {
    return published(alias) + " AND " + in(alias, PostAudience::inRoom);
  }

  private static String published(String alias) {
    return alias + ".status = 'PUBLISHED'";
  }

  private static String in(String alias, Predicate<PostAudience> question) {
    return alias
        + ".audience IN ("
        + PostAudience.where(question).stream()
            .map(audience -> "'" + audience.name() + "'")
            .collect(Collectors.joining(", "))
        + ")";
  }
}
