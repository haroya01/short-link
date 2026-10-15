package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
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
    seed(tag, host("exact"));
    long followed = seed(tag + "zz", host("followed"));
    seed(tag + "aa", host("gone"));
    seed(tag + "bb", host("blocked"));
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
        .containsExactly(
            tag + "@" + host("exact"),
            tag + "bb@" + host("blocked"),
            tag + "zz@" + host("followed"));
    assertThat(anyone.path("items").get(2).path("following").asBoolean()).isFalse();

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
