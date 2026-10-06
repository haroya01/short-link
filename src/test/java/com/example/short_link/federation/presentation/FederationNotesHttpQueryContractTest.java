package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FederationNotesHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final Map<String, String> ACTIVITY_JSON =
      Map.of("Accept", "application/activity+json");

  @Autowired private FederationActorService actors;
  @Autowired private FederationUrls urls;

  private String followedByRemote(UserEntity user) {
    actors.byUsername(user.getUsername()).orElseThrow();
    String remote = "https://m" + UUID.randomUUID().toString().substring(0, 8) + ".example";
    String alice = remote + "/users/alice";
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " shared_inbox, username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, 'alice', ?, NOW(6), NOW(6), NOW(6))",
        alice,
        alice + "#main-key",
        KEYS.publicKey(),
        alice + "/inbox",
        remote + "/inbox",
        remote.substring("https://".length()));
    jdbc.update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " created_at, updated_at) SELECT ?, id, ?, NOW(6), NOW(6)"
            + " FROM federation_remote_actor WHERE actor_uri = ?",
        user.getId(),
        remote + "/follows/1",
        alice);
    return remote + "/inbox";
  }

  private List<String> queuedTypes(UserEntity user) {
    return jdbc
        .queryForList(
            "SELECT body FROM federation_delivery WHERE signer_user_id = ? ORDER BY id",
            String.class,
            user.getId())
        .stream()
        .map(body -> json.readTree(body).path("type").asString())
        .toList();
  }

  @Test
  void aWritersNotesReachTheirRemoteFollowersUntilTheyTurnFederationOff() throws Exception {
    String sharedInbox = followedByRemote(owner);

    var settings =
        body(
            call(
                "federation-settings-get", "GET", "/api/v1/federation/settings", null, token, 200));
    assertThat(settings.path("enabled").asBoolean()).isTrue();
    assertThat(settings.path("noticeSeen").asBoolean()).isFalse();
    assertThat(settings.path("handle").asString())
        .isEqualTo("@" + owner.getUsername() + "@" + urls.domain());
    assertThat(
            body(call(
                    "federation-settings-notice",
                    "PUT",
                    "/api/v1/federation/settings",
                    Map.of("noticeSeen", true),
                    token,
                    200))
                .path("noticeSeen")
                .asBoolean())
        .isTrue();

    long noteId =
        body(call(
                "federation-note-create",
                "POST",
                "/api/v1/notes",
                Map.of("body", "hello fediverse https://example.com"),
                token,
                201))
            .path("id")
            .asLong();
    assertThat(queuedTypes(owner)).containsExactly("Create");
    assertThat(
            jdbc.queryForObject(
                "SELECT inbox FROM federation_delivery WHERE signer_user_id = ?",
                String.class,
                owner.getId()))
        .isEqualTo(sharedInbox);

    var document =
        body(
            callWithHeaders(
                "federation-note-document",
                "GET",
                "/ap/notes/" + noteId,
                null,
                ACTIVITY_JSON,
                200));
    assertThat(document.path("id").asString()).isEqualTo(urls.note(noteId));
    assertThat(document.path("content").asString()).contains("<a href=\"https://example.com\"");
    callWithHeaders(
        "federation-note-page",
        "GET",
        "/ap/notes/" + noteId,
        null,
        Map.of("Accept", "text/html"),
        302);

    call(
        "federation-note-edit",
        "PATCH",
        "/api/v1/notes/" + noteId,
        Map.of("body", "edited"),
        token,
        200);
    call("federation-note-delete", "DELETE", "/api/v1/notes/" + noteId, null, token, 204);
    assertThat(queuedTypes(owner)).containsExactly("Create", "Update", "Delete");

    call(
        "federation-settings-off",
        "PUT",
        "/api/v1/federation/settings",
        Map.of("enabled", false),
        token,
        200);
    assertThat(queuedTypes(owner)).containsExactly("Create", "Update", "Delete", "Delete");
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();
    callWithHeaders(
        "federation-webfinger-opted-out",
        "GET",
        "/.well-known/webfinger?resource=acct:" + owner.getUsername() + "@" + urls.domain(),
        null,
        Map.of(),
        404);
    call(
        "federation-settings-on",
        "PUT",
        "/api/v1/federation/settings",
        Map.of("enabled", true),
        token,
        200);
  }

  @Test
  void deletingTheAccountTellsFollowersAtOnce() throws Exception {
    followedByRemote(stranger);

    call(
        "federation-account-delete-leaves", "DELETE", "/api/v1/users/me", null, strangerToken, 204);

    assertThat(queuedTypes(stranger)).containsExactly("Delete");
    assertThat(
            count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", stranger.getId()))
        .isZero();
  }
}
