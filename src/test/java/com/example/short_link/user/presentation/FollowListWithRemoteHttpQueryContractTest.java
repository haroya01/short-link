package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class FollowListWithRemoteHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

  private final String tag = UUID.randomUUID().toString().substring(0, 8);

  private String following() {
    return "/api/v1/users/" + owner.getUsername() + "/following";
  }

  private String domain(String name) {
    return name + "-" + tag + ".example";
  }

  private long actor(String name) {
    String uri = "https://" + domain(name) + "/users/" + name;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        name,
        domain(name));
    return jdbc.queryForObject(
        "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);
  }

  private void localFollow(UserEntity follower, UserEntity following, int minute) {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, ?)",
        follower.getId(),
        following.getId(),
        at(minute));
  }

  private void remoteFollow(
      String table, UserEntity user, long actor, int minute, boolean accepted) {
    jdbc.update(
        "INSERT INTO "
            + table
            + " (user_id, remote_actor_id, follow_activity_id, accepted_at, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
        user.getId(),
        actor,
        "https://kurl.example/follows/" + UUID.randomUUID(),
        accepted ? at(minute) : null,
        at(minute),
        at(minute));
  }

  private static Timestamp at(int minute) {
    return Timestamp.from(T0.plusSeconds(60L * minute));
  }

  private static List<String> names(JsonNode page) {
    List<String> names = new ArrayList<>();
    page.path("items")
        .forEach(
            item ->
                names.add(
                    "remote".equals(item.path("type").asString())
                        ? item.path("account").path("acct").asString()
                        : item.path("username").asString()));
    return names;
  }

  @Test
  void aProfilesFollowsFromHereAndElsewhereListTogether() throws Exception {
    UserEntity a = createUser();
    UserEntity b = createUser();
    UserEntity c = createUser();
    long r1 = actor("r1");
    long r2 = actor("r2");
    long r3 = actor("r3");
    long waiting = actor("waiting");
    long suspended = actor("suspended");
    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'SUSPEND', NOW(6))",
        domain("suspended"));
    remoteFollow("federation_following", owner, r1, 1, true);
    remoteFollow("federation_following", owner, r2, 2, true);
    localFollow(owner, a, 3);
    remoteFollow("federation_following", owner, r3, 4, true);
    localFollow(owner, b, 5);
    localFollow(owner, c, 6);
    remoteFollow("federation_following", owner, waiting, 7, false);
    remoteFollow("federation_following", owner, suspended, 8, true);
    remoteFollow("federation_following", stranger, r2, 1, true);
    localFollow(stranger, b, 1);
    localFollow(a, owner, 1);
    remoteFollow("federation_follower", owner, r1, 2, true);
    remoteFollow("federation_follower", owner, waiting, 3, false);

    JsonNode mixed =
        body(
            call(
                "follow-list-following-with-remote",
                "GET",
                following() + "?include=remote",
                null,
                strangerToken,
                200));
    assertThat(names(mixed))
        .containsExactly(
            c.getUsername(),
            b.getUsername(),
            "r3@" + domain("r3"),
            a.getUsername(),
            "r2@" + domain("r2"),
            "r1@" + domain("r1"));
    assertThat(mixed.path("items").get(1).path("followedByMe").asBoolean()).isTrue();
    assertThat(mixed.path("items").get(4).path("account").path("following").asBoolean()).isTrue();
    assertThat(mixed.path("items").get(5).path("account").path("following").asBoolean()).isFalse();
    assertThat(mixed.path("hasNext").asBoolean()).isFalse();

    JsonNode anonymous =
        body(
            call(
                "follow-list-following-with-remote-anonymous",
                "GET",
                following() + "?include=remote",
                null,
                null,
                200));
    assertThat(anonymous.path("items").get(4).path("account").path("following").asBoolean())
        .isFalse();

    JsonNode crossing =
        body(
            call(
                "follow-list-following-with-remote-crossing-page",
                "GET",
                following() + "?include=remote&page=0&size=4",
                null,
                strangerToken,
                200));
    assertThat(names(crossing))
        .containsExactly(c.getUsername(), b.getUsername(), "r3@" + domain("r3"), a.getUsername());
    assertThat(crossing.path("hasNext").asBoolean()).isTrue();
    JsonNode elsewhereOnly =
        body(
            call(
                "follow-list-following-with-remote-elsewhere-only-page",
                "GET",
                following() + "?include=remote&page=1&size=4",
                null,
                strangerToken,
                200));
    assertThat(names(elsewhereOnly)).containsExactly("r2@" + domain("r2"), "r1@" + domain("r1"));
    assertThat(elsewhereOnly.path("hasNext").asBoolean()).isFalse();

    JsonNode followers =
        body(
            call(
                "follow-list-followers-with-remote",
                "GET",
                "/api/v1/users/" + owner.getUsername() + "/followers?include=remote",
                null,
                strangerToken,
                200));
    assertThat(names(followers)).containsExactly("r1@" + domain("r1"), a.getUsername());

    JsonNode plain =
        body(
            call(
                "follow-list-following-without-include",
                "GET",
                following(),
                null,
                strangerToken,
                200));
    assertThat(plain.path("items")).hasSize(3);
    assertThat(plain.path("items").get(0).has("type")).isFalse();

    JsonNode counts =
        body(
            call(
                "follow-status-with-remote-counts",
                "GET",
                "/api/v1/users/" + owner.getUsername() + "/follow",
                null,
                strangerToken,
                200));
    assertThat(counts.path("followingCount").asLong()).isEqualTo(3);
    assertThat(counts.path("remoteFollowingCount").asLong()).isEqualTo(3);
    assertThat(counts.path("followerCount").asLong()).isEqualTo(1);
    assertThat(counts.path("remoteFollowerCount").asLong()).isEqualTo(1);

    jdbc.update("UPDATE users SET hide_follower_count = TRUE WHERE id = ?", owner.getId());
    call(
        "follow-list-following-with-remote-hidden",
        "GET",
        following() + "?include=remote",
        null,
        strangerToken,
        403);
    assertThat(
            body(call(
                    "follow-list-following-with-remote-own-hidden",
                    "GET",
                    following() + "?include=remote",
                    null,
                    token,
                    200))
                .path("items"))
        .hasSize(6);
  }
}
