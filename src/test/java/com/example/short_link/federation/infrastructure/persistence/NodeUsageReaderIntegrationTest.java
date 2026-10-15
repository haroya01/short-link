package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.NodeUsage;
import com.example.short_link.federation.domain.repository.NodeUsageReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NodeUsageReaderIntegrationTest {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired private NodeUsageReader reader;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private Long user() {
    String name = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long federatingUser() {
    Long id = user();
    em.createNativeQuery(
            "INSERT INTO federation_actor (user_id, public_id, public_key_pem, private_key_cipher,"
                + " created_at, updated_at) VALUES (?, ?, 'pem', 'cipher', NOW(6), NOW(6))")
        .setParameter(1, id)
        .setParameter(2, "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
        .executeUpdate();
    return id;
  }

  private void update(String set, Long userId) {
    em.createNativeQuery("UPDATE users SET " + set + " WHERE id = ?")
        .setParameter(1, userId)
        .executeUpdate();
  }

  private void suspend(Long userId, Instant until) {
    em.createNativeQuery(
            "UPDATE users SET moderation_status = 'SUSPENDED', suspended_until = ? WHERE id = ?")
        .setParameter(1, until)
        .setParameter(2, userId)
        .executeUpdate();
  }

  private void federation(Long userId, boolean enabled) {
    em.createNativeQuery(
            "INSERT INTO federation_preference (user_id, enabled, created_at, updated_at)"
                + " VALUES (?, ?, NOW(6), NOW(6))")
        .setParameter(1, userId)
        .setParameter(2, enabled)
        .executeUpdate();
  }

  private void note(Long userId, String visibility) {
    em.createNativeQuery(
            "INSERT INTO note (user_id, body, created_at, visibility) VALUES (?, 'x', NOW(6), ?)")
        .setParameter(1, userId)
        .setParameter(2, visibility)
        .executeUpdate();
  }

  private void remoteNote() {
    String uri = "https://r-" + UUID.randomUUID() + ".example/users/a";
    em.createNativeQuery(
            "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
                + " username, domain, fetched_at, created_at, updated_at)"
                + " VALUES (?, ?, 'pem', ?, 'a', 'r.example', NOW(6), NOW(6), NOW(6))")
        .setParameter(1, uri)
        .setParameter(2, uri + "#k")
        .setParameter(3, uri + "/inbox")
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
                + " SELECT id, ?, 'x', NOW(6), 'PUBLIC' FROM federation_remote_actor"
                + " WHERE actor_uri = ?")
        .setParameter(1, uri + "/notes/1")
        .setParameter(2, uri)
        .executeUpdate();
  }

  @Test
  void usersAreLiveFederatingMembersWithAnActorAndPostsAreTheirPublicNotes() {
    NodeUsage before = reader.read(NOW);

    Long active = federatingUser();
    update("deleted_at = NOW(6)", federatingUser());
    suspend(federatingUser(), NOW.plus(Duration.ofDays(1)));
    suspend(federatingUser(), NOW.minus(Duration.ofDays(1)));
    update("moderation_status = 'BANNED'", federatingUser());
    federation(federatingUser(), false);
    federation(federatingUser(), true);
    Long withoutActor = user();

    Long deleted = federatingUser();
    note(active, "PUBLIC");
    note(active, "UNLISTED");
    note(active, "PRIVATE");
    note(withoutActor, "PUBLIC");
    note(deleted, "PUBLIC");
    update("deleted_at = NOW(6)", deleted);
    remoteNote();

    NodeUsage after = reader.read(NOW);
    assertThat(after.users() - before.users()).isEqualTo(3);
    assertThat(after.localPosts() - before.localPosts()).isEqualTo(2);
  }
}
