package com.example.short_link.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.user.SilenceCheck;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainBlockSilenceIntegrationTest {

  @Autowired private MuteRepository mutes;
  @Autowired private NotificationFollowerReader sharers;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private final String tag = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

  private Long member(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private String domain(String username) {
    return username + "-" + tag + ".example";
  }

  private Long account(String username) {
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
    return id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
  }

  private Long noteBy(Long account) {
    String uri = "https://" + domain("notes") + "/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, 'from elsewhere', NOW(6), 'PUBLIC')",
        account,
        uri);
    return id("SELECT id FROM note WHERE uri = ?1", uri);
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

  @Test
  void aMemberWhoBlockedAServerHearsNothingFromItsAccounts() {
    Long blocker = member("b");
    Long listener = member("l");
    Long alice = account("alice");
    Long bob = account("bob");
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        domain("alice"));
    Instant now = Instant.now();

    assertThat(mutes.silences(SilenceCheck.of(blocker, Actor.remote(alice)), now)).isTrue();
    assertThat(mutes.silences(SilenceCheck.of(blocker, Actor.remote(bob)), now)).isFalse();
    assertThat(mutes.silences(SilenceCheck.of(listener, Actor.remote(alice)), now)).isFalse();

    Long edited = noteBy(alice);
    for (Long sharer : new Long[] {blocker, listener}) {
      update(
          "INSERT INTO note_repost (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
          edited,
          sharer);
    }
    assertThat(sharers.noteSharersOf(edited, null, alice)).containsExactly(listener);
  }
}
