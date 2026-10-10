package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class MentionCandidateHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String CANDIDATES = "/api/v1/users/me/mention-candidates";

  private void follows(UserEntity follower, UserEntity following) {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, notify_notes, created_at)"
            + " VALUES (?, ?, FALSE, NOW(6))",
        follower.getId(),
        following.getId());
  }

  private void named(UserEntity user, String username) {
    jdbc.update("UPDATE users SET username = ? WHERE id = ?", username, user.getId());
  }

  private static List<String> names(JsonNode list) {
    List<String> names = new ArrayList<>();
    list.forEach(c -> names.add(c.path("username").asString()));
    return names;
  }

  @Test
  void anAtSignListsFollowsAndLettersPutFollowsFirstWithoutBlockedPeople() throws Exception {
    UserEntity followed = createUser();
    UserEntity stranger = createUser();
    UserEntity blocked = createUser();
    named(followed, "mentionzed");
    named(stranger, "mentionabe");
    named(blocked, "mentionblk");
    follows(owner, followed);
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        blocked.getId(),
        owner.getId());

    var mine =
        body(call("mention-candidates-followed", "GET", CANDIDATES + "?q=@", null, token, 200));
    assertThat(names(mine)).containsExactly("mentionzed");
    assertThat(mine.get(0).path("following").asBoolean()).isTrue();
    assertThat(mine.get(0).path("userId").asLong()).isEqualTo(followed.getId());

    var typed =
        body(call("mention-candidates-prefix", "GET", CANDIDATES + "?q=Mention", null, token, 200));
    assertThat(names(typed)).containsExactly("mentionzed", "mentionabe");
    assertThat(typed.get(1).path("following").asBoolean()).isFalse();
    assertThat(typed.get(1).path("userId").asLong()).isEqualTo(stranger.getId());

    var wild =
        body(call("mention-candidates-wildcard", "GET", CANDIDATES + "?q=%25", null, token, 200));
    assertThat(names(wild)).isEmpty();
  }
}
