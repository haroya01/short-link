package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowSuggestionHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String SUGGESTIONS = "/api/v1/users/me/suggestions";

  private void follows(UserEntity follower, UserEntity following) {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, notify_notes, created_at)"
            + " VALUES (?, ?, FALSE, NOW(6))",
        follower.getId(),
        following.getId());
  }

  @Test
  void friendsPicksComeFirstAndASetAsideOneStaysAway() throws Exception {
    UserEntity friend = createUser();
    UserEntity pick = createUser();
    UserEntity other = createUser();
    follows(owner, friend);
    follows(friend, pick);
    follows(other, pick);
    follows(owner, other);

    var picks =
        body(call("suggestions-friends", "GET", SUGGESTIONS + "?limit=40", null, token, 200));
    List<String> names = new ArrayList<>();
    picks.forEach(p -> names.add(p.path("username").asString()));
    assertThat(names).first().isEqualTo(pick.getUsername());
    assertThat(names)
        .doesNotContain(friend.getUsername(), other.getUsername(), owner.getUsername());
    assertThat(picks.get(0).path("mutuals").asLong()).isEqualTo(2);
    assertThat(picks.get(0).path("reason").asString()).isEqualTo("FRIENDS");
    assertThat(picks.get(0).path("locked").asBoolean()).isFalse();
    assertThat(picks.get(0).path("userId").asLong()).isEqualTo(pick.getId());

    call("suggestions-dismiss", "DELETE", SUGGESTIONS + "/" + pick.getUsername(), null, token, 204);
    var after =
        body(call("suggestions-after-dismiss", "GET", SUGGESTIONS + "?limit=40", null, token, 200));
    List<String> remaining = new ArrayList<>();
    after.forEach(p -> remaining.add(p.path("username").asString()));
    assertThat(remaining).doesNotContain(pick.getUsername());
    call("suggestions-dismiss-nobody", "DELETE", SUGGESTIONS + "/nobody-at-all", null, token, 404);
  }
}
