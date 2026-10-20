package com.example.short_link.common.note;

import com.example.short_link.common.note.NoteVisibility.Reader;
import java.util.List;
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
    return readable(alias, ":viewer");
  }

  public static String readable(String alias, String viewer) {
    StringBuilder readable = new StringBuilder("(").append(shareable(alias));
    for (Reader reader : Reader.values()) {
      List<NoteVisibility> beyondAnyone =
          NoteVisibility.where(
              visibility -> visibility.readBy(reader) && !visibility.readBy(Reader.ANYONE));
      if (!beyondAnyone.isEmpty()) {
        readable
            .append(" OR (")
            .append(in(alias, beyondAnyone::contains))
            .append(" AND ")
            .append(holds(reader, alias, viewer))
            .append(")");
      }
    }
    return readable.append(")").toString();
  }

  private static String holds(Reader reader, String alias, String viewer) {
    return switch (reader) {
      case ANYONE -> "TRUE";
      case AUTHOR -> alias + ".user_id = " + viewer;
      case NAMED ->
          "EXISTS (SELECT 1 FROM note_recipient vr WHERE vr.note_id = "
              + alias
              + ".id AND vr.user_id = "
              + viewer
              + ")";
      case FOLLOWER ->
          "(EXISTS (SELECT 1 FROM user_follow vf WHERE vf.follower_id = "
              + viewer
              + " AND vf.following_id = "
              + alias
              + ".user_id) OR EXISTS (SELECT 1 FROM federation_following vff"
              + " WHERE vff.user_id = "
              + viewer
              + " AND vff.remote_actor_id = "
              + alias
              + ".remote_actor_id AND vff.accepted_at IS NOT NULL))";
      case MEMBER ->
          "EXISTS (SELECT 1 FROM room_member vm WHERE vm.room_id = "
              + alias
              + ".room_id AND vm.user_id = "
              + viewer
              + ")";
    };
  }

  public static String inRoom(String alias) {
    return in(alias, NoteVisibility::inRoom);
  }

  public static String outsideRooms(String alias) {
    return in(alias, visibility -> !visibility.inRoom());
  }

  public static String inFeedRooms(String alias) {
    return inRoom(alias)
        + " AND "
        + alias
        + ".room_id IN (SELECT fr.room_id FROM room_member fr"
        + " WHERE fr.user_id = :viewer AND NOT fr.hidden_from_feed)";
  }

  public static String onProfile(String alias) {
    return readable(alias)
        + " AND ("
        + in(alias, visibility -> visibility.onProfile(false))
        + " OR ("
        + in(alias, visibility -> visibility.onProfile(true))
        + " AND "
        + alias
        + ".user_id = :viewer))";
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
