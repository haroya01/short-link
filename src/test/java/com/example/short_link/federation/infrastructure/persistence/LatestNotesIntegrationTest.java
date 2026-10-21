package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class LatestNotesIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private FederationRelayedNoteRepository relayed;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long viewer;
  private Long alice;
  private Long bob;
  private Long carol;

  @BeforeEach
  void peopleHereAndElsewhere() {
    tag = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    UserEntity user = new UserEntity(tag + "@example.com", "google", tag);
    user.claimUsername(tag);
    viewer = users.save(user).getId();
    alice = actor("alice");
    bob = actor("bob");
    carol = actor("carol");
  }

  private String domain(String username) {
    return username + "-" + tag + ".example";
  }

  private Long actor(String username) {
    String uri = "https://" + domain(username) + "/users/" + username;
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        username + tag,
        domain(username));
    return number("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }

  private Long number(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    return ((Number) query.getSingleResult()).longValue();
  }

  private Long remoteNote(Long actorId, String visibility, boolean reply) {
    String uri = "https://elsewhere-" + tag + ".example/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility, reply)"
            + " VALUES (?1, ?2, 'hi', NOW(6), ?3, ?4)",
        actorId,
        uri,
        visibility,
        reply);
    return number("SELECT id FROM note WHERE uri = ?1", uri);
  }

  private Long pulled(Long actorId) {
    Long note = remoteNote(actorId, "PUBLIC", false);
    relayed.markPulled(note, "pulled-" + tag + ".example", Instant.now());
    return note;
  }

  private Long relayedNote(Long actorId) {
    Long note = remoteNote(actorId, "PUBLIC", false);
    relayed.mark(note, null, Instant.now());
    return note;
  }

  private List<Long> latest(Long viewerId, Long beforeId) {
    return notes.latestBefore(viewerId, beforeId, 50).stream()
        .map(NoteEntity::getId)
        .filter(this::ours)
        .toList();
  }

  private boolean ours(Long noteId) {
    return number(
            "SELECT COUNT(*) FROM note n JOIN federation_remote_actor a ON a.id = n.remote_actor_id"
                + " WHERE n.id = ?1 AND a.domain LIKE ?2",
            noteId,
            "%-" + tag + ".example")
        > 0;
  }

  @Test
  void theLatestAreThePulledAndRelayedNotesNewestFirstPagedByTheLastOneShown() {
    Long first = pulled(alice);
    Long relayedOne = relayedNote(bob);
    remoteNote(alice, "PUBLIC", false);
    Long answer = remoteNote(alice, "PUBLIC", true);
    relayed.markPulled(answer, "pulled-" + tag + ".example", Instant.now());
    Long unlisted = remoteNote(alice, "UNLISTED", false);
    relayed.markPulled(unlisted, "pulled-" + tag + ".example", Instant.now());
    Long newest = pulled(bob);

    assertThat(latest(viewer, null)).containsExactly(newest, relayedOne, first);
    assertThat(latest(null, null)).containsExactly(newest, relayedOne, first);
    assertThat(latest(viewer, relayedOne)).containsExactly(first);
  }

  @Test
  void anAccountTheViewerBlockedOrMutedOrAServerTheyOrThisServerShutOutStaysOut() {
    Long blocked = pulled(alice);
    Long muted = pulled(bob);
    Long shown = pulled(carol);
    Long elsewhere = pulled(actor("dave"));
    Long suspended = pulled(actor("erin"));
    Long limited = pulled(actor("finn"));
    update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?1, ?2, NOW(6))",
        viewer,
        alice);
    update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " expires_at, created_at) VALUES (?1, ?2, FALSE, NULL, NOW(6))",
        viewer,
        bob);
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        viewer,
        domain("dave"));
    update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?1, 'SUSPEND', NOW(6))",
        domain("erin"));
    update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?1, 'LIMIT', NOW(6))",
        domain("finn"));

    assertThat(latest(viewer, null)).containsExactly(shown);
    assertThat(latest(null, null)).containsExactly(elsewhere, shown, muted, blocked);
    assertThat(latest(null, null)).doesNotContain(suspended, limited);
  }
}
