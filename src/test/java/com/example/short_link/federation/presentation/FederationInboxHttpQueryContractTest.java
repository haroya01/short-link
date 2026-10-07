package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.inbox.SignedInboxRequests;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FederationInboxHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();

  @Autowired private FederationUrls urls;
  @Autowired private FederationActorService actors;
  @Autowired private FederationProperties props;

  private String remote;
  private String alice;
  private LocalActor target;

  @BeforeEach
  void seedACachedRemoteActor() {
    remote = "https://m" + UUID.randomUUID().toString().substring(0, 8) + ".example";
    alice = remote + "/users/alice";
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " shared_inbox, username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, 'alice', ?, NOW(6), NOW(6), NOW(6))",
        alice,
        alice + "#main-key",
        KEYS.publicKey(),
        alice + "/inbox",
        remote + "/inbox",
        URI.create(remote).getHost());
    target = actors.byUsername(owner.getUsername()).orElseThrow();
  }

  private Map<String, String> signed(String path, Map<String, Object> activity) {
    byte[] body = json.writeValueAsString(activity).getBytes(StandardCharsets.UTF_8);
    return SignedInboxRequests.headers(
        alice + "#main-key",
        KEYS.privateKey(),
        HttpSignatures.host(URI.create(props.baseUrl())),
        path,
        body,
        Instant.now());
  }

  private Map<String, Object> activity(String id, String type, Object object) {
    Map<String, Object> activity = new LinkedHashMap<>();
    activity.put("@context", "https://www.w3.org/ns/activitystreams");
    activity.put("id", id);
    activity.put("type", type);
    activity.put("actor", alice);
    activity.put("object", object);
    return activity;
  }

  private void post(String id, String path, Map<String, Object> activity, int expected)
      throws Exception {
    callWithHeaders(id, "POST", path, activity, signed(path, activity), expected);
  }

  @Test
  void aMastodonAccountFollowsUnfollowsAndLeaves() throws Exception {
    String me = urls.actor(target.publicId());
    String personal = "/ap/actors/" + target.publicId() + "/inbox";

    post("federation-inbox-follow", personal, activity(remote + "/follows/1", "Follow", me), 202);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_follower f JOIN federation_remote_actor r"
                    + " ON r.id = f.remote_actor_id WHERE f.user_id = ? AND r.actor_uri = ?",
                owner.getId(),
                alice))
        .isEqualTo(1);
    var accept =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?",
                String.class,
                owner.getId(),
                alice + "/inbox"));
    assertThat(accept.path("type").asString()).isEqualTo("Accept");
    assertThat(accept.path("actor").asString()).isEqualTo(me);
    assertThat(accept.path("object").path("id").asString()).isEqualTo(remote + "/follows/1");

    post(
        "federation-inbox-refollow",
        "/ap/inbox",
        activity(remote + "/follows/2", "Follow", me),
        202);
    assertThat(
            jdbc.queryForObject(
                "SELECT GROUP_CONCAT(follow_activity_id) FROM federation_follower WHERE user_id = ?",
                String.class,
                owner.getId()))
        .isEqualTo(remote + "/follows/2");
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_delivery WHERE signer_user_id = ?", owner.getId()))
        .isEqualTo(2);

    Map<String, Object> unsigned = activity(remote + "/follows/3", "Follow", me);
    callWithHeaders("federation-inbox-unsigned", "POST", "/ap/inbox", unsigned, Map.of(), 401);

    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", remote + "/follows/2");
    follow.put("type", "Follow");
    follow.put("actor", alice);
    follow.put("object", me);
    post("federation-inbox-undo", "/ap/inbox", activity(remote + "/undo/2", "Undo", follow), 202);
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();

    post(
        "federation-inbox-ignored",
        "/ap/inbox",
        activity(remote + "/likes/1", "Like", urls.actor(target.publicId()) + "/notes/1"),
        202);

    post(
        "federation-inbox-follow-before-leave",
        personal,
        activity(remote + "/follows/4", "Follow", me),
        202);
    post(
        "federation-inbox-delete-actor",
        "/ap/inbox",
        activity(alice + "#delete", "Delete", alice),
        202);
    assertThat(count("SELECT COUNT(*) FROM federation_remote_actor WHERE actor_uri = ?", alice))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();

    post(
        "federation-inbox-delete-unknown-actor",
        "/ap/inbox",
        activity(alice + "#delete", "Delete", alice),
        202);
  }

  @Test
  void aMemberFollowsAMastodonAccountThatAcceptsThenUnfollows() throws Exception {
    String me = urls.actor(target.publicId());
    String host = URI.create(remote).getHost();
    var found =
        body(
            call(
                "federation-remote-lookup",
                "GET",
                "/api/v1/federation/accounts/lookup?acct=@alice@" + host,
                null,
                token,
                200));
    long id = found.path("id").asLong();
    assertThat(found.path("acct").asString()).isEqualTo("alice@" + host);
    assertThat(found.path("requested").asBoolean()).isFalse();

    var requested =
        body(
            call(
                "federation-remote-follow",
                "POST",
                "/api/v1/federation/accounts/" + id + "/follow",
                null,
                token,
                200));
    assertThat(requested.path("requested").asBoolean()).isTrue();
    String followId =
        jdbc.queryForObject(
            "SELECT follow_activity_id FROM federation_following WHERE user_id = ?",
            String.class,
            owner.getId());
    var follow =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE activity_id = ?",
                String.class,
                followId));
    assertThat(follow.path("type").asString()).isEqualTo("Follow");
    assertThat(follow.path("actor").asString()).isEqualTo(me);
    assertThat(follow.path("object").asString()).isEqualTo(alice);

    Map<String, Object> echoed = new LinkedHashMap<>();
    echoed.put("id", followId);
    echoed.put("type", "Follow");
    echoed.put("actor", me);
    echoed.put("object", alice);
    post(
        "federation-remote-accept",
        "/ap/actors/" + target.publicId() + "/inbox",
        activity(remote + "/accepts/1", "Accept", echoed),
        202);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_following"
                    + " WHERE user_id = ? AND accepted_at IS NOT NULL",
                owner.getId()))
        .isEqualTo(1);

    var listed =
        body(
            call(
                "federation-remote-following",
                "GET",
                "/api/v1/federation/following",
                null,
                token,
                200));
    assertThat(listed.get(0).path("following").asBoolean()).isTrue();
    assertThat(
            body(call(
                    "federation-remote-account",
                    "GET",
                    "/api/v1/federation/accounts/" + id,
                    null,
                    token,
                    200))
                .path("following")
                .asBoolean())
        .isTrue();

    var dropped =
        body(
            call(
                "federation-remote-unfollow",
                "DELETE",
                "/api/v1/federation/accounts/" + id + "/follow",
                null,
                token,
                200));
    assertThat(dropped.path("following").asBoolean()).isFalse();
    assertThat(count("SELECT COUNT(*) FROM federation_following WHERE user_id = ?", owner.getId()))
        .isZero();
    var undo =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE signer_user_id = ?"
                    + " AND activity_id LIKE '%#undo/%'",
                String.class, owner.getId()));
    assertThat(undo.path("object").path("id").asString()).isEqualTo(followId);
  }

  @Test
  void aMastodonAccountVotesInAPollAndTheQuestionCountsItOnce() throws Exception {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, poll_options, poll_expires_at)"
            + " VALUES (?, '점심 어디서?', NOW(6), ?, '2099-01-01')",
        owner.getId(),
        "국밥\n파스타");
    Long noteId =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    String note = urls.note(noteId);

    post("federation-inbox-vote", "/ap/inbox", vote(alice + "#votes/1", "파스타", note), 202);
    post("federation-inbox-vote-again", "/ap/inbox", vote(alice + "#votes/2", "국밥", note), 202);
    assertThat(count("SELECT choices FROM note_poll_remote_vote WHERE note_id = ?", noteId))
        .isEqualTo(2);

    var question =
        body(
            callWithHeaders(
                "federation-note-question",
                "GET",
                "/ap/notes/" + noteId,
                null,
                Map.of("Accept", "application/activity+json"),
                200));
    assertThat(question.path("type").asString()).isEqualTo("Question");
    assertThat(question.path("oneOf").get(1).path("replies").path("totalItems").asLong())
        .isEqualTo(1);
    assertThat(question.path("votersCount").asLong()).isEqualTo(1);
    jdbc.update("DELETE FROM note WHERE id = ?", noteId);
  }

  private Map<String, Object> vote(String id, String option, String question) {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("id", id + "/object");
    object.put("type", "Note");
    object.put("name", option);
    object.put("attributedTo", alice);
    object.put("inReplyTo", question);
    return activity(id, "Create", object);
  }

  @Test
  void aMastodonAccountLikesAndBoostsANoteAndTheAuthorSeesBothCounted() throws Exception {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'hello fediverse', NOW(6))",
        owner.getId());
    Long noteId =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    String note = urls.note(noteId);
    String personal = "/ap/actors/" + target.publicId() + "/inbox";
    String boost = alice + "/statuses/1/activity";

    post("federation-inbox-like", personal, activity(alice + "#likes/1", "Like", note), 202);
    post("federation-inbox-like-again", personal, activity(alice + "#likes/2", "Like", note), 202);
    post("federation-inbox-announce", "/ap/inbox", activity(boost, "Announce", note), 202);
    assertThat(count("SELECT COUNT(*) FROM note_remote_reaction WHERE note_id = ?", noteId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT activity_id FROM note_remote_reaction WHERE note_id = ? AND kind = 'LIKE'",
                String.class,
                noteId))
        .isEqualTo(alice + "#likes/2");

    call(
        "federation-note-member-like",
        "PUT",
        "/api/v1/notes/" + noteId + "/like",
        null,
        strangerToken,
        200);
    var inbox =
        body(call("notification-note-groups", "GET", "/api/v1/notifications", null, token, 200));
    var likes = inbox.path("items").get(0);
    String handle = "alice@" + URI.create(remote).getHost();
    assertThat(likes.path("type").asString()).isEqualTo("NOTE_LIKE");
    assertThat(likes.path("count").asLong()).isEqualTo(2);
    assertThat(likes.path("noteId").asLong()).isEqualTo(noteId);
    assertThat(likes.path("noteExcerpt").asString()).isEqualTo("hello fediverse");
    List<String> likers = new ArrayList<>();
    likes.path("actors").forEach(actor -> likers.add(actor.path("username").asString()));
    assertThat(likers).containsExactly(stranger.getUsername(), handle);
    var boosts = inbox.path("items").get(1);
    assertThat(boosts.path("type").asString()).isEqualTo("NOTE_REPOST");
    assertThat(boosts.path("count").asLong()).isEqualTo(1);
    assertThat(boosts.path("actorUsername").asString()).isEqualTo(handle);
    assertThat(boosts.path("actorProfileUrl").asString()).isEqualTo(alice);
    assertThat(
            count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
        .isEqualTo(3);
    assertThat(
            body(call(
                    "notification-note-unread",
                    "GET",
                    "/api/v1/notifications/unread-count",
                    null,
                    token,
                    200))
                .path("count")
                .asLong())
        .isEqualTo(2);
    call(
        "notification-note-group-read",
        "POST",
        "/api/v1/notifications/" + likes.path("id").asLong() + "/read",
        null,
        token,
        204);
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND read_at IS NULL",
                owner.getId()))
        .isEqualTo(1);

    var mine =
        body(
            call(
                "federation-note-remote-counts",
                "GET",
                "/api/v1/public/notes/" + noteId,
                null,
                token,
                200));
    assertThat(mine.path("note").path("likeCount").asLong()).isEqualTo(2);
    assertThat(mine.path("note").path("repostCount").asLong()).isEqualTo(1);
    var theirs =
        body(
            call(
                "federation-note-remote-counts-stranger",
                "GET",
                "/api/v1/public/notes/" + noteId,
                null,
                strangerToken,
                200));
    assertThat(theirs.path("note").path("likeCount").asLong()).isEqualTo(2);

    Map<String, Object> like = new LinkedHashMap<>();
    like.put("id", alice + "#likes/2");
    like.put("type", "Like");
    like.put("actor", alice);
    like.put("object", note);
    post(
        "federation-inbox-undo-like", "/ap/inbox", activity(remote + "/undo/l", "Undo", like), 202);
    post(
        "federation-inbox-undo-by-id",
        "/ap/inbox",
        activity(remote + "/undo/a", "Undo", boost),
        202);
    assertThat(count("SELECT COUNT(*) FROM note_remote_reaction WHERE note_id = ?", noteId))
        .isZero();

    post(
        "federation-inbox-like-before-delete",
        "/ap/inbox",
        activity(alice + "#likes/3", "Like", note),
        202);
    post(
        "federation-inbox-delete-liker",
        "/ap/inbox",
        activity(alice + "#delete", "Delete", alice),
        202);
    assertThat(count("SELECT COUNT(*) FROM note_remote_reaction WHERE note_id = ?", noteId))
        .isZero();
  }

  @Test
  void aPersonalInboxOfNoOneIs404() throws Exception {
    String path = "/ap/actors/nobody0000/inbox";
    post("federation-inbox-unknown", path, activity(remote + "/f/9", "Follow", "x"), 404);
  }
}
