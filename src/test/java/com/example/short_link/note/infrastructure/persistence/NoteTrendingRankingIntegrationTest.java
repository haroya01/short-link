package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
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
class NoteTrendingRankingIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;

  @BeforeEach
  void tag() {
    tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long actor(String domain) {
    String uri = "https://" + domain + "/users/someone";
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, 'someone', ?4, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        domain);
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

  private Long localNote(Long userId, String language) {
    update(
        "INSERT INTO note (user_id, body, language, created_at) VALUES (?1, 'here', ?2, NOW(6))",
        userId,
        language);
    return id("SELECT MAX(id) FROM note WHERE user_id = ?1", userId);
  }

  private Long remoteNote(Long actorId, Long likedBy) {
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
    return noteId;
  }

  private Set<Long> trending(Long viewerId) {
    Set<Long> ids = new HashSet<>();
    notes.trending(viewerId, 0, 500).forEach(note -> ids.add(note.getId()));
    return ids;
  }

  @Test
  void aLimitedServersNoteTrendsOnlyForThoseWhoFollowTheAccount() {
    Long member = user("m");
    Long follower = user("f");
    Long actor = actor(tag + ".example");
    update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?1, 'LIMIT', NOW(6))",
        tag + ".example");
    update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?1, ?2, ?3, NOW(6), NOW(6), NOW(6))",
        follower,
        actor,
        "https://kurl.example/follows/" + tag);
    Long limited = remoteNote(actor, member);

    assertThat(trending(null)).doesNotContain(limited);
    assertThat(trending(member)).doesNotContain(limited);
    assertThat(trending(follower)).contains(limited);
  }

  @Test
  void aReaderWhoChoseLanguagesSeesTrendingInThemAlone() {
    Long author = user("a");
    Long reader = user("r");
    Long korean = localNote(author, "ko");
    Long japanese = localNote(author, "ja");
    update(
        "INSERT INTO note_feed_preference (user_id, show_reposts, languages, updated_at)"
            + " VALUES (?1, TRUE, 'ko', NOW(6))",
        reader);

    assertThat(trending(reader)).contains(korean).doesNotContain(japanese);
    assertThat(trending(null)).contains(korean, japanese);
  }

  @Test
  void pagesPastTheSharedRankingContinueWithoutRepeatsOrGaps() {
    List<Long> authors = List.of(user("x"), user("y"), user("z"));
    Set<Long> mine = new HashSet<>();
    for (int i = 0; i < TrendingNoteRanking.SIZE + 5; i++) {
      mine.add(localNote(authors.get(i % authors.size()), null));
    }

    List<Long> seen = new ArrayList<>();
    for (int offset = 0; ; offset += 10) {
      List<NoteEntity> page = notes.trending(null, offset, 10);
      page.stream().map(NoteEntity::getId).filter(mine::contains).forEach(seen::add);
      if (page.size() < 10) {
        break;
      }
    }

    assertThat(seen).doesNotHaveDuplicates().hasSize(mine.size());
  }
}
