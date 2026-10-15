package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
class NoteSearchElsewhereIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long viewer;
  private Long writer;

  @BeforeEach
  void people() {
    tag = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    viewer = user("v");
    writer = user("w");
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private void exec(String sql, Object... values) {
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

  private String domain(String name) {
    return name + "-" + tag + ".example";
  }

  private Long account(String name, boolean indexable) {
    String uri = "https://" + domain(name) + "/users/" + name;
    exec(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, username,"
            + " domain, indexable, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, ?6, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        name,
        domain(name),
        indexable);
    return id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
  }

  private Long remoteNote(Long account, String visibility) {
    String uri = "https://elsewhere-" + tag + ".example/notes/" + UUID.randomUUID();
    exec(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, ?3, NOW(6), ?4)",
        account,
        uri,
        "about " + tag,
        visibility);
    return id("SELECT id FROM note WHERE uri = ?1", uri);
  }

  private List<Long> found(Long viewerId) {
    return notes.search(null, "%" + tag + "%", viewerId, 0, 50).stream()
        .map(NoteEntity::getId)
        .toList();
  }

  @Test
  void publicNotesFromAccountsThatOptedIntoSearchJoinTheMembersNotes() {
    exec(
        "INSERT INTO note (user_id, body, created_at) VALUES (?1, ?2, NOW(6))",
        writer,
        "about " + tag);
    Long mine = id("SELECT MAX(id) FROM note WHERE user_id = ?1", writer);
    Long open = remoteNote(account("open", true), "PUBLIC");
    remoteNote(account("closed", false), "PUBLIC");
    remoteNote(account("listed", true), "UNLISTED");
    remoteNote(account("followers", true), "PRIVATE");

    assertThat(found(viewer)).containsExactly(open, mine);
    assertThat(found(null)).containsExactly(open, mine);
  }

  @Test
  void serversAndAccountsTheViewerOrModeratorsShutOutStayOut() {
    Long gone = remoteNote(account("gone", true), "PUBLIC");
    Long quiet = remoteNote(account("quiet", true), "PUBLIC");
    Long walled = remoteNote(account("walled", true), "PUBLIC");
    Long blockedAccount = account("blocked", true);
    Long blocked = remoteNote(blockedAccount, "PUBLIC");
    Long mutedAccount = account("muted", true);
    Long muted = remoteNote(mutedAccount, "PUBLIC");
    Long lapsedAccount = account("lapsed", true);
    Long lapsed = remoteNote(lapsedAccount, "PUBLIC");
    exec(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?1, 'SUSPEND', NOW(6)), (?2, 'LIMIT', NOW(6))",
        domain("gone"),
        domain("quiet"));
    exec(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        viewer,
        domain("walled"));
    exec(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?1, ?2, NOW(6))",
        viewer,
        blockedAccount);
    exec(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " expires_at, created_at) VALUES (?1, ?2, FALSE, NULL, NOW(6)),"
            + " (?1, ?3, FALSE, ?4, NOW(6))",
        viewer,
        mutedAccount,
        lapsedAccount,
        Instant.now().minusSeconds(60));

    assertThat(found(viewer)).containsExactly(lapsed);
    assertThat(found(writer)).containsExactly(lapsed, muted, blocked, walled);

    Long quietAccount = id("SELECT remote_actor_id FROM note WHERE id = ?1", quiet);
    exec(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?1, ?2, ?3, NOW(6), NOW(6), NOW(6))",
        writer,
        quietAccount,
        "https://kurl.me/ap/actors/x#follows/" + tag);
    assertThat(found(writer)).containsExactly(lapsed, muted, blocked, walled, quiet);
    assertThat(found(writer)).doesNotContain(gone);
  }
}
