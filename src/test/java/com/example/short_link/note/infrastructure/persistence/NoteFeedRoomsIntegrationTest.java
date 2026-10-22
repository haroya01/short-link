package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteFeedRoomsIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long ann;
  private Long ben;
  private Long cat;
  private Long out;
  private List<Long> annLatest;
  private List<Long> publicLatest;
  private List<Long> annRooms;

  @BeforeEach
  void publicAndRoomNotesTakingTurns() {
    tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    ann = user("ann");
    ben = user("ben");
    cat = user("cat");
    out = user("out");
    Long shown = room(ann, false, ben, cat);
    Long hidden = room(ann, true, ben);
    Long foreign = room(ben, false);
    update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?1, ?2, FALSE, NOW(6))",
        ann,
        cat);

    Long pub1 = note(ben, "PUBLIC", null);
    Long shown1 = note(ben, "ROOM", shown);
    Long hidden1 = note(ben, "ROOM", hidden);
    note(ben, "ROOM", foreign);
    Long pub2 = note(ben, "PUBLIC", null);
    reply(ben, pub2);
    note(ben, "UNLISTED", null);
    Long shown2 = note(ann, "ROOM", shown);
    note(cat, "ROOM", shown);
    Long pub3 = note(ann, "PUBLIC", null);
    Long catPub = note(cat, "PUBLIC", null);
    remote();
    Long shown3 = note(ben, "ROOM", shown);
    Long pub4 = note(ben, "PUBLIC", null);

    annLatest = List.of(pub4, shown3, pub3, shown2, pub2, shown1, pub1);
    publicLatest = List.of(pub4, catPub, pub3, pub2, pub1);
    annRooms = List.of(shown3, shown2, hidden1, shown1);
  }

  @Test
  void theLatestFeedTakesPublicNotesAndTheMembersShownRoomsInIdOrder() {
    assertThat(newest(ann, annLatest.size())).containsExactlyElementsOf(annLatest);
  }

  @Test
  void othersGetThePublicNotesAlone() {
    assertThat(newest(null, publicLatest.size())).containsExactlyElementsOf(publicLatest);
    assertThat(newest(out, publicLatest.size())).containsExactlyElementsOf(publicLatest);
  }

  @Test
  void walkingTheLatestFeedByAnyPageSizeNeitherRepeatsNorSkips() {
    for (int size = 1; size <= annLatest.size(); size++) {
      assertThat(
              walk(size, (viewer, offset, limit) -> notes.topLevel(viewer, offset, limit, null))
                  .subList(0, annLatest.size()))
          .as("page size %d", size)
          .containsExactlyElementsOf(annLatest);
    }
  }

  @Test
  void aReaderWhoChoseLanguagesGetsNeitherSourceInOthers() {
    Long japanesePublic = note(ben, "PUBLIC", null, "ja");
    Long japaneseRoom = note(ben, "ROOM", roomOf(ann), "ja");
    update(
        "INSERT INTO note_feed_preference (user_id, show_reposts, languages, updated_at)"
            + " VALUES (?1, TRUE, 'ko', NOW(6))",
        ann);

    assertThat(newest(ann, annLatest.size())).containsExactlyElementsOf(annLatest);
    assertThat(notes.topLevel(ann, 0, 50, null).stream().map(NoteEntity::getId))
        .doesNotContain(japanesePublic, japaneseRoom);
  }

  @Test
  void everyRoomOfTheMemberWalksInIdOrderByAnyPageSize() {
    for (int size = 1; size <= annRooms.size(); size++) {
      assertThat(walk(size, notes::inRoomsOf))
          .as("page size %d", size)
          .containsExactlyElementsOf(annRooms);
    }
    assertThat(notes.inRoomsOf(out, 0, 20)).isEmpty();
  }

  interface Page {
    List<NoteEntity> at(Long viewerId, int offset, int limit);
  }

  private List<Long> newest(Long viewerId, int count) {
    return notes.topLevel(viewerId, 0, count, null).stream().map(NoteEntity::getId).toList();
  }

  private List<Long> walk(int size, Page page) {
    List<Long> seen = new ArrayList<>();
    for (int offset = 0; offset < 50; offset += size) {
      List<NoteEntity> rows = page.at(ann, offset, size);
      if (rows.isEmpty()) {
        break;
      }
      rows.forEach(note -> seen.add(note.getId()));
    }
    return seen;
  }

  private Long roomOf(Long member) {
    return id(
        "SELECT MIN(m.room_id) FROM room_member m WHERE m.user_id = ?1 AND NOT m.hidden_from_feed",
        member);
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long room(Long owner, boolean hiddenByAnn, Long... members) {
    update(
        "INSERT INTO room (owner_id, name, approval_required, created_at, updated_at)"
            + " VALUES (?1, ?2, FALSE, NOW(6), NOW(6))",
        owner,
        "room " + tag);
    Long room = id("SELECT MAX(id) FROM room WHERE owner_id = ?1", owner);
    member(room, owner, owner.equals(ann) && hiddenByAnn);
    for (Long member : members) {
      member(room, member, member.equals(ann) && hiddenByAnn);
    }
    return room;
  }

  private void member(Long room, Long user, boolean hidden) {
    update(
        "INSERT INTO room_member (room_id, user_id, joined_at, hidden_from_feed)"
            + " VALUES (?1, ?2, NOW(6), ?3)",
        room,
        user,
        hidden);
  }

  private Long note(Long author, String visibility, Long room) {
    return note(author, visibility, room, null);
  }

  private Long note(Long author, String visibility, Long room, String language) {
    update(
        "INSERT INTO note (user_id, body, visibility, room_id, language, created_at)"
            + " VALUES (?1, 'here', ?2, ?3, ?4, NOW(6))",
        author,
        visibility,
        room,
        language);
    return id("SELECT MAX(id) FROM note WHERE user_id = ?1", author);
  }

  private void reply(Long author, Long parent) {
    update(
        "INSERT INTO note (user_id, body, in_reply_to_id, reply, created_at)"
            + " VALUES (?1, 'answer', ?2, TRUE, NOW(6))",
        author,
        parent);
  }

  private void remote() {
    String uri = "https://" + tag + ".example/users/someone";
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, 'someone', ?4, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        tag + ".example");
    Long actor = id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, 'elsewhere', NOW(6), 'PUBLIC')",
        actor,
        uri + "/notes/1");
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }

  private Long id(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    return ((Number) query.getSingleResult()).longValue();
  }
}
