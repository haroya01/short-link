package com.example.short_link.common.note;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteAccessSqlIntegrationTest {

  record Reader(String name, long id, boolean follows, boolean named, boolean member) {}

  record Written(long id, NoteVisibility visibility, boolean remote) {}

  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private UserRepository users;

  private long author;
  private long elsewhere;
  private long room;
  private final List<Reader> readers = new ArrayList<>();
  private final List<Written> written = new ArrayList<>();

  @BeforeEach
  void everyVisibilityFromHereAndElsewhereAndEveryKindOfReader() {
    String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    author = member("au" + tag);
    long follower = member("fo" + tag);
    long named = member("na" + tag);
    long pending = member("pe" + tag);
    long stranger = member("st" + tag);
    long roommate = member("ro" + tag);
    elsewhere = remoteActor(tag);
    update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (:o, :n, NOW(6), NOW(6))",
        Map.of("o", author, "n", tag));
    room =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM room WHERE name = :n", Map.of("n", tag), Long.class);
    update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (:r, :a, NOW(6)), (:r, :m, NOW(6))",
        Map.of("r", room, "a", author, "m", roommate));
    update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (:a, :b, NOW(6))",
        Map.of("a", follower, "b", author));
    update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (:u, :r, :f, NOW(6), NOW(6), NOW(6)),"
            + " (:p, :r, :g, NULL, NOW(6), NOW(6))",
        Map.of("u", follower, "p", pending, "r", elsewhere, "f", tag + "f", "g", tag + "g"));
    for (NoteVisibility visibility : NoteVisibility.values()) {
      for (boolean remote : List.of(false, true)) {
        long id = note(visibility, remote, tag);
        update(
            "INSERT INTO note_recipient (note_id, user_id) VALUES (:n, :u)",
            Map.of("n", id, "u", named));
        written.add(new Written(id, visibility, remote));
      }
    }
    readers.add(new Reader("anonymous", -1L, false, false, false));
    readers.add(new Reader("author", author, false, false, true));
    readers.add(new Reader("follower", follower, true, false, false));
    readers.add(new Reader("named", named, false, true, false));
    readers.add(new Reader("pending", pending, false, false, false));
    readers.add(new Reader("stranger", stranger, false, false, false));
    readers.add(new Reader("roommate", roommate, false, false, true));
  }

  @Test
  void everyFragmentKeepsExactlyTheNotesItsJavaTwinKeeps() {
    List<String> differ = new ArrayList<>();
    for (Reader reader : readers) {
      compare(differ, "listed", NoteAccessSql::listed, reader, note -> note.visibility().listed());
      compare(
          differ, "shareable", NoteAccessSql::shareable, reader, n -> n.visibility().shareable());
      compare(differ, "direct", NoteAccessSql::direct, reader, n -> n.visibility().direct());
      compare(differ, "inRoom", NoteAccessSql::inRoom, reader, n -> n.visibility().inRoom());
      compare(
          differ,
          "outsideRooms",
          NoteAccessSql::outsideRooms,
          reader,
          n -> !n.visibility().inRoom());
      compare(
          differ,
          "inFeedRooms",
          NoteAccessSql::inFeedRooms,
          reader,
          n -> n.visibility().inRoom() && reader.member());
      compare(differ, "readable", NoteAccessSql::readable, reader, n -> readable(reader, n));
      compare(
          differ,
          "onProfile",
          NoteAccessSql::onProfile,
          reader,
          n ->
              readable(reader, n)
                  && (n.visibility().onProfile(false)
                      || (n.visibility().onProfile(true) && owns(reader, n))));
    }

    assertThat(differ).isEmpty();
  }

  private boolean readable(Reader reader, Written note) {
    return note.visibility()
        .readableBy(
            relation ->
                switch (relation) {
                  case ANYONE -> true;
                  case AUTHOR -> owns(reader, note);
                  case NAMED -> reader.named();
                  case FOLLOWER -> reader.follows();
                  case MEMBER -> reader.member();
                });
  }

  private boolean owns(Reader reader, Written note) {
    return !note.remote() && reader.id() == author;
  }

  private void compare(
      List<String> differ,
      String name,
      Function<String, String> fragment,
      Reader reader,
      java.util.function.Predicate<Written> twin) {
    Map<Long, Written> byId = new LinkedHashMap<>();
    written.forEach(note -> byId.put(note.id(), note));
    Set<Long> sql =
        new TreeSet<>(
            jdbc.queryForList(
                "SELECT n.id FROM note n WHERE n.id IN (:ids) AND " + fragment.apply("n"),
                new MapSqlParameterSource()
                    .addValue("ids", byId.keySet())
                    .addValue("viewer", reader.id()),
                Long.class));
    Set<Long> java = new TreeSet<>();
    byId.values().stream().filter(twin).forEach(note -> java.add(note.id()));
    if (!sql.equals(java)) {
      differ.add(name + " for " + reader.name() + ": sql " + sql + " java " + java);
    }
  }

  private long member(String handle) {
    UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long remoteActor(String tag) {
    String uri = "https://" + tag + ".example/users/x";
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, username,"
            + " domain, fetched_at, created_at, updated_at)"
            + " VALUES (:u, :k, 'pem', :i, 'x', :d, NOW(6), NOW(6), NOW(6))",
        Map.of("u", uri, "k", uri + "#key", "i", uri + "/inbox", "d", tag + ".example"));
    return jdbc.queryForObject(
        "SELECT id FROM federation_remote_actor WHERE actor_uri = :u",
        Map.of("u", uri),
        Long.class);
  }

  private long note(NoteVisibility visibility, boolean remote, String tag) {
    String body = tag + UUID.randomUUID();
    String uri = remote ? "https://" + tag + ".example/n/" + body : null;
    MapSqlParameterSource row =
        new MapSqlParameterSource()
            .addValue("user", remote ? null : author)
            .addValue("actor", remote ? elsewhere : null)
            .addValue("uri", uri)
            .addValue("visibility", visibility.name())
            .addValue("room", room)
            .addValue("body", body);
    jdbc.update(
        "INSERT INTO note (user_id, remote_actor_id, uri, body, created_at, visibility, room_id)"
            + " VALUES (:user, :actor, :uri, :body, NOW(6), :visibility, :room)",
        row);
    return jdbc.queryForObject(
        "SELECT id FROM note WHERE body = :body", Map.of("body", body), Long.class);
  }

  private void update(String sql, Map<String, ?> values) {
    jdbc.update(sql, values);
  }
}
