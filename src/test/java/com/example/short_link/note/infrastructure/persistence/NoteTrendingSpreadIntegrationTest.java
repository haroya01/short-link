package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteTrendsReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
class NoteTrendingSpreadIntegrationTest {

  @Autowired private NoteTrendsReader trends;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private final List<Long> fans = new ArrayList<>();

  @BeforeEach
  void fans() {
    tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    for (int i = 0; i < 12; i++) {
      fans.add(actor("fan" + i));
    }
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long actor(String username) {
    String uri = "https://" + tag + ".example/users/" + username;
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        username,
        tag + ".example");
    return id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
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

  private Long localNote(Long userId, int reactions) {
    update("INSERT INTO note (user_id, body, created_at) VALUES (?1, 'here', NOW(6))", userId);
    Long noteId = id("SELECT MAX(id) FROM note WHERE user_id = ?1", userId);
    react(noteId, reactions);
    return noteId;
  }

  private Long remoteNote(Long actorId, Long likedBy, int reactions) {
    String uri = "https://" + tag + ".example/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, 'elsewhere', NOW(6), 'PUBLIC')",
        actorId,
        uri);
    Long noteId = id("SELECT id FROM note WHERE uri = ?1", uri);
    update(
        "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        noteId,
        likedBy);
    react(noteId, reactions - 1);
    return noteId;
  }

  private void react(Long noteId, int count) {
    for (int i = 0; i < count; i++) {
      update(
          "INSERT INTO note_remote_reaction (note_id, remote_actor_id, kind, activity_id,"
              + " created_at) VALUES (?1, ?2, 'LIKE', ?3, NOW(6))",
          noteId,
          fans.get(i),
          "https://" + tag + ".example/likes/" + UUID.randomUUID());
    }
  }

  private List<Long> ranked(Set<Long> mine) {
    return trends.trending(null, 0, 500).stream()
        .map(NoteEntity::getId)
        .filter(mine::contains)
        .toList();
  }

  @Test
  void anAuthorPlacesTwoNotesPerRoundAndMembersTakeTurnsWithNotesFromElsewhere() {
    Long prolific = user("p");
    Long quiet = user("q");
    Long rare = user("r");
    Long a10 = localNote(prolific, 10);
    Long a9 = localNote(prolific, 9);
    Long a8 = localNote(prolific, 8);
    Long a7 = localNote(prolific, 7);
    Long b3 = localNote(quiet, 3);
    Long c1 = localNote(rare, 1);
    Long r2 = remoteNote(actor("alice"), rare, 2);

    assertThat(ranked(Set.of(a10, a9, a8, a7, b3, c1, r2)))
        .containsExactly(a10, r2, a9, b3, c1, a8, a7);
  }

  @Test
  void aPageAfterTheFirstContinuesTheSameOrder() {
    Long prolific = user("p");
    Long quiet = user("q");
    Long a5 = localNote(prolific, 5);
    Long a4 = localNote(prolific, 4);
    Long a3 = localNote(prolific, 3);
    Long b1 = localNote(quiet, 1);
    Set<Long> mine = Set.of(a5, a4, a3, b1);

    List<Long> all = ranked(mine);
    assertThat(all).containsExactly(a5, a4, b1, a3);
    List<Long> paged = new ArrayList<>();
    int total = trends.trending(null, 0, 500).size();
    for (int offset = 0; offset < total; offset += 2) {
      trends.trending(null, offset, 2).stream()
          .map(NoteEntity::getId)
          .filter(mine::contains)
          .forEach(paged::add);
    }
    assertThat(paged).isEqualTo(all);
  }
}
