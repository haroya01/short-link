package com.example.short_link.common.note;

import java.util.function.Predicate;
import java.util.stream.Collectors;

// NoteVisibility's questions for a list in SQL, each built from the Java answer so the two cannot
// drift. The caller binds :viewer (-1 when anonymous) wherever readable or onProfile is used.
public final class NoteAccessSql {

  private NoteAccessSql() {}

  public static String listed(String alias) {
    return in(alias, NoteVisibility::listed);
  }

  public static String shareable(String alias) {
    return in(alias, NoteVisibility::shareable);
  }

  public static String direct(String alias) {
    return in(alias, NoteVisibility::direct);
  }

  public static String readable(String alias) {
    return "("
        + shareable(alias)
        + " OR "
        + alias
        + ".user_id = :viewer OR "
        + named(alias)
        + " OR ("
        + followersOnly(alias)
        + " AND (EXISTS (SELECT 1 FROM user_follow vf"
        + " WHERE vf.follower_id = :viewer AND vf.following_id = "
        + alias
        + ".user_id)"
        + " OR EXISTS (SELECT 1 FROM federation_following vff"
        + " WHERE vff.user_id = :viewer AND vff.remote_actor_id = "
        + alias
        + ".remote_actor_id AND vff.accepted_at IS NOT NULL))))";
  }

  public static String onProfile(String alias) {
    return readable(alias)
        + " AND ("
        + alias
        + ".user_id = :viewer OR NOT ("
        + direct(alias)
        + "))";
  }

  private static String named(String alias) {
    return "EXISTS (SELECT 1 FROM note_recipient vr WHERE vr.note_id = "
        + alias
        + ".id AND vr.user_id = :viewer)";
  }

  private static String followersOnly(String alias) {
    return in(alias, visibility -> visibility.followersRead() && !visibility.shareable());
  }

  private static String in(String alias, Predicate<NoteVisibility> question) {
    return alias
        + ".visibility IN ("
        + NoteVisibility.where(question).stream()
            .map(visibility -> "'" + visibility.name() + "'")
            .collect(Collectors.joining(", "))
        + ")";
  }
}
