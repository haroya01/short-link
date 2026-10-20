package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.inbox.SignedInboxRequests;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.scheduler.RelayWorker;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.testsupport.FederationFixtures;
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
import tools.jackson.databind.JsonNode;

class FederationInboxHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();

  @Autowired private FederationUrls urls;
  @Autowired private FederationActorService actors;
  @Autowired private FederationProperties props;
  @Autowired private SigningKeys signingKeys;
  @Autowired private RelaySubscriptions relays;
  @Autowired private RelayWorker relayWorker;

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
    signingKeys.forInstance();
  }

  private Map<String, String> signed(String path, Map<String, Object> activity) {
    return signedBy(alice + "#main-key", path, activity);
  }

  private Map<String, String> signedBy(String keyId, String path, Map<String, Object> activity) {
    byte[] body = json.writeValueAsString(activity).getBytes(StandardCharsets.UTF_8);
    return SignedInboxRequests.headers(
        keyId,
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
  void aLockedMemberApprovesAMastodonFollowerThenTurnsTheNextFollowDown() throws Exception {
    jdbc.update("UPDATE users SET locked = TRUE WHERE id = ?", owner.getId());
    String me = urls.actor(target.publicId());
    String personal = "/ap/actors/" + target.publicId() + "/inbox";
    String requests = "/api/v1/federation/follow-requests";

    post(
        "federation-inbox-follow-locked",
        personal,
        activity(remote + "/follows/1", "Follow", me),
        202);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_follower WHERE user_id = ? AND accepted_at IS NULL",
                owner.getId()))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_delivery WHERE signer_user_id = ?", owner.getId()))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?"
                    + " AND type = 'FOLLOW_REQUEST' AND actor_remote_id IS NOT NULL",
                owner.getId()))
        .isEqualTo(1);

    var waiting = body(call("federation-follow-requests", "GET", requests, null, token, 200));
    assertThat(waiting).hasSize(1);
    assertThat(waiting.get(0).path("acct").asString())
        .isEqualTo("alice@" + URI.create(remote).getHost());
    long aliceId = waiting.get(0).path("id").asLong();

    call(
        "federation-follow-request-authorize",
        "POST",
        requests + "/" + aliceId + "/authorize",
        null,
        token,
        204);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_follower WHERE user_id = ? AND accepted_at IS NULL",
                owner.getId()))
        .isZero();
    var accept =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE signer_user_id = ?",
                String.class,
                owner.getId()));
    assertThat(accept.path("type").asString()).isEqualTo("Accept");
    assertThat(accept.path("object").path("id").asString()).isEqualTo(remote + "/follows/1");
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?"
                    + " AND type = 'FOLLOW_REQUEST'",
                owner.getId()))
        .isZero();

    call(
        "federation-follow-request-authorize-missing",
        "POST",
        requests + "/" + aliceId + "/authorize",
        null,
        token,
        404);

    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", remote + "/follows/1");
    follow.put("type", "Follow");
    follow.put("actor", alice);
    follow.put("object", me);
    post(
        "federation-inbox-undo-locked",
        "/ap/inbox",
        activity(remote + "/undo/1", "Undo", follow),
        202);
    post(
        "federation-inbox-follow-locked-again",
        personal,
        activity(remote + "/follows/2", "Follow", me),
        202);
    call(
        "federation-follow-request-reject",
        "POST",
        requests + "/" + aliceId + "/reject",
        null,
        token,
        204);
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();
    assertThat(
            json.readTree(
                    jdbc.queryForObject(
                        "SELECT body FROM federation_delivery WHERE signer_user_id = ?"
                            + " ORDER BY id DESC LIMIT 1",
                        String.class,
                        owner.getId()))
                .path("type")
                .asString())
        .isEqualTo("Reject");

    post(
        "federation-inbox-follow-locked-before-unlock",
        personal,
        activity(remote + "/follows/3", "Follow", me),
        202);
    call(
        "federation-unlock-accepts-waiting",
        "PUT",
        "/api/v1/users/me/profile",
        Map.of("locked", false),
        token,
        200);
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_follower WHERE user_id = ? AND accepted_at IS NOT NULL",
                owner.getId()))
        .isEqualTo(1);
    assertThat(
            json.readTree(
                    jdbc.queryForObject(
                        "SELECT body FROM federation_delivery WHERE signer_user_id = ?"
                            + " ORDER BY id DESC LIMIT 1",
                        String.class,
                        owner.getId()))
                .path("object")
                .path("id")
                .asString())
        .isEqualTo(remote + "/follows/3");
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
  void aFollowedMastodonAccountsNotesReachTheFollowingFeedAndRepliesTheThread() throws Exception {
    String host = URI.create(remote).getHost();
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        urls.actor(target.publicId()) + "#follows/seed");
    String status = alice + "/statuses/1";
    String tag = "cats" + UUID.randomUUID().toString().substring(0, 6);
    Map<String, Object> note = new LinkedHashMap<>();
    note.put("id", status);
    note.put("type", "Note");
    note.put("attributedTo", alice);
    note.put("url", remote + "/@alice/1");
    note.put(
        "content",
        "<p>hello <a href=\""
            + remote
            + "/tags/"
            + tag
            + "\" class=\"mention hashtag\">#<span>"
            + tag
            + "</span></a></p>");
    note.put("published", "2026-10-07T00:00:00Z");
    note.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
    note.put("cc", List.of(alice + "/followers"));
    note.put(
        "attachment",
        List.of(
            Map.of(
                "type", "Document",
                "mediaType", "image/png",
                "url", remote + "/media/1.png",
                "name", "a cat")));
    post(
        "federation-inbox-remote-note",
        "/ap/inbox",
        activity(status + "/activity", "Create", note),
        202);
    Long noteId = jdbc.queryForObject("SELECT id FROM note WHERE uri = ?", Long.class, status);
    assertThat(taggedIds("notes-tag-remote", tag, strangerToken)).containsExactly(noteId);

    Map<String, Object> stray = new LinkedHashMap<>(note);
    stray.put("id", alice + "/statuses/stray");
    stray.put("inReplyTo", remote + "/users/bob/statuses/404");
    stray.put("content", "<p>answering a note this server never saw</p>");
    stray.remove("attachment");
    post(
        "federation-inbox-remote-orphan-reply",
        "/ap/inbox",
        activity(alice + "/statuses/stray/activity", "Create", stray),
        202);
    Long strayId =
        jdbc.queryForObject(
            "SELECT id FROM note WHERE uri = ? AND in_reply_to_id IS NULL AND reply",
            Long.class,
            alice + "/statuses/stray");

    List<Long> federated = new ArrayList<>();
    body(call("notes-federated", "GET", "/api/v1/notes/federated", null, strangerToken, 200))
        .path("items")
        .forEach(item -> federated.add(item.path("id").asLong()));
    assertThat(federated).contains(noteId).doesNotContain(strayId);
    call("notes-federated-anonymous", "GET", "/api/v1/notes/federated", null, null, 401);

    var following =
        body(call("notes-following-remote", "GET", "/api/v1/notes/following", null, token, 200));
    var first = following.path("items").get(0);
    assertThat(first.path("id").asLong()).isEqualTo(noteId);
    assertThat(first.path("body").asString()).isEqualTo("hello #" + tag);
    assertThat(first.path("author").path("username").asString()).isEqualTo("alice@" + host);
    assertThat(first.path("author").path("remoteId").asLong()).isEqualTo(aliceId);
    assertThat(first.path("media").get(0).path("altText").asString()).isEqualTo("a cat");
    assertThat(
            body(call(
                    "federation-remote-notes",
                    "GET",
                    "/api/v1/federation/accounts/" + aliceId + "/notes",
                    null,
                    token,
                    200))
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(noteId);
    assertThat(
            body(
                    call(
                        "notes-everyone-without-remote",
                        "GET",
                        "/api/v1/public/notes",
                        null,
                        null,
                        200))
                .path("items")
                .findValues("id")
                .stream()
                .map(id -> id.asLong())
                .toList())
        .doesNotContain(noteId);

    call(
        "federation-remote-note-report",
        "POST",
        "/api/v1/public/abuse-reports",
        Map.of(
            "subjectType", "NOTE",
            "subjectId", noteId,
            "reasonCode", "SPAM",
            "detail", "광고 계정",
            "forward", true),
        token,
        202);
    var flag =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE signer_user_id IS NULL"
                    + " AND inbox = ? AND activity_id LIKE '%#flags/%'",
                String.class, remote + "/inbox"));
    assertThat(flag.path("type").asString()).isEqualTo("Flag");
    assertThat(flag.path("object").get(1).asString()).isEqualTo(status);

    jdbc.update("UPDATE note SET created_at = NOW(6) WHERE id = ?", noteId);
    assertThat(trendingIds("notes-trending-remote-untouched")).doesNotContain(noteId);

    String aliceInbox = remote + "/inbox";
    call(
        "federation-remote-note-like",
        "PUT",
        "/api/v1/notes/" + noteId + "/like",
        null,
        token,
        200);
    call(
        "federation-remote-note-repost",
        "PUT",
        "/api/v1/notes/" + noteId + "/repost",
        null,
        token,
        200);
    var answer =
        body(
            call(
                "federation-remote-note-reply",
                "POST",
                "/api/v1/notes",
                Map.of("body", "replying from kurl", "inReplyToId", noteId),
                token,
                201));
    List<String> sent =
        jdbc.queryForList(
            "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?"
                + " ORDER BY id",
            String.class,
            owner.getId(),
            aliceInbox);
    assertThat(sent).hasSize(3);
    assertThat(json.readTree(sent.get(0)).path("type").asString()).isEqualTo("Like");
    assertThat(json.readTree(sent.get(0)).path("object").asString()).isEqualTo(status);
    assertThat(json.readTree(sent.get(1)).path("type").asString()).isEqualTo("Announce");
    var created = json.readTree(sent.get(2));
    assertThat(created.path("type").asString()).isEqualTo("Create");
    assertThat(created.path("object").path("inReplyTo").asString()).isEqualTo(status);
    assertThat(trendingIds("notes-trending-remote")).contains(noteId);
    long answerId = answer.path("id").asLong();
    call(
        "federation-remote-reply-edit",
        "PATCH",
        "/api/v1/notes/" + answerId,
        Map.of("body", "replying from kurl, edited"),
        token,
        200);
    call("federation-remote-reply-delete", "DELETE", "/api/v1/notes/" + answerId, null, token, 204);
    List<String> after =
        jdbc.queryForList(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(body, '$.type')) FROM federation_delivery"
                + " WHERE signer_user_id = ? AND inbox = ? ORDER BY id",
            String.class,
            owner.getId(),
            aliceInbox);
    assertThat(after).containsExactly("Like", "Announce", "Create", "Update", "Delete");

    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'a question', NOW(6))",
        owner.getId());
    Long mine =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    Map<String, Object> reply = new LinkedHashMap<>(note);
    reply.put("id", alice + "/statuses/2");
    reply.put("inReplyTo", urls.note(mine));
    reply.put("content", "<p>an answer</p>");
    reply.remove("attachment");
    post(
        "federation-inbox-remote-reply",
        "/ap/inbox",
        activity(alice + "/statuses/2/activity", "Create", reply),
        202);
    var thread =
        body(
            call(
                "notes-thread-remote-reply",
                "GET",
                "/api/v1/public/notes/" + mine,
                null,
                null,
                200));
    assertThat(thread.path("replies").get(0).path("body").asString()).isEqualTo("an answer");
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = 'NOTE_REPLY'",
                owner.getId()))
        .isEqualTo(1);

    Map<String, Object> edited = new LinkedHashMap<>(note);
    edited.put("content", "<p>hello again</p>");
    edited.put("contentMap", Map.of("ja", "<p>hello again</p>"));
    edited.put("updated", "2026-10-07T01:00:00Z");
    edited.put(
        "attachment",
        List.of(
            Map.of(
                "type", "Document",
                "mediaType", "image/png",
                "url", remote + "/media/1.png",
                "name", "a sleeping cat")));
    post(
        "federation-inbox-remote-update",
        "/ap/inbox",
        activity(status + "#updates/1", "Update", edited),
        202);
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, noteId))
        .isEqualTo("hello again");
    assertThat(jdbc.queryForObject("SELECT language FROM note WHERE id = ?", String.class, noteId))
        .isEqualTo("ja");
    assertThat(
            jdbc.queryForObject(
                "SELECT alt_text FROM note_media WHERE note_id = ?", String.class, noteId))
        .isEqualTo("a sleeping cat");
    assertThat(count("SELECT COUNT(*) FROM note_tag WHERE note_id = ?", noteId)).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?"
                    + " AND type = 'NOTE_EDIT' AND actor_remote_id = ?",
                owner.getId(),
                aliceId))
        .isEqualTo(1);

    post(
        "federation-inbox-remote-update-again",
        "/ap/inbox",
        activity(status + "#updates/2", "Update", edited),
        202);
    Map<String, Object> stale = new LinkedHashMap<>(edited);
    stale.put("content", "<p>an older draft</p>");
    stale.remove("contentMap");
    stale.put("updated", "2026-10-07T00:30:00Z");
    post(
        "federation-inbox-remote-update-stale",
        "/ap/inbox",
        activity(status + "#updates/0", "Update", stale),
        202);
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, noteId))
        .isEqualTo("hello again");
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?"
                    + " AND type = 'NOTE_EDIT' AND actor_remote_id = ?",
                owner.getId(),
                aliceId))
        .isEqualTo(1);

    post(
        "federation-inbox-remote-delete",
        "/ap/inbox",
        activity(status + "#delete", "Delete", Map.of("id", status, "type", "Tombstone")),
        202);
    assertThat(count("SELECT COUNT(*) FROM note WHERE id = ?", noteId)).isZero();

    String late = alice + "/statuses/4";
    post(
        "federation-inbox-delete-before-create",
        "/ap/inbox",
        activity(late + "#delete", "Delete", Map.of("id", late, "type", "Tombstone")),
        202);
    Map<String, Object> deleted = new LinkedHashMap<>(note);
    deleted.put("id", late);
    deleted.remove("attachment");
    post(
        "federation-inbox-create-after-delete",
        "/ap/inbox",
        activity(late + "/activity", "Create", deleted),
        202);
    assertThat(count("SELECT COUNT(*) FROM note WHERE uri = ?", late)).isZero();

    jdbc.update("DELETE FROM federation_following WHERE user_id = ?", owner.getId());
    Map<String, Object> unasked = new LinkedHashMap<>(note);
    unasked.put("id", alice + "/statuses/3");
    post(
        "federation-inbox-remote-unsolicited",
        "/ap/inbox",
        activity(alice + "/statuses/3/activity", "Create", unasked),
        202);
    assertThat(count("SELECT COUNT(*) FROM note WHERE uri = ?", alice + "/statuses/3")).isZero();
    jdbc.update("DELETE FROM note WHERE remote_actor_id = ?", aliceId);
    jdbc.update("DELETE FROM note WHERE id = ?", mine);
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

  @Test
  void aFollowedAccountsPollAndArticleArriveAsWhatTheyWereThere() throws Exception {
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        urls.actor(target.publicId()) + "#follows/shapes");

    JsonNode question =
        json.readTree(
            FederationFixtures.text("mastodon-question.json")
                .replace("https://mastodon.example", remote));
    post(
        "federation-inbox-remote-question",
        "/ap/inbox",
        activity(question.path("id").asString() + "/activity", "Create", question),
        202);

    Map<String, Object> article = new LinkedHashMap<>();
    article.put("id", remote + "/articles/1");
    article.put("type", "Article");
    article.put("attributedTo", alice);
    article.put("name", "What the garden taught me");
    article.put("summary", "A season of tomatoes &amp; patience.");
    article.put("content", "<p>" + "A long article. ".repeat(100) + "</p>");
    article.put("url", remote + "/@alice/articles/1");
    article.put("published", "2026-10-01T12:00:00Z");
    article.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
    article.put(
        "attachment",
        List.of(
            Map.of(
                "type", "Image",
                "mediaType", "image/jpeg",
                "url", remote + "/media/tomatoes.jpg",
                "name", "Tomatoes")));
    post(
        "federation-inbox-remote-article",
        "/ap/inbox",
        activity(remote + "/articles/1/activity", "Create", article),
        202);

    var feed =
        body(
            call(
                "notes-following-remote-shapes",
                "GET",
                "/api/v1/notes/following",
                null,
                token,
                200));
    var poll = feed.path("items").get(0);
    assertThat(poll.path("remote").path("kind").asString()).isEqualTo("QUESTION");
    assertThat(poll.path("poll").path("votersCount").asLong()).isEqualTo(12);
    assertThat(poll.path("poll").path("options").get(0).path("votesCount").asLong()).isEqualTo(7);
    assertThat(poll.path("poll").path("voted").asBoolean()).isTrue();
    var card = feed.path("items").get(1);
    assertThat(card.path("remote").path("kind").asString()).isEqualTo("ARTICLE");
    assertThat(card.path("remote").path("title").asString()).isEqualTo("What the garden taught me");
    assertThat(card.path("remote").path("url").asString()).isEqualTo(remote + "/@alice/articles/1");
    assertThat(card.path("body").asString()).isEqualTo("A season of tomatoes & patience.");
    assertThat(card.path("media").get(0).path("url").asString())
        .isEqualTo(remote + "/media/tomatoes.jpg");
    jdbc.update("DELETE FROM note WHERE remote_actor_id = ?", aliceId);
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
    assertThat(boosts.path("actorRemoteId").asLong())
        .isEqualTo(count("SELECT id FROM federation_remote_actor WHERE actor_uri = ?", alice));
    assertThat(likes.path("actorRemoteId").isNull()).isTrue();
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
  void aFollowersOnlyOrDirectNoteTakesLikesBoostsAndRepliesOnlyFromThoseWhoMayReadIt()
      throws Exception {
    String me = urls.actor(target.publicId());
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility)"
            + " VALUES (?, 'for followers', NOW(6), 'PRIVATE')",
        owner.getId());
    Long forFollowers =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility)"
            + " VALUES (?, 'just us', NOW(6), 'DIRECT')",
        owner.getId());
    Long direct =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());

    post(
        "federation-inbox-like-followers-only-stranger",
        "/ap/inbox",
        activity(alice + "#likes/1", "Like", urls.note(forFollowers)),
        202);
    post(
        "federation-inbox-announce-followers-only",
        "/ap/inbox",
        activity(alice + "/statuses/1/activity", "Announce", urls.note(forFollowers)),
        202);
    assertThat(count("SELECT COUNT(*) FROM note_remote_reaction WHERE note_id = ?", forFollowers))
        .isZero();

    Map<String, Object> reply = new LinkedHashMap<>();
    reply.put("id", alice + "/statuses/2");
    reply.put("type", "Note");
    reply.put("attributedTo", alice);
    reply.put("inReplyTo", urls.note(direct));
    reply.put("content", "<p>what did I miss?</p>");
    reply.put("published", "2026-10-07T00:00:00Z");
    reply.put("to", List.of(me));
    reply.put("tag", List.of(Map.of("type", "Mention", "href", me)));
    post(
        "federation-inbox-reply-direct-unseen",
        "/ap/inbox",
        activity(alice + "/statuses/2/activity", "Create", reply),
        202);
    assertThat(
            count(
                "SELECT COUNT(*) FROM note WHERE uri = ? AND in_reply_to_id IS NULL AND reply",
                alice + "/statuses/2"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = 'NOTE_REPLY'",
                owner.getId()))
        .isZero();

    jdbc.update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) SELECT ?, id, ?, NOW(6), NOW(6), NOW(6)"
            + " FROM federation_remote_actor WHERE actor_uri = ?",
        owner.getId(),
        remote + "/follows/7",
        alice);
    post(
        "federation-inbox-like-followers-only-follower",
        "/ap/inbox",
        activity(alice + "#likes/2", "Like", urls.note(forFollowers)),
        202);
    assertThat(count("SELECT COUNT(*) FROM note_remote_reaction WHERE note_id = ?", forFollowers))
        .isEqualTo(1);
    jdbc.update("DELETE FROM note WHERE uri = ?", alice + "/statuses/2");
    jdbc.update("DELETE FROM note WHERE id IN (?, ?)", forFollowers, direct);
  }

  @Test
  void aMemberBlocksAServerAndEverythingFromItGoes() throws Exception {
    String me = urls.actor(target.publicId());
    String host = URI.create(remote).getHost();
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    jdbc.update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        remote + "/follows/9");
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        me + "#follows/9");
    jdbc.update(
        "INSERT INTO notification (recipient_user_id, type, actor_remote_id, created_at)"
            + " VALUES (?, 'REMOTE_FOLLOW', ?, NOW(6))",
        owner.getId(),
        aliceId);

    var blocked =
        body(
            call(
                "federation-domain-block",
                "PUT",
                "/api/v1/federation/domain-blocks/" + host.toUpperCase(),
                null,
                token,
                200));
    assertThat(blocked.path("domain").asString()).isEqualTo(host);
    assertThat(count("SELECT COUNT(*) FROM federation_following WHERE user_id = ?", owner.getId()))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(body, '$.type')) FROM federation_delivery"
                    + " WHERE signer_user_id = ? AND inbox = ? ORDER BY id",
                String.class,
                owner.getId(),
                alice + "/inbox"))
        .containsExactly("Undo", "Reject");
    assertThat(
            count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
        .isZero();

    assertThat(
            body(call(
                    "federation-domain-blocks",
                    "GET",
                    "/api/v1/federation/domain-blocks",
                    null,
                    token,
                    200))
                .get(0)
                .path("domain")
                .asString())
        .isEqualTo(host);
    assertThat(
            body(call(
                    "federation-domain-blocked-account",
                    "GET",
                    "/api/v1/federation/accounts/" + aliceId,
                    null,
                    token,
                    200))
                .path("domainBlocked")
                .asBoolean())
        .isTrue();
    call(
        "federation-domain-blocked-follow",
        "POST",
        "/api/v1/federation/accounts/" + aliceId + "/follow",
        null,
        token,
        409);

    post(
        "federation-inbox-follow-blocked-domain",
        "/ap/actors/" + target.publicId() + "/inbox",
        activity(remote + "/follows/10", "Follow", me),
        202);
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_delivery WHERE signer_user_id = ?"
                    + " AND body LIKE '%\"Reject\"%'",
                owner.getId()))
        .isEqualTo(2);

    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'from kurl', NOW(6))",
        owner.getId());
    Long mine =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    Map<String, Object> reply = new LinkedHashMap<>();
    reply.put("id", alice + "/statuses/90");
    reply.put("type", "Note");
    reply.put("attributedTo", alice);
    reply.put("inReplyTo", urls.note(mine));
    reply.put("content", "<p>from a blocked server</p>");
    reply.put("published", "2026-10-07T00:00:00Z");
    reply.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
    post(
        "federation-inbox-reply-blocked-domain",
        "/ap/inbox",
        activity(alice + "/statuses/90/activity", "Create", reply),
        202);
    assertThat(
            count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
        .isZero();
    assertThat(
            body(call(
                    "notes-thread-blocked-domain",
                    "GET",
                    "/api/v1/public/notes/" + mine,
                    null,
                    token,
                    200))
                .path("replies"))
        .isEmpty();

    call(
        "federation-domain-unblock",
        "DELETE",
        "/api/v1/federation/domain-blocks/" + host,
        null,
        token,
        204);
    assertThat(count("SELECT COUNT(*) FROM user_domain_block WHERE user_id = ?", owner.getId()))
        .isZero();
  }

  private Map<String, Object> relayed(String relay, String id, String type, Object object) {
    Map<String, Object> activity = new LinkedHashMap<>();
    activity.put("@context", "https://www.w3.org/ns/activitystreams");
    activity.put("id", id);
    activity.put("type", type);
    activity.put("actor", relay);
    activity.put("object", object);
    return activity;
  }

  @Test
  void aMemberRepostsTakesBackAndQuotesANoteFromElsewhere() throws Exception {
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    String status = alice + "/statuses/77";
    String page = remote + "/@alice/77";
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, remote_url, body, created_at, visibility)"
            + " VALUES (?, ?, ?, 'worth sharing', NOW(6), 'PUBLIC')",
        aliceId,
        status,
        page);
    Long noteId = jdbc.queryForObject("SELECT id FROM note WHERE uri = ?", Long.class, status);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        stranger.getId(),
        owner.getId());
    String inbox = remote + "/inbox";

    var reposted =
        body(
            call(
                "federation-remote-share-repost",
                "PUT",
                "/api/v1/notes/" + noteId + "/repost",
                null,
                token,
                200));
    assertThat(reposted.path("reposted").asBoolean()).isTrue();
    assertThat(reposted.path("repostCount").asLong()).isEqualTo(1);
    var first =
        body(call(
                "federation-remote-share-following",
                "GET",
                "/api/v1/notes/following",
                null,
                strangerToken,
                200))
            .path("items")
            .get(0);
    assertThat(first.path("id").asLong()).isEqualTo(noteId);
    assertThat(first.path("repostedBy").path("username").asString()).isEqualTo(owner.getUsername());
    assertThat(first.path("author").path("remoteId").asLong()).isEqualTo(aliceId);
    assertThat(first.path("repostCount").asLong()).isEqualTo(1);
    assertThat(
            body(call(
                    "federation-remote-share-profile-reposts",
                    "GET",
                    "/api/v1/public/profiles/" + owner.getUsername() + "/reposts",
                    null,
                    null,
                    200))
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(noteId);

    var undone =
        body(
            call(
                "federation-remote-share-unrepost",
                "DELETE",
                "/api/v1/notes/" + noteId + "/repost",
                null,
                token,
                200));
    assertThat(undone.path("reposted").asBoolean()).isFalse();
    assertThat(undone.path("repostCount").asLong()).isZero();
    List<String> shared =
        jdbc.queryForList(
            "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?"
                + " ORDER BY id",
            String.class,
            owner.getId(),
            inbox);
    assertThat(shared).hasSize(2);
    var announce = json.readTree(shared.get(0));
    assertThat(announce.path("type").asString()).isEqualTo("Announce");
    assertThat(announce.path("object").asString()).isEqualTo(status);
    assertThat(announce.path("cc").toString()).contains(alice);
    var undo = json.readTree(shared.get(1));
    assertThat(undo.path("type").asString()).isEqualTo("Undo");
    assertThat(undo.path("object").path("id").asString()).isEqualTo(announce.path("id").asString());
    assertThat(undo.path("object").path("object").asString()).isEqualTo(status);
    assertThat(
            body(
                    call(
                        "federation-remote-share-following-undone",
                        "GET",
                        "/api/v1/notes/following",
                        null,
                        strangerToken,
                        200))
                .path("items")
                .findValues("id")
                .stream()
                .map(JsonNode::asLong)
                .toList())
        .doesNotContain(noteId);

    var quote =
        body(
            call(
                "federation-remote-share-quote",
                "POST",
                "/api/v1/notes",
                Map.of("body", "this, from elsewhere", "quotedNoteId", noteId),
                token,
                201));
    long quoteId = quote.path("id").asLong();
    assertThat(quote.path("quotedNote").path("id").asLong()).isEqualTo(noteId);
    assertThat(quote.path("quotedNote").path("author").path("remoteId").asLong())
        .isEqualTo(aliceId);
    var created =
        json.readTree(
            jdbc.queryForObject(
                "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?"
                    + " AND activity_id = ?",
                String.class,
                owner.getId(),
                inbox,
                urls.note(quoteId) + "/activity"));
    assertThat(created.path("object").path("quoteUri").asString()).isEqualTo(status);
    assertThat(created.path("object").path("_misskey_quote").asString()).isEqualTo(status);
    assertThat(created.path("object").path("content").asString())
        .contains("RE: <a href=\"" + page + "\">");
    assertThat(created.path("cc").toString()).contains(alice);
    assertThat(created.path("object").path("tag").size()).isZero();
    assertThat(
            body(call(
                    "federation-remote-share-quotes",
                    "GET",
                    "/api/v1/public/notes/" + noteId + "/quotes",
                    null,
                    null,
                    200))
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(quoteId);

    call(
        "federation-remote-share-quote-delete",
        "DELETE",
        "/api/v1/notes/" + quoteId,
        null,
        token,
        204);
    assertThat(
            jdbc.queryForList(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(body, '$.type')) FROM federation_delivery"
                    + " WHERE signer_user_id = ? AND inbox = ? ORDER BY id",
                String.class,
                owner.getId(),
                inbox))
        .containsExactly("Announce", "Undo", "Create", "Delete");
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE actor_user_id = ?"
                    + " AND type IN ('NOTE_REPOST', 'NOTE_QUOTE')",
                owner.getId()))
        .isZero();
  }

  @Test
  void anAdminSubscribesToARelayThatAcceptsAndAnnouncesUntilUnsubscribed() throws Exception {
    String admin = jwt.createAccessToken(createUser().getId(), "ADMIN");
    String relay = remote + "/tag/dev";
    String post = alice + "/statuses/5";
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " shared_inbox, domain, display_name, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, '#dev', NOW(6), NOW(6), NOW(6))",
        relay,
        relay + "#key",
        KEYS.publicKey(),
        relay,
        remote + "/inbox",
        URI.create(remote).getHost());
    try {
      var subscribed =
          body(
              call(
                  "admin-relay-subscribe",
                  "POST",
                  "/api/v1/admin/federation/relays",
                  Map.of("actorUri", relay),
                  admin,
                  200));
      assertThat(subscribed.path("state").asString()).isEqualTo("PENDING");
      assertThat(subscribed.path("label").asString()).isEqualTo("#dev");
      String followId =
          jdbc.queryForObject(
              "SELECT follow_activity_id FROM federation_relay WHERE actor_uri = ?",
              String.class,
              relay);
      var follow =
          json.readTree(
              jdbc.queryForObject(
                  "SELECT body FROM federation_delivery WHERE activity_id = ?"
                      + " AND signer_user_id IS NULL AND inbox = ?",
                  String.class,
                  followId,
                  relay));
      assertThat(follow.path("actor").asString()).isEqualTo(urls.instance());
      assertThat(follow.path("object").asString())
          .isEqualTo("https://www.w3.org/ns/activitystreams#Public");

      Map<String, Object> ours = new LinkedHashMap<>();
      ours.put("id", followId);
      ours.put("type", "Follow");
      ours.put("actor", urls.instance());
      ours.put("object", "https://www.w3.org/ns/activitystreams#Public");
      Map<String, Object> accept = relayed(relay, remote + "/accept/1", "Accept", ours);
      callWithHeaders(
          "federation-inbox-relay-accept",
          "POST",
          "/ap/inbox",
          accept,
          signedBy(relay + "#key", "/ap/inbox", accept),
          202);
      Map<String, Object> announce = relayed(relay, remote + "/announce/1", "Announce", post);
      callWithHeaders(
          "federation-inbox-relay-announce",
          "POST",
          "/ap/inbox",
          announce,
          signedBy(relay + "#key", "/ap/inbox", announce),
          202);

      var status =
          body(call("admin-relays", "GET", "/api/v1/admin/federation/relays", null, admin, 200));
      assertThat(status.path("enabled").asBoolean()).isTrue();
      assertThat(status.path("mode").asString()).isEqualTo("COUNT");
      assertThat(status.path("queued").asInt()).isEqualTo(1);
      assertThat(status.path("today").path("queued").asLong()).isEqualTo(1);
      assertThat(status.path("relays").get(0).path("state").asString()).isEqualTo("ACCEPTED");
      call("admin-relays-member", "GET", "/api/v1/admin/federation/relays", null, token, 403);

      captureBackgroundWork(
          "federation-relay-worker", () -> assertThat(relayWorker.processNext()).isTrue());
      var counted =
          body(
              call(
                  "admin-relays-after-work",
                  "GET",
                  "/api/v1/admin/federation/relays",
                  null,
                  admin,
                  200));
      assertThat(counted.path("queued").asInt()).isZero();
      assertThat(counted.path("today").path("unanswered").asLong()).isEqualTo(1);

      var off =
          body(
              call(
                  "admin-relay-setting",
                  "PUT",
                  "/api/v1/admin/federation/relays/setting",
                  Map.of("mode", "OFF", "languages", List.of("ko", "ja", "en")),
                  admin,
                  200));
      assertThat(off.path("mode").asString()).isEqualTo("OFF");
      assertThat(off.path("languages").size()).isEqualTo(3);

      call(
          "admin-relay-unsubscribe",
          "DELETE",
          "/api/v1/admin/federation/relays/" + subscribed.path("id").asLong(),
          null,
          admin,
          204);
      assertThat(count("SELECT COUNT(*) FROM federation_relay WHERE actor_uri = ?", relay))
          .isZero();
      assertThat(
              json.readTree(
                      jdbc.queryForObject(
                          "SELECT body FROM federation_delivery WHERE activity_id = ?",
                          String.class,
                          followId + "/undo"))
                  .path("type")
                  .asString())
          .isEqualTo("Undo");
      Map<String, Object> late = relayed(relay, remote + "/announce/2", "Announce", post);
      callWithHeaders(
          "federation-inbox-relay-unsubscribed",
          "POST",
          "/ap/inbox",
          late,
          signedBy(relay + "#key", "/ap/inbox", late),
          202);
    } finally {
      relays.change(RelayMode.COUNT, List.of("ko", "ja"));
      jdbc.update("DELETE FROM federation_relay WHERE actor_uri = ?", relay);
      jdbc.update("DELETE FROM federation_delivery WHERE inbox = ?", relay);
      jdbc.update("DELETE FROM federation_remote_actor WHERE actor_uri = ?", relay);
    }
  }

  @Test
  void moderatorsLimitThenSuspendAServer() throws Exception {
    String me = urls.actor(target.publicId());
    String host = URI.create(remote).getHost();
    String admin = jwt.createAccessToken(createUser().getId(), "ADMIN");
    String tag = "srv" + UUID.randomUUID().toString().substring(0, 8);
    String shared = "list" + UUID.randomUUID().toString().substring(0, 8);
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    jdbc.update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        remote + "/follows/9");
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        me + "#follows/9");
    jdbc.update(
        "INSERT INTO notification (recipient_user_id, type, actor_remote_id, created_at)"
            + " VALUES (?, 'REMOTE_FOLLOW', ?, NOW(6))",
        owner.getId(),
        aliceId);
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, marked_sensitive, visibility,"
            + " poll_multiple) VALUES (?, ?, ?, NOW(6), FALSE, 'PUBLIC', FALSE)",
        aliceId,
        alice + "/statuses/77",
        "#" + tag);
    Long theirs =
        jdbc.queryForObject(
            "SELECT id FROM note WHERE uri = ?", Long.class, alice + "/statuses/77");
    jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, ?)", theirs, tag);
    try {
      var limited =
          body(
              call(
                  "admin-server-limit",
                  "PUT",
                  "/api/v1/admin/federation/servers/" + host,
                  Map.of("severity", "LIMIT", "reason", "spam"),
                  admin,
                  200));
      assertThat(limited.path("severity").asString()).isEqualTo("LIMIT");
      assertThat(taggedIds("notes-tag-limited-server-stranger", tag, strangerToken))
          .doesNotContain(theirs);
      assertThat(taggedIds("notes-tag-limited-server-follower", tag, token)).contains(theirs);

      var suspended =
          body(
              call(
                  "admin-server-suspend",
                  "PUT",
                  "/api/v1/admin/federation/servers/" + host.toUpperCase(),
                  Map.of("severity", "SUSPEND"),
                  admin,
                  200));
      assertThat(suspended.path("severity").asString()).isEqualTo("SUSPEND");
      assertThat(suspended.path("reason").isNull()).isTrue();
      assertThat(
              count("SELECT COUNT(*) FROM federation_following WHERE user_id = ?", owner.getId()))
          .isZero();
      assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
          .isZero();
      assertThat(
              count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
          .isZero();

      post(
          "federation-inbox-suspended-server",
          "/ap/actors/" + target.publicId() + "/inbox",
          activity(remote + "/follows/10", "Follow", me),
          202);
      assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
          .isZero();
      call(
          "federation-account-suspended-server",
          "GET",
          "/api/v1/federation/accounts/" + aliceId,
          null,
          token,
          404);
      assertThat(taggedIds("notes-tag-suspended-server", tag, token)).doesNotContain(theirs);
      call(
          "notes-thread-suspended-server",
          "GET",
          "/api/v1/public/notes/" + theirs,
          null,
          token,
          404);

      var listed =
          body(call("admin-servers", "GET", "/api/v1/admin/federation/servers", null, admin, 200));
      List<String> domains = new ArrayList<>();
      listed.forEach(row -> domains.add(row.path("domain").asString()));
      assertThat(domains).contains(host);
      call("admin-servers-member", "GET", "/api/v1/admin/federation/servers", null, token, 403);

      String csv =
          "#domain,#severity,#reject_media,#reject_reports,#public_comment,#obfuscate\n"
              + host
              + ",silence,false,false,,false\n"
              + shared
              + "-a.example,suspend,true,true,\"spam, abuse\",false\n"
              + shared
              + "-b.example,silence,false,false,,false\n"
              + shared
              + "-c.example,noop,false,false,,false\n"
              + "*.wild.example,suspend,false,false,,false\n";
      Map<String, String> asCsv =
          Map.of("Authorization", "Bearer " + admin, "Content-Type", "text/csv");
      var preview =
          body(
              callWithHeaders(
                  "admin-servers-import-dry-run",
                  "POST",
                  "/api/v1/admin/federation/servers/import?dryRun=true",
                  csv,
                  asCsv,
                  200));
      assertThat(preview.path("added").asInt()).isEqualTo(2);
      assertThat(preview.path("kept").asInt()).isEqualTo(1);
      assertThat(preview.path("skipped").asInt()).isEqualTo(2);
      assertThat(preview.path("invalid").get(0).asString()).isEqualTo("*.wild.example");
      assertThat(
              count(
                  "SELECT COUNT(*) FROM federation_domain_block WHERE domain LIKE ?",
                  shared + "-%"))
          .isZero();
      var imported =
          body(
              callWithHeaders(
                  "admin-servers-import",
                  "POST",
                  "/api/v1/admin/federation/servers/import",
                  csv,
                  asCsv,
                  200));
      assertThat(imported.path("added").asInt()).isEqualTo(2);
      assertThat(
              jdbc.queryForList(
                  "SELECT CONCAT(domain, ' ', severity, ' ', COALESCE(reason, '-'))"
                      + " FROM federation_domain_block WHERE domain LIKE ? ORDER BY domain",
                  String.class,
                  shared + "-%"))
          .containsExactly(
              shared + "-a.example SUSPEND spam, abuse", shared + "-b.example LIMIT -");
      assertThat(
              jdbc.queryForObject(
                  "SELECT severity FROM federation_domain_block WHERE domain = ?",
                  String.class,
                  host))
          .isEqualTo("SUSPEND");

      call(
          "admin-server-unblock",
          "DELETE",
          "/api/v1/admin/federation/servers/" + host,
          null,
          admin,
          204);
      assertThat(count("SELECT COUNT(*) FROM federation_domain_block WHERE domain = ?", host))
          .isZero();
      assertThat(taggedIds("notes-tag-unblocked-server", tag, token)).contains(theirs);
    } finally {
      jdbc.update("DELETE FROM federation_domain_block WHERE domain = ?", host);
      jdbc.update("DELETE FROM federation_domain_block WHERE domain LIKE ?", shared + "-%");
      jdbc.update("DELETE FROM note_tag WHERE note_id = ?", theirs);
      jdbc.update("DELETE FROM note WHERE id = ?", theirs);
    }
  }

  @Test
  void aMemberBlocksOneMastodonAccountThenMutesItInstead() throws Exception {
    String me = urls.actor(target.publicId());
    String personal = "/ap/actors/" + target.publicId() + "/inbox";
    Long aliceId =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, alice);
    String account = "/api/v1/federation/accounts/" + aliceId;
    jdbc.update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        remote + "/follows/20");
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        aliceId,
        me + "#follows/20");
    jdbc.update(
        "INSERT INTO notification (recipient_user_id, type, actor_remote_id, created_at)"
            + " VALUES (?, 'REMOTE_FOLLOW', ?, NOW(6))",
        owner.getId(),
        aliceId);
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at) VALUES (?, ?, ?, NOW(6))",
        aliceId,
        alice + "/statuses/20",
        "loud from elsewhere");
    Long theirs =
        jdbc.queryForObject(
            "SELECT id FROM note WHERE uri = ?", Long.class, alice + "/statuses/20");
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'from kurl', NOW(6))",
        owner.getId());
    Long mine =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    try {
      var blocked =
          body(call("federation-remote-block", "PUT", account + "/block", null, token, 200));
      assertThat(blocked.path("blocking").asBoolean()).isTrue();
      assertThat(blocked.path("muting").asBoolean()).isFalse();
      assertThat(blocked.path("following").asBoolean()).isFalse();
      assertThat(
              count("SELECT COUNT(*) FROM federation_following WHERE user_id = ?", owner.getId()))
          .isZero();
      assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
          .isZero();
      assertThat(
              count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
          .isZero();
      List<String> sent =
          jdbc.queryForList(
              "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?"
                  + " ORDER BY id",
              String.class,
              owner.getId(),
              alice + "/inbox");
      assertThat(sent).hasSize(3);
      assertThat(json.readTree(sent.get(0)).path("type").asString()).isEqualTo("Undo");
      assertThat(json.readTree(sent.get(1)).path("type").asString()).isEqualTo("Reject");
      var block = json.readTree(sent.get(2));
      assertThat(block.path("type").asString()).isEqualTo("Block");
      assertThat(block.path("actor").asString()).isEqualTo(me);
      assertThat(block.path("object").asString()).isEqualTo(alice);

      var listed =
          body(
              call(
                  "federation-remote-blocks",
                  "GET",
                  "/api/v1/federation/blocks",
                  null,
                  token,
                  200));
      assertThat(listed.path("items").get(0).path("id").asLong()).isEqualTo(aliceId);
      assertThat(listed.path("items").get(0).path("blocking").asBoolean()).isTrue();
      assertThat(
              body(call("federation-remote-account-blocked", "GET", account, null, token, 200))
                  .path("blocking")
                  .asBoolean())
          .isTrue();
      assertThat(
              body(call(
                      "federation-remote-notes-blocked",
                      "GET",
                      account + "/notes",
                      null,
                      token,
                      200))
                  .path("items"))
          .isEmpty();
      call(
          "notes-thread-blocked-account",
          "GET",
          "/api/v1/public/notes/" + theirs,
          null,
          token,
          404);
      var refused =
          body(
              call(
                  "federation-remote-follow-blocked",
                  "POST",
                  account + "/follow",
                  null,
                  token,
                  409));
      assertThat(refused.path("code").asString()).isEqualTo("REMOTE_ACCOUNT_BLOCKED");

      post(
          "federation-inbox-follow-blocked-account",
          personal,
          activity(remote + "/follows/21", "Follow", me),
          202);
      assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?", owner.getId()))
          .isZero();
      Map<String, Object> reply = new LinkedHashMap<>();
      reply.put("id", alice + "/statuses/21");
      reply.put("type", "Note");
      reply.put("attributedTo", alice);
      reply.put("inReplyTo", urls.note(mine));
      reply.put("content", "<p>from a blocked account</p>");
      reply.put("published", "2026-10-15T00:00:00Z");
      reply.put("to", List.of("https://www.w3.org/ns/activitystreams#Public"));
      post(
          "federation-inbox-reply-blocked-account",
          "/ap/inbox",
          activity(alice + "/statuses/21/activity", "Create", reply),
          202);
      assertThat(
              count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
          .isZero();

      var unblocked =
          body(call("federation-remote-unblock", "DELETE", account + "/block", null, token, 200));
      assertThat(unblocked.path("blocking").asBoolean()).isFalse();
      var undo =
          json.readTree(
              jdbc.queryForObject(
                  "SELECT body FROM federation_delivery WHERE signer_user_id = ? AND inbox = ?"
                      + " ORDER BY id DESC LIMIT 1",
                  String.class,
                  owner.getId(),
                  alice + "/inbox"));
      assertThat(undo.path("type").asString()).isEqualTo("Undo");
      assertThat(undo.path("object").path("id").asString()).isEqualTo(block.path("id").asString());

      var muted =
          body(
              call(
                  "federation-remote-mute",
                  "PUT",
                  account + "/mute",
                  Map.of("notifications", true, "duration", 3600),
                  token,
                  200));
      assertThat(muted.path("muting").asBoolean()).isTrue();
      assertThat(muted.path("blocking").asBoolean()).isFalse();
      var mutes =
          body(
              call("federation-remote-mutes", "GET", "/api/v1/federation/mutes", null, token, 200));
      assertThat(mutes.path("items").get(0).path("account").path("id").asLong()).isEqualTo(aliceId);
      assertThat(mutes.path("items").get(0).path("notifications").asBoolean()).isTrue();
      assertThat(mutes.path("items").get(0).path("expiresAt").isNull()).isFalse();
      assertThat(
              body(call(
                      "federation-remote-notes-muted", "GET", account + "/notes", null, token, 200))
                  .path("items")
                  .get(0)
                  .path("id")
                  .asLong())
          .isEqualTo(theirs);
      Map<String, Object> again = new LinkedHashMap<>(reply);
      again.put("id", alice + "/statuses/22");
      post(
          "federation-inbox-reply-muted-account",
          "/ap/inbox",
          activity(alice + "/statuses/22/activity", "Create", again),
          202);
      assertThat(
              count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
          .isZero();
      assertThat(
              body(call(
                      "federation-remote-mute-invalid",
                      "PUT",
                      account + "/mute",
                      Map.of("duration", 5),
                      token,
                      400))
                  .path("code")
                  .asString())
          .isEqualTo("MUTE_DURATION_INVALID");

      var unmuted =
          body(call("federation-remote-unmute", "DELETE", account + "/mute", null, token, 200));
      assertThat(unmuted.path("muting").asBoolean()).isFalse();
      assertThat(
              count("SELECT COUNT(*) FROM federation_remote_mute WHERE user_id = ?", owner.getId()))
          .isZero();
    } finally {
      jdbc.update("DELETE FROM note WHERE remote_actor_id = ?", aliceId);
      jdbc.update("DELETE FROM note WHERE id = ?", mine);
    }
  }

  private List<Long> taggedIds(String id, String tag, String auth) throws Exception {
    List<Long> ids = new ArrayList<>();
    body(call(id, "GET", "/api/v1/public/notes/tags/" + tag, null, auth, 200))
        .path("items")
        .forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  private List<Long> trendingIds(String id) throws Exception {
    List<Long> ids = new ArrayList<>();
    body(call(id, "GET", "/api/v1/public/notes?sort=trending&size=50", null, null, 200))
        .path("items")
        .forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  @Test
  void aPersonalInboxOfNoOneIs404() throws Exception {
    String path = "/ap/actors/nobody0000/inbox";
    post("federation-inbox-unknown", path, activity(remote + "/f/9", "Follow", "x"), 404);
  }
}
