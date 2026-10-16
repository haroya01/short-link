package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
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
    return actors.search(viewerId, term, 20, Instant.now()).stream()
        .map(RemoteAccountMatch::id)
        .toList();
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

    List<RemoteAccountMatch> found = actors.search(viewer, tag, 20, Instant.now());
    assertThat(found)
        .extracting(RemoteAccountMatch::id)
        .containsExactly(followed, exact, first, requested);
    assertThat(found.get(0).following()).isTrue();
    assertThat(found.get(0).requested()).isFalse();
    assertThat(found.get(3).following()).isFalse();
    assertThat(found.get(3).requested()).isTrue();
    assertThat(ids(null, tag)).containsExactly(exact, first, requested, followed);
    assertThat(actors.search(viewer, tag, 2, Instant.now())).hasSize(2);
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
  void anAccountTheViewerBlockedStaysOutAndOneTheyMutedSaysSoUntilTheMuteEnds() {
    Long blocked = actor(tag + "a", domain("one"), null);
    Long muted = actor(tag + "b", domain("two"), null);
    Long lapsed = actor(tag + "c", domain("three"), null);
    em.createNativeQuery(
            "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
                + " VALUES (?, ?, NOW(6))")
        .setParameter(1, viewer)
        .setParameter(2, blocked)
        .executeUpdate();
    for (Long actor : List.of(muted, lapsed)) {
      em.createNativeQuery(
              "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
                  + " expires_at, created_at) VALUES (?, ?, TRUE, ?, NOW(6))")
          .setParameter(1, viewer)
          .setParameter(2, actor)
          .setParameter(3, actor.equals(muted) ? null : Instant.now().minusSeconds(60))
          .executeUpdate();
    }

    List<RemoteAccountMatch> found = actors.search(viewer, tag, 20, Instant.now());

    assertThat(found).extracting(RemoteAccountMatch::id).containsExactly(muted, lapsed);
    assertThat(found).extracting(RemoteAccountMatch::muting).containsExactly(true, false);
    assertThat(ids(null, tag)).containsExactly(blocked, muted, lapsed);
  }

  @Test
  void wildcardsInTheTermAreMatchedLiterally() {
    actor(tag + "alice", domain("one"), null);

    assertThat(ids(viewer, tag.substring(0, 4) + "%")).isEmpty();
    assertThat(ids(viewer, tag.substring(0, 4) + "_")).isEmpty();
  }

  private Long member(String prefix) {
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

  private Long discoverable(String username, String domain) {
    Long id = actor(username, domain, null);
    exec("UPDATE federation_remote_actor SET discoverable = TRUE WHERE id = ?", id);
    return id;
  }

  private void follows(Long userId, Long actorId, boolean accepted) {
    exec(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, "
            + (accepted ? "NOW(6)" : "NULL")
            + ", NOW(6), NOW(6))",
        userId,
        actorId,
        "https://kurl.me/ap/actors/x#follows/" + userId + "-" + actorId);
  }

  @Test
  void suggestionsAreAccountsThePeopleIFollowFollowMostFirst() {
    Long first = member("a");
    Long second = member("b");
    Long unknown = member("c");
    for (Long followee : List.of(first, second)) {
      exec(
          "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
          viewer,
          followee);
    }
    Long popular = discoverable("popular", domain("one"));
    Long single = discoverable("single", domain("two"));
    Long lapsedMute = discoverable("lapsed", domain("three"));
    Long quiet = actor("quiet", domain("four"), null);
    Long already = discoverable("already", domain("five"));
    Long asked = discoverable("asked", domain("six"));
    Long blocked = discoverable("blocked", domain("seven"));
    Long muted = discoverable("muted", domain("eight"));
    Long gone = discoverable("gone", "social." + domain("nine"));
    Long limited = discoverable("limited", domain("ten"));
    Long walled = discoverable("walled", domain("eleven"));
    Long strangers = discoverable("strangers", domain("twelve"));
    Long waiting = discoverable("waiting", domain("thirteen"));
    for (Long account :
        List.of(
            popular, single, lapsedMute, quiet, already, asked, blocked, muted, gone, limited)) {
      follows(first, account, true);
    }
    follows(second, popular, true);
    follows(second, quiet, true);
    follows(second, walled, true);
    follows(unknown, strangers, true);
    follows(first, waiting, false);
    follows(viewer, already, true);
    follows(viewer, asked, false);
    exec(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        viewer,
        blocked);
    exec(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " expires_at, created_at) VALUES (?, ?, TRUE, NULL, NOW(6))",
        viewer,
        muted);
    exec(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " expires_at, created_at) VALUES (?, ?, TRUE, ?, NOW(6))",
        viewer,
        lapsedMute,
        Instant.now().minusSeconds(60));
    serverBlock(domain("nine"), "SUSPEND");
    serverBlock(domain("ten"), "LIMIT");
    viewerBlocks(domain("eleven"));

    List<RemoteAccountMatch> found = actors.suggestions(viewer, 20, Instant.now());

    assertThat(found)
        .extracting(RemoteAccountMatch::id)
        .containsExactly(popular, lapsedMute, single);
    assertThat(found.getFirst().username()).isEqualTo("popular");
    assertThat(found.getFirst().domain()).isEqualTo(domain("one"));
    assertThat(actors.suggestions(viewer, 1, Instant.now()))
        .extracting(RemoteAccountMatch::id)
        .containsExactly(popular);
    assertThat(actors.suggestions(unknown, 20, Instant.now())).isEmpty();
  }

  @Test
  void anAccountByItsAddressIsFoundByTheExactNameAndAServerPrefixOnly() {
    Long exact = actor(tag, domain("one"), null);
    actor(tag + "x", domain("one"), null);
    actor(tag, "other.example", null);

    assertThat(ids(viewer, tag + "@one")).containsExactly(exact);
    assertThat(ids(viewer, tag + "@")).hasSize(2);
    assertThat(ids(viewer, tag + "@one%")).isEmpty();
    assertThat(ids(viewer, tag.toUpperCase() + "@ONE-" + tag)).containsExactly(exact);
  }

  @Test
  void aHandleFindsItsAccountByTheServingHostOrByTheDomainThatHostConfirmed() {
    Long served = actor(tag, domain("social"), null);
    em.createNativeQuery("UPDATE federation_remote_actor SET account_domain = ? WHERE id = ?")
        .setParameter(1, domain("home"))
        .setParameter(2, served)
        .executeUpdate();
    Long elsewhere = actor(tag, domain("other"), null);

    assertThat(actors.findByAcct(tag, domain("social")).map(RemoteActorEntity::getId))
        .contains(served);
    assertThat(actors.findByAcct(tag.toUpperCase(), domain("home")).map(RemoteActorEntity::getId))
        .contains(served);
    assertThat(actors.findByAcct(tag, domain("other")).map(RemoteActorEntity::getId))
        .contains(elsewhere);
    assertThat(actors.findByAcct(tag + "x", domain("home"))).isEmpty();
  }
}
