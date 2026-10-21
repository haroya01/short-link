package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowListReader.RemoteAccount;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
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
class FollowListWithRemoteIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

  @Autowired private FollowListQueryService lists;
  @Autowired private FollowQueryService status;
  @Autowired private FollowUseCase follows;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private String target;
  private Long targetId;
  private Long viewer;
  private Long ben;
  private Long r1;
  private Long r2;
  private Long r5;

  @BeforeEach
  void aMemberFollowingPeopleHereAndElsewhere() {
    tag = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    target = "t" + tag;
    targetId = user(target);
    viewer = user("v" + tag);
    Long ann = user("a" + tag);
    ben = user("b" + tag);
    Long cat = user("c" + tag);
    r1 = actor("r1");
    r2 = actor("r2");
    Long pending = actor("pending");
    Long suspended = actor("suspended");
    Long blocked = actor("blocked");
    Long onBlockedDomain = actor("domainblocked");
    r5 = actor("r5");

    localFollow(targetId, ann, 1);
    following(targetId, r1, 2, true);
    localFollow(targetId, ben, 3);
    following(targetId, r2, 4, true);
    following(targetId, pending, 5, false);
    following(targetId, suspended, 6, true);
    following(targetId, blocked, 7, true);
    following(targetId, onBlockedDomain, 8, true);
    localFollow(targetId, cat, 9);
    following(targetId, r5, 9, true);

    update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?1, 'SUSPEND', NOW(6))",
        domain("suspended"));
    update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?1, ?2, NOW(6))",
        viewer,
        blocked);
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        viewer,
        domain("domainblocked"));

    localFollow(viewer, ben, 1);
    following(viewer, r2, 1, true);
    following(viewer, r1, 1, false);
    update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " created_at) VALUES (?1, ?2, FALSE, NOW(6))",
        viewer,
        r2);

    localFollow(ann, targetId, 1);
    follower(targetId, r1, 2, true);
    follower(targetId, pending, 3, false);
    follower(targetId, suspended, 4, true);
  }

  @Test
  void followsFromHereAndElsewhereMixNewestFirstWithTiesLocalFirst() {
    MixedFollowListView page = lists.followingWithRemote(null, target, 0, 20);

    assertThat(names(page))
        .containsExactly(
            "c" + tag,
            "r5@" + domain("r5"),
            "domainblocked@" + domain("domainblocked"),
            "blocked@" + domain("blocked"),
            "r2@" + domain("r2"),
            "b" + tag,
            "r1@" + domain("r1"),
            "a" + tag);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void aViewerDoesNotSeeAccountsOrServersTheyBlocked() {
    MixedFollowListView page = lists.followingWithRemote(viewer, target, 0, 20);

    assertThat(names(page))
        .containsExactly(
            "c" + tag,
            "r5@" + domain("r5"),
            "r2@" + domain("r2"),
            "b" + tag,
            "r1@" + domain("r1"),
            "a" + tag);
  }

  @Test
  void aPageBoundaryFallsBetweenFollowsFromHereAndElsewhere() {
    MixedFollowListView first = lists.followingWithRemote(viewer, target, 0, 3);
    MixedFollowListView second = lists.followingWithRemote(viewer, target, 1, 3);
    MixedFollowListView past = lists.followingWithRemote(viewer, target, 2, 3);

    assertThat(names(first)).containsExactly("c" + tag, "r5@" + domain("r5"), "r2@" + domain("r2"));
    assertThat(first.hasNext()).isTrue();
    assertThat(names(second)).containsExactly("b" + tag, "r1@" + domain("r1"), "a" + tag);
    assertThat(second.hasNext()).isFalse();
    assertThat(past.items()).isEmpty();
    assertThat(past.hasNext()).isFalse();
  }

  @Test
  void theFollowButtonsShowTheViewersOwnFollows() {
    List<FollowListItem> items = lists.followingWithRemote(viewer, target, 0, 20).items();

    assertThat(remote(items, r2).following()).isTrue();
    assertThat(remote(items, r2).muting()).isTrue();
    assertThat(remote(items, r1).following()).isFalse();
    assertThat(remote(items, r1).requested()).isTrue();
    assertThat(remote(items, r5).following()).isFalse();
    assertThat(remote(items, r5).requested()).isFalse();
    assertThat(local(items, ben).followedByMe()).isTrue();
  }

  @Test
  void anAnonymousViewerFollowsNoOne() {
    List<FollowListItem> items = lists.followingWithRemote(null, target, 0, 20).items();

    assertThat(items)
        .filteredOn(FollowListItem.Remote.class::isInstance)
        .map(item -> ((FollowListItem.Remote) item).account())
        .allSatisfy(
            account -> {
              assertThat(account.following()).isFalse();
              assertThat(account.requested()).isFalse();
              assertThat(account.muting()).isFalse();
            });
  }

  @Test
  void aRemoteItemCarriesTheAccountAsTheRemoteAccountPageShowsIt() {
    RemoteAccount account = remote(lists.followingWithRemote(null, target, 0, 20).items(), r5);

    assertThat(account.acct()).isEqualTo("r5@" + domain("r5"));
    assertThat(account.username()).isEqualTo("r5");
    assertThat(account.domain()).isEqualTo(domain("r5"));
    assertThat(account.url()).isEqualTo(uri("r5"));
  }

  @Test
  void followersFromElsewhereMixInTheSameWay() {
    MixedFollowListView page = lists.followersWithRemote(viewer, target, 0, 20);

    assertThat(names(page)).containsExactly("r1@" + domain("r1"), "a" + tag);
  }

  @Test
  void theProfileCountsAcceptedFollowsElsewhereLeavingOutSuspendedServers() {
    FollowStatus counts = status.status(viewer, target);

    assertThat(counts.followerCount()).isEqualTo(1);
    assertThat(counts.followingCount()).isEqualTo(3);
    assertThat(counts.remoteFollowerCount()).isEqualTo(1);
    assertThat(counts.remoteFollowingCount()).isEqualTo(5);
  }

  @Test
  void followingAndUnfollowingCountInTheSameTransaction() {
    assertThat(follows.follow(viewer, target, null).followerCount()).isEqualTo(2);
    assertThat(follows.unfollow(viewer, target).followerCount()).isEqualTo(1);
  }

  @Test
  void hiddenCountsHideTheMixedListsAndTheRemoteCountsFromOthers() {
    users.findById(targetId).orElseThrow().updateHideFollowerCount(true);

    assertThatThrownBy(() -> lists.followingWithRemote(viewer, target, 0, 20))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.FOLLOW_LIST_HIDDEN);
    assertThat(lists.followingWithRemote(targetId, target, 0, 20).items()).hasSize(8);
    assertThat(status.status(viewer, target).remoteFollowingCount()).isNull();
    assertThat(status.status(viewer, target).remoteFollowerCount()).isNull();
  }

  private static List<String> names(MixedFollowListView page) {
    return page.items().stream()
        .map(
            item ->
                switch (item) {
                  case FollowListItem.Local local -> local.username();
                  case FollowListItem.Remote remote -> remote.account().acct();
                })
        .toList();
  }

  private static RemoteAccount remote(List<FollowListItem> items, Long id) {
    return items.stream()
        .filter(FollowListItem.Remote.class::isInstance)
        .map(item -> ((FollowListItem.Remote) item).account())
        .filter(account -> account.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static FollowListItem.Local local(List<FollowListItem> items, Long id) {
    return items.stream()
        .filter(FollowListItem.Local.class::isInstance)
        .map(FollowListItem.Local.class::cast)
        .filter(local -> local.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private Long user(String name) {
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private String domain(String username) {
    return username + "-" + tag + ".example";
  }

  private String uri(String username) {
    return "https://" + domain(username) + "/users/" + username;
  }

  private Long actor(String username) {
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, NOW(6), NOW(6), NOW(6))",
        uri(username),
        uri(username) + "#main-key",
        uri(username) + "/inbox",
        username,
        domain(username));
    return id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri(username));
  }

  private void localFollow(Long follower, Long following, int minute) {
    update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?1, ?2, ?3)",
        follower,
        following,
        at(minute));
  }

  private void following(Long userId, Long actorId, int minute, boolean accepted) {
    remoteFollow("federation_following", userId, actorId, minute, accepted);
  }

  private void follower(Long userId, Long actorId, int minute, boolean accepted) {
    remoteFollow("federation_follower", userId, actorId, minute, accepted);
  }

  private void remoteFollow(String table, Long userId, Long actorId, int minute, boolean accepted) {
    update(
        "INSERT INTO "
            + table
            + " (user_id, remote_actor_id, follow_activity_id, created_at, updated_at)"
            + " VALUES (?1, ?2, ?3, ?4, ?4)",
        userId,
        actorId,
        "https://kurl.example/follows/" + UUID.randomUUID(),
        at(minute));
    if (accepted) {
      update(
          "UPDATE " + table + " SET accepted_at = ?1 WHERE user_id = ?2 AND remote_actor_id = ?3",
          at(minute + 1),
          userId,
          actorId);
    }
  }

  private static Timestamp at(int minute) {
    return Timestamp.from(T0.plusSeconds(60L * minute));
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
