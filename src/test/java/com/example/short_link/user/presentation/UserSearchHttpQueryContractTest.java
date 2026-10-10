package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class UserSearchHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String SEARCH = "/api/v1/public/users/search";

  private void named(UserEntity user, String username, String displayName) {
    jdbc.update(
        "UPDATE users SET username = ?, display_name = ? WHERE id = ?",
        username,
        displayName,
        user.getId());
  }

  private static List<String> names(JsonNode view) {
    List<String> names = new ArrayList<>();
    view.path("items").forEach(item -> names.add(item.path("username").asString()));
    return names;
  }

  @Test
  void anyoneFindsPeopleByHandleOrNameAndASignedInViewerSeesFollowsFirstWithoutBlocks()
      throws Exception {
    UserEntity exact = createUser();
    UserEntity longer = createUser();
    UserEntity fan = createUser();
    UserEntity blocked = createUser();
    named(exact, "searchkim", "Kim");
    named(longer, "searchkimlee", null);
    named(fan, "zzfan", "Searchkim fan");
    named(blocked, "searchkimblk", null);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, notify_notes, created_at)"
            + " VALUES (?, ?, FALSE, NOW(6))",
        owner.getId(),
        fan.getId());
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        blocked.getId());

    var anonymous =
        body(
            call("user-search-anonymous", "GET", SEARCH + "?q=@SearchKim&size=2", null, null, 200));
    assertThat(names(anonymous)).containsExactly("searchkim", "searchkimblk");
    assertThat(anonymous.path("hasNext").asBoolean()).isTrue();
    JsonNode first = anonymous.path("items").get(0);
    assertThat(first.path("displayName").asString()).isEqualTo("Kim");
    assertThat(first.path("followerCount").asLong()).isZero();
    assertThat(first.path("following").asBoolean()).isFalse();
    assertThat(first.path("requested").asBoolean()).isFalse();

    var signedIn =
        body(call("user-search-signed-in", "GET", SEARCH + "?q=searchkim", null, token, 200));
    assertThat(names(signedIn)).containsExactly("searchkim", "zzfan", "searchkimlee");
    assertThat(signedIn.path("items").get(1).path("following").asBoolean()).isTrue();
    assertThat(signedIn.path("hasNext").asBoolean()).isFalse();

    var tooShort = body(call("user-search-too-short", "GET", SEARCH + "?q=@s", null, token, 200));
    assertThat(tooShort.path("items")).isEmpty();
    assertThat(tooShort.path("size").asInt()).isEqualTo(20);
  }
}
