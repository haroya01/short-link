package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomNoteErasureIntegrationTest {

  @Autowired private RoomNoteErasures erasures;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private String tag;
  private long writer;
  private long other;
  private long room;
  private long elsewhere;

  @BeforeEach
  void aWriterWithNotesInTwoRooms() {
    tag = "er" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    writer = member("w");
    other = member("o");
    room = room("a");
    elsewhere = room("b");
    for (int i = 0; i < 3; i++) {
      note(writer, room);
    }
    note(other, room);
    note(writer, elsewhere);
  }

  @Test
  void aRequestIsErasedChunkByChunkAndThenForgotten() {
    erasures.request(writer, room);
    erasures.request(writer, room);
    assertThat(requests()).isOne();

    assertThat(erasures.erase(writer, room, 2)).isEqualTo(2);
    em.flush();
    assertThat(notes(writer, room)).isOne();
    assertThat(requests()).isOne();

    assertThat(erasures.erase(writer, room, 2)).isEqualTo(1);
    em.flush();
    assertThat(notes(writer, room)).isZero();
    assertThat(requests()).isZero();

    assertThat(erasures.erase(writer, room, 2)).isZero();
    assertThat(notes(other, room)).isOne();
    assertThat(notes(writer, elsewhere)).isOne();
  }

  private long requests() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM room_note_erasure WHERE user_id = ? AND room_id = ?",
        Long.class,
        writer,
        room);
  }

  private long notes(long user, long inRoom) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM note WHERE user_id = ? AND room_id = ?", Long.class, user, inRoom);
  }

  private long member(String prefix) {
    String handle = tag + prefix;
    UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long room(String suffix) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        writer,
        tag + suffix);
    return jdbc.queryForObject("SELECT id FROM room WHERE name = ?", Long.class, tag + suffix);
  }

  private void note(long user, long inRoom) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, room_id)"
            + " VALUES (?, ?, NOW(6), 'ROOM', ?)",
        user,
        tag,
        inRoom);
  }
}
