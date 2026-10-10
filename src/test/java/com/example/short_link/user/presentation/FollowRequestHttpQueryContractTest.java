package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FollowRequestHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String PROFILE = "/api/v1/users/me/profile";
  private static final String REQUESTS = "/api/v1/users/me/follow-requests";

  private String follow() {
    return "/api/v1/users/" + owner.getUsername() + "/follow";
  }

  private long requests() {
    return count("SELECT COUNT(*) FROM follow_request WHERE following_id = ?", owner.getId());
  }

  private long follows(UserEntity follower) {
    return count(
        "SELECT COUNT(*) FROM user_follow WHERE follower_id = ? AND following_id = ?",
        follower.getId(),
        owner.getId());
  }

  private long requestNotifications() {
    return count(
        "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = 'FOLLOW_REQUEST'",
        owner.getId());
  }

  @Test
  void aLockedMemberApprovesOneAskerTurnsDownAnotherAndUnlockingLetsTheRestIn() throws Exception {
    assertThat(
            body(call("follow-request-lock", "PUT", PROFILE, Map.of("locked", true), token, 200))
                .path("locked")
                .asBoolean())
        .isTrue();

    var asked = body(call("follow-request-ask", "PUT", follow(), null, strangerToken, 200));
    assertThat(asked.path("following").asBoolean()).isFalse();
    assertThat(asked.path("requested").asBoolean()).isTrue();
    assertThat(asked.path("locked").asBoolean()).isTrue();
    assertThat(follows(stranger)).isZero();
    assertThat(requestNotifications()).isEqualTo(1);

    call("follow-request-ask-again", "PUT", follow(), null, strangerToken, 200);
    assertThat(requests()).isEqualTo(1);
    assertThat(requestNotifications()).isEqualTo(1);

    assertThat(
            body(call("follow-request-status", "GET", follow(), null, strangerToken, 200))
                .path("requested")
                .asBoolean())
        .isTrue();

    var waiting = body(call("follow-request-list", "GET", REQUESTS, null, token, 200));
    assertThat(waiting).hasSize(1);
    assertThat(waiting.get(0).path("username").asString()).isEqualTo(stranger.getUsername());
    assertThat(waiting.get(0).path("userId").asLong()).isEqualTo(stranger.getId());

    call(
        "follow-request-authorize",
        "POST",
        REQUESTS + "/" + stranger.getUsername() + "/authorize",
        null,
        token,
        204);
    assertThat(follows(stranger)).isEqualTo(1);
    assertThat(requests()).isZero();
    assertThat(requestNotifications()).isZero();

    call(
        "follow-request-authorize-missing",
        "POST",
        REQUESTS + "/" + stranger.getUsername() + "/authorize",
        null,
        token,
        404);

    UserEntity second = createUser();
    String secondToken = jwt.createAccessToken(second.getId(), "USER");
    call("follow-request-ask-second", "PUT", follow(), null, secondToken, 200);
    call("follow-request-withdraw", "DELETE", follow(), null, secondToken, 200);
    assertThat(requests()).isZero();
    assertThat(requestNotifications()).isZero();

    call("follow-request-ask-again-second", "PUT", follow(), null, secondToken, 200);
    call(
        "follow-request-reject",
        "POST",
        REQUESTS + "/" + second.getUsername() + "/reject",
        null,
        token,
        204);
    assertThat(requests()).isZero();
    assertThat(follows(second)).isZero();
    assertThat(requestNotifications()).isZero();

    UserEntity third = createUser();
    call(
        "follow-request-ask-third",
        "PUT",
        follow(),
        null,
        jwt.createAccessToken(third.getId(), "USER"),
        200);
    call("follow-request-unlock", "PUT", PROFILE, Map.of("locked", false), token, 200);
    assertThat(follows(third)).isEqualTo(1);
    assertThat(requests()).isZero();
    assertThat(requestNotifications()).isZero();
  }
}
