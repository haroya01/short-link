package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class FederationSearchHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();

  @MockitoBean private FederationHttp http;
  @Autowired private FederationUrls urls;
  @Autowired private FederationActorService localActors;
  @Autowired private SigningKeys signingKeys;

  private String tag;

  @BeforeEach
  void instanceKeyAndTag() {
    signingKeys.forInstance();
    tag = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  private String host(String name) {
    return name + "-" + tag + ".example";
  }

  private long seed(String username, String host) {
    String actor = "https://" + host + "/users/" + username;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " shared_inbox, username, domain, profile_url, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        actor,
        actor + "#main-key",
        KEYS.publicKey(),
        actor + "/inbox",
        "https://" + host + "/inbox",
        username,
        host,
        "https://" + host + "/@" + username);
    return jdbc.queryForObject(
        "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, actor);
  }

  private static String query(String q) {
    return URLEncoder.encode(q, StandardCharsets.UTF_8);
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> accts(JsonNode found) {
    List<String> accts = new ArrayList<>();
    for (JsonNode item : found.path("items")) {
      accts.add(item.path("acct").asString());
    }
    return accts;
  }

  @Test
  void anyoneFindsKnownAccountsAndAMemberSeesTheirFollowsFirst() throws Exception {
    long exact = seed(tag, host("exact"));
    long followed = seed(tag + "zz", host("followed"));
    long gone = seed(tag + "aa", host("gone"));
    long blocked = seed(tag + "bb", host("blocked"));
    for (long findable : List.of(exact, gone, blocked)) {
      jdbc.update("UPDATE federation_remote_actor SET discoverable = TRUE WHERE id = ?", findable);
    }
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        owner.getId(),
        followed,
        "https://kurl.me/ap/actors/x#follows/" + tag);
    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'SUSPEND', NOW(6))",
        host("gone"));
    jdbc.update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        host("blocked"));

    var mine =
        body(
            call(
                "federation-account-search",
                "GET",
                "/api/v1/federation/accounts/search?q=" + query("@" + tag) + "&limit=5",
                null,
                token,
                200));
    assertThat(accts(mine))
        .containsExactly(tag + "zz@" + host("followed"), tag + "@" + host("exact"));
    assertThat(mine.path("items").get(0).path("following").asBoolean()).isTrue();
    assertThat(mine.path("items").get(0).path("url").asString())
        .isEqualTo("https://" + host("followed") + "/@" + tag + "zz");

    var anyone =
        body(
            call(
                "federation-account-search-anonymous",
                "GET",
                "/api/v1/federation/accounts/search?q=" + tag,
                null,
                null,
                200));
    assertThat(accts(anyone))
        .containsExactly(tag + "@" + host("exact"), tag + "bb@" + host("blocked"));

    var byHandle =
        body(
            call(
                "federation-account-search-handle",
                "GET",
                "/api/v1/federation/accounts/search?q="
                    + query("@" + tag + "zz@" + host("followed")),
                null,
                null,
                200));
    assertThat(accts(byHandle)).containsExactly(tag + "zz@" + host("followed"));
    assertThat(byHandle.path("items").get(0).path("following").asBoolean()).isFalse();

    assertThat(
            body(call(
                    "federation-account-search-short",
                    "GET",
                    "/api/v1/federation/accounts/search?q=" + query(" @f "),
                    null,
                    null,
                    200))
                .path("items"))
        .isEmpty();
  }

  @Test
  void aMemberIsSuggestedAccountsTheirFollowsFollowAndFindsTheirPublicNotes() throws Exception {
    long carol = seed(tag + "c", host("carol"));
    long quiet = seed(tag + "q", host("quiet"));
    jdbc.update(
        "UPDATE federation_remote_actor SET discoverable = TRUE, indexable = TRUE WHERE id = ?",
        carol);
    var friend = createUser();
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        friend.getId());
    for (long account : List.of(carol, quiet)) {
      jdbc.update(
          "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
              + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
          friend.getId(),
          account,
          "https://kurl.me/ap/actors/x#follows/" + tag + account);
    }
    String word = "zqx" + tag;
    for (long account : List.of(carol, quiet)) {
      jdbc.update(
          "INSERT INTO note (remote_actor_id, uri, body, created_at) VALUES (?, ?, ?, NOW(6))",
          account,
          "https://elsewhere.example/" + tag + "/" + account,
          "notes about " + word);
    }
    try {
      var suggested =
          body(
              call(
                  "federation-account-suggestions",
                  "GET",
                  "/api/v1/federation/accounts/suggestions",
                  null,
                  token,
                  200));
      assertThat(accts(suggested)).containsExactly(tag + "c@" + host("carol"));
      call(
          "federation-account-suggestions-anonymous",
          "GET",
          "/api/v1/federation/accounts/suggestions",
          null,
          null,
          401);

      var found =
          body(
              call(
                  "notes-search-elsewhere",
                  "GET",
                  "/api/v1/public/notes/search?q=" + word,
                  null,
                  null,
                  200));
      assertThat(found.path("items")).hasSize(1);
      assertThat(found.path("items").get(0).path("author").path("remoteId").asLong())
          .isEqualTo(carol);
    } finally {
      jdbc.update("DELETE FROM note WHERE remote_actor_id IN (?, ?)", carol, quiet);
    }
  }

  @Test
  void aMemberResolvesAHandleAProfileAddressAndANoteAddress() throws Exception {
    long alice = seed(tag, host("exact"));
    String aliceUri = "https://" + host("exact") + "/users/" + tag;

    var handle =
        body(
            call(
                "federation-resolve-handle",
                "GET",
                "/api/v1/federation/resolve?q=" + query("@" + tag + "@" + host("exact")),
                null,
                token,
                200));
    assertThat(handle.path("account").path("id").asLong()).isEqualTo(alice);
    assertThat(handle.path("note").isNull()).isTrue();

    String carol = "https://" + host("profile") + "/users/carol";
    URI profile = URI.create("https://" + host("profile") + "/@carol");
    when(http.get(eq(profile), any()))
        .thenReturn(
            ok(
                """
                {"@context":["https://www.w3.org/ns/activitystreams"],
                 "id":"%1$s","type":"Person","preferredUsername":"carol","name":"Carol",
                 "inbox":"%1$s/inbox","url":"%2$s",
                 "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%3$s}}"""
                    .formatted(carol, profile, json.writeValueAsString(KEYS.publicKey()))));
    var account =
        body(
            call(
                "federation-resolve-profile",
                "GET",
                "/api/v1/federation/resolve?q=" + query(profile.toString()),
                null,
                token,
                200));
    assertThat(account.path("account").path("acct").asString())
        .isEqualTo("carol@" + host("profile"));
    assertThat(account.path("account").path("url").asString()).isEqualTo(profile.toString());
    assertThat(count("SELECT COUNT(*) FROM federation_remote_actor WHERE actor_uri = ?", carol))
        .isEqualTo(1);

    String noteId = aliceUri + "/statuses/1";
    URI page = URI.create("https://" + host("exact") + "/@" + tag + "/1");
    when(http.get(eq(page), any()))
        .thenReturn(
            ok(
                """
                {"@context":"https://www.w3.org/ns/activitystreams",
                 "id":"%s","type":"Note","attributedTo":"%s",
                 "to":["https://www.w3.org/ns/activitystreams#Public"],
                 "url":"%s","published":"2026-10-15T00:00:00Z",
                 "content":"<p>hello from elsewhere</p>"}"""
                    .formatted(noteId, aliceUri, page)));
    var note =
        body(
            call(
                "federation-resolve-note",
                "GET",
                "/api/v1/federation/resolve?q=" + query(page.toString()),
                null,
                token,
                200));
    assertThat(note.path("account").isNull()).isTrue();
    assertThat(note.path("note").path("body").asString()).isEqualTo("hello from elsewhere");
    assertThat(note.path("note").path("author").path("remoteId").asLong()).isEqualTo(alice);
    assertThat(count("SELECT COUNT(*) FROM note WHERE uri = ?", noteId)).isEqualTo(1);

    var again =
        body(
            call(
                "federation-resolve-note-again",
                "GET",
                "/api/v1/federation/resolve?q=" + query(page.toString()),
                null,
                token,
                200));
    assertThat(again.path("note")).isEqualTo(note.path("note"));
    assertThat(count("SELECT COUNT(*) FROM note WHERE uri = ?", noteId)).isEqualTo(1);

    var listed =
        body(
            call(
                "federation-resolve-note-listed",
                "GET",
                "/api/v1/federation/accounts/" + alice + "/notes",
                null,
                token,
                200));
    assertThat(listed.path("items").get(0)).isEqualTo(note.path("note"));
  }

  @Test
  void aNewlySeenAccountsPageIsFilledFromItsOutboxOnceInSixHours() throws Exception {
    String server = "https://" + host("outbox");
    long alice = seed("alice", host("outbox"));
    String actor = server + "/users/alice";
    when(http.get(eq(URI.create(actor)), any()))
        .thenReturn(
            ok(
                "{\"id\":\"%s\",\"type\":\"Person\",\"outbox\":\"%s/outbox\"}"
                    .formatted(actor, actor)));
    when(http.get(eq(URI.create(actor + "/outbox")), any()))
        .thenReturn(
            ok(
                FederationFixtures.text("mastodon-outbox.json")
                    .replace("https://mastodon.example", server)));
    String me = urls.actor(localActors.byUserId(owner.getId()).orElseThrow().publicId());
    when(http.get(eq(URI.create(actor + "/outbox?page=true")), any()))
        .thenReturn(
            ok(
                FederationFixtures.text("mastodon-outbox-page.json")
                    .replace("https://mastodon.example", server)
                    .replace("files.mastodon.example", "files." + host("outbox"))
                    .replace("KURL_MEMBER_ACTOR", me)
                    .replace("KURL_MEMBER_NAME", owner.getUsername())));

    var filled =
        body(
            call(
                "federation-remote-notes-filled",
                "GET",
                "/api/v1/federation/accounts/" + alice + "/notes",
                null,
                token,
                200));
    List<String> kinds = new ArrayList<>();
    filled.path("items").forEach(item -> kinds.add(item.path("remote").path("kind").asString()));
    assertThat(kinds).containsExactly("NOTE", "NOTE", "QUESTION");
    assertThat(filled.path("items").get(2).path("poll").path("votersCount").asLong()).isEqualTo(12);
    assertThat(
            count("SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", owner.getId()))
        .isZero();

    var again =
        body(
            call(
                "federation-remote-notes-filled-again",
                "GET",
                "/api/v1/federation/accounts/" + alice + "/notes",
                null,
                token,
                200));
    assertThat(again.path("items")).isEqualTo(filled.path("items"));
    verify(http, times(3)).get(any(), any());
  }

  @Test
  void eachFailureSaysWhichKindItIs() throws Exception {
    call(
        "federation-resolve-anonymous",
        "GET",
        "/api/v1/federation/resolve?q=" + query("@alice@mastodon.example"),
        null,
        null,
        401);

    var self =
        body(
            call(
                "federation-resolve-self",
                "GET",
                "/api/v1/federation/resolve?q=" + query(urls.profile(owner.getUsername())),
                null,
                token,
                404));
    assertThat(self.path("code").asString()).isEqualTo("REMOTE_ACCOUNT_NOT_FOUND");

    var malformed =
        body(
            call(
                "federation-resolve-invalid",
                "GET",
                "/api/v1/federation/resolve?q=" + query("alice"),
                null,
                token,
                400));
    assertThat(malformed.path("code").asString()).isEqualTo("REMOTE_ACCOUNT_INVALID");

    URI down = URI.create("https://" + host("down") + "/@alice");
    when(http.get(eq(down), any()))
        .thenReturn(new FederationHttp.Result.Unreachable("SocketTimeoutException"));
    var unreachable =
        body(
            call(
                "federation-resolve-unreachable",
                "GET",
                "/api/v1/federation/resolve?q=" + query(down.toString()),
                null,
                token,
                502));
    assertThat(unreachable.path("code").asString()).isEqualTo("REMOTE_SERVER_UNREACHABLE");

    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'SUSPEND', NOW(6))",
        host("gone"));
    var suspended =
        body(
            call(
                "federation-resolve-suspended",
                "GET",
                "/api/v1/federation/resolve?q=" + query("https://" + host("gone") + "/@alice"),
                null,
                token,
                404));
    assertThat(suspended.path("code").asString()).isEqualTo("REMOTE_ACCOUNT_NOT_FOUND");
  }
}
