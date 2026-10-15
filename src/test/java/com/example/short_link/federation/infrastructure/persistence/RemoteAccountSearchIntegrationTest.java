package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
class RemoteAccountSearchIntegrationTest {

  @Autowired private RemoteActorRepository actors;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long viewer;

  @BeforeEach
  void viewerAndTag() {
    tag = "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    UserEntity user = new UserEntity(tag + "@example.com", "google", tag);
    user.claimUsername(tag);
    viewer = users.save(user).getId();
  }

  private String domain(String name) {
    return name + "-" + tag + ".example";
  }

  private Long actor(String username, String domain, String displayName) {
    String uri = "https://" + domain + "/users/" + (username == null ? "x" : username);
    em.createNativeQuery(
            "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
                + " username, domain, display_name, fetched_at, created_at, updated_at)"
                + " VALUES (?, ?, 'pem', ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))")
        .setParameter(1, uri)
        .setParameter(2, uri + "#main-key")
        .setParameter(3, uri + "/inbox")
        .setParameter(4, username)
        .setParameter(5, domain)
        .setParameter(6, displayName)
        .executeUpdate();
    return ((Number)
            em.createNativeQuery("SELECT id FROM federation_remote_actor WHERE actor_uri = ?")
                .setParameter(1, uri)
                .getSingleResult())
        .longValue();
  }

  private void follow(Long actorId, boolean accepted) {
    em.createNativeQuery(
            "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
                + " accepted_at, created_at, updated_at)"
                + " VALUES (?, ?, ?, "
                + (accepted ? "NOW(6)" : "NULL")
                + ", NOW(6), NOW(6))")
        .setParameter(1, viewer)
        .setParameter(2, actorId)
        .setParameter(3, "https://kurl.me/ap/actors/x#follows/" + actorId)
        .executeUpdate();
  }

  private void serverBlock(String domain, String severity) {
    em.createNativeQuery(
            "INSERT INTO federation_domain_block (domain, severity, created_at)"
                + " VALUES (?, ?, NOW(6))")
        .setParameter(1, domain)
        .setParameter(2, severity)
        .executeUpdate();
  }

  private void viewerBlocks(String domain) {
    em.createNativeQuery(
            "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, ?, NOW(6))")
        .setParameter(1, viewer)
        .setParameter(2, domain)
        .executeUpdate();
  }

  private List<Long> ids(Long viewerId, String term) {
    return actors.search(viewerId, term, 20).stream().map(RemoteAccountMatch::id).toList();
  }

  @Test
  void aUsernameOrDisplayNameStartingWithTheTermMatchesInAnyCase() {
    Long byName = actor(tag + "alice", domain("one"), "Zed");
    Long byDisplayName = actor("bob", domain("two"), tag.toUpperCase() + " Bob");
    actor("carol", domain("three"), "Carol " + tag);
    actor("dave" + tag, domain("four"), null);

    assertThat(ids(viewer, tag)).containsExactly(byDisplayName, byName);
    assertThat(ids(viewer, tag.toUpperCase())).containsExactly(byDisplayName, byName);
  }

  @Test
  void aTermWithAServerMatchesUserAtDomain() {
    Long one = actor(tag, domain("one"), null);
    actor(tag, domain("two"), null);

    assertThat(ids(viewer, tag + "@one-" + tag.substring(0, 3))).containsExactly(one);
    assertThat(ids(viewer, tag + "@" + domain("one"))).containsExactly(one);
  }

  @Test
  void theViewersFollowsComeFirstThenAnExactNameThenTheRestByName() {
    Long followed = actor(tag + "zz", domain("one"), null);
    Long exact = actor(tag, domain("two"), null);
    Long first = actor(tag + "aa", domain("three"), null);
    Long requested = actor(tag + "bb", domain("four"), null);
    follow(followed, true);
    follow(requested, false);

    List<RemoteAccountMatch> found = actors.search(viewer, tag, 20);
    assertThat(found)
        .extracting(RemoteAccountMatch::id)
        .containsExactly(followed, exact, first, requested);
    assertThat(found.get(0).following()).isTrue();
    assertThat(found.get(0).requested()).isFalse();
    assertThat(found.get(3).following()).isFalse();
    assertThat(found.get(3).requested()).isTrue();
    assertThat(ids(null, tag)).containsExactly(exact, first, requested, followed);
    assertThat(actors.search(viewer, tag, 2)).hasSize(2);
  }

  @Test
  void accountsOnSuspendedServersAndOnServersTheViewerBlockedStayOut() {
    Long open = actor(tag + "a", domain("open"), null);
    actor(tag + "b", "social." + domain("gone"), null);
    Long limited = actor(tag + "c", domain("quiet"), null);
    Long blocked = actor(tag + "d", domain("muted"), null);
    actor(null, domain("open"), tag + " no username");
    serverBlock(domain("gone"), "SUSPEND");
    serverBlock(domain("quiet"), "LIMIT");
    viewerBlocks(domain("muted"));

    assertThat(ids(viewer, tag)).containsExactly(open, limited);
    assertThat(ids(null, tag)).containsExactly(open, limited, blocked);
  }

  @Test
  void wildcardsInTheTermAreMatchedLiterally() {
    actor(tag + "alice", domain("one"), null);

    assertThat(ids(viewer, tag.substring(0, 4) + "%")).isEmpty();
    assertThat(ids(viewer, tag.substring(0, 4) + "_")).isEmpty();
  }
}
