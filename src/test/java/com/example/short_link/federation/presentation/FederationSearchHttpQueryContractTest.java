package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.directory.DirectorySync;
import com.example.short_link.federation.application.inbox.SignedInboxRequests;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.TrendingPosts;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.scheduler.RelayWorker;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class FederationSearchHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();

  @MockitoBean private FederationHttp http;
  @Autowired private TrendingPosts trending;
  @Autowired private FederationUrls urls;
  @Autowired private FederationActorService localActors;
  @Autowired private SigningKeys signingKeys;
  @Autowired private DirectorySync directorySync;
  @Autowired private RelaySubscriptions relays;
  @Autowired private FederationProperties federation;
  @Autowired private RelayWorker relayWorker;

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
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
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

  private String trendingStatus(String host, String user, long boosts) {
    return """
        {"uri":"https://%1$s/users/%2$s/statuses/1","url":"https://%1$s/@%2$s/1",
         "created_at":"2026-10-16T13:48:01.441Z","visibility":"public","in_reply_to_id":null,
         "reblog":null,"sensitive":false,"spoiler_text":"","language":"ko",
         "content":"<p>%2$s says hi</p>","replies_count":0,"reblogs_count":%3$d,
         "favourites_count":0,"media_attachments":[],"card":null,"poll":null,
         "account":{"acct":"%2$s@%1$s","display_name":"%2$s","discoverable":true,
          "avatar":"https://%1$s/a.png","url":"https://%1$s/@%2$s",
          "uri":"https://%1$s/users/%2$s"}}"""
        .formatted(host, user, boosts);
  }

  private static List<String> trendingUris(JsonNode found) {
    List<String> uris = new ArrayList<>();
    for (JsonNode item : found.path("items")) {
      uris.add(item.path("uri").asString());
    }
    return uris;
  }

  @Test
  void anyoneSeesWhatTrendsElsewhereAndAMemberMissesWhatTheyHid() throws Exception {
    String alice = trendingStatus(host("ta"), "alice", 0);
    String bob = trendingStatus(host("tb"), "bob", 0);
    String carol = trendingStatus(host("tc"), "carol", 0);
    String dave = trendingStatus(host("td"), "dave", 0);
    String erin = trendingStatus(host("te"), "erin", 10);
    when(http.getJson(
            eq(URI.create("https://mastodon.social/api/v1/trends/statuses?limit=40")),
            any(),
            any()))
        .thenReturn(ok("[" + String.join(",", alice, bob, carol, dave) + "]"));
    when(http.getJson(
            eq(URI.create("https://mstdn.jp/api/v1/trends/statuses?limit=40")), any(), any()))
        .thenReturn(ok("[" + String.join(",", alice, erin) + "]"));
    when(http.get(any(), any())).thenReturn(new FederationHttp.Result.Failed(404, "gone"));
    long bobHere = seed("bob", host("tb"));
    jdbc.update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        owner.getId(),
        bobHere);
    jdbc.update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        host("tc"));
    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'LIMIT', NOW(6))",
        host("td"));

    assertThat(
            body(call(
                    "federation-trending-empty",
                    "GET",
                    "/api/v1/federation/trending",
                    null,
                    null,
                    200))
                .path("items"))
        .isEmpty();

    captureBackgroundWork("federation-trending-refresh", trending::refresh);

    var anyone =
        body(
            call(
                "federation-trending",
                "GET",
                "/api/v1/federation/trending?limit=10",
                null,
                null,
                200));
    String post = "https://%s/users/%s/statuses/1";
    assertThat(trendingUris(anyone))
        .containsExactly(
            post.formatted(host("ta"), "alice"),
            post.formatted(host("te"), "erin"),
            post.formatted(host("tb"), "bob"),
            post.formatted(host("tc"), "carol"));
    JsonNode first = anyone.path("items").get(0);
    assertThat(first.path("body").asString()).isEqualTo("alice says hi");
    assertThat(first.path("author").path("username").asString()).isEqualTo("alice@" + host("ta"));
    assertThat(first.path("language").asString()).isEqualTo("ko");
    assertThat(first.path("remote").path("url").asString())
        .isEqualTo("https://" + host("ta") + "/@alice/1");
    assertThat(first.path("servers").toString()).isEqualTo("[\"mastodon.social\",\"mstdn.jp\"]");
    assertThat(first.path("sourceHost").asString()).isEqualTo("mastodon.social");
    assertThat(first.path("url").asString()).isEqualTo("https://" + host("ta") + "/@alice/1");
    assertThat(first.has("id")).isTrue();
    assertThat(first.path("id").isNull()).isTrue();
    assertThat(first.path("likedByMe").isNull()).isTrue();
    assertThat(first.path("visibility").asString()).isEqualTo("public");
    assertThat(first.path("author").path("id").isNull()).isTrue();
    assertThat(first.path("author").path("remoteId").isNull()).isTrue();
    JsonNode known = anyone.path("items").get(2).path("author");
    assertThat(known.path("remoteId").asLong()).isEqualTo(bobHere);
    assertThat(known.path("id").asLong()).isEqualTo(-bobHere);

    var mine =
        body(
            call(
                "federation-trending-member",
                "GET",
                "/api/v1/federation/trending",
                null,
                token,
                200));
    assertThat(trendingUris(mine))
        .containsExactly(post.formatted(host("ta"), "alice"), post.formatted(host("te"), "erin"));
    assertThat(mine.path("next").asString()).isEqualTo("r");
  }

  private String person(String host, String user, String publicKey) {
    String actor = "https://" + host + "/users/" + user;
    return """
        {"@context":["https://www.w3.org/ns/activitystreams","https://w3id.org/security/v1"],
         "id":"%1$s","type":"Person","preferredUsername":"%2$s","inbox":"%1$s/inbox",
         "endpoints":{"sharedInbox":"https://%3$s/inbox"},"url":"https://%3$s/@%2$s",
         "discoverable":true,
         "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%4$s}}"""
        .formatted(actor, user, host, json.writeValueAsString(publicKey));
  }

  @Test
  void aMemberBringsATrendingPostOverFromMemoryRepostsItAndScrollsOnToKeptNotes() throws Exception {
    String alice = trendingStatus(host("ra"), "alice", 3);
    String status = "https://" + host("ra") + "/users/alice/statuses/1";
    String actor = "https://" + host("ra") + "/users/alice";
    when(http.getJson(
            eq(URI.create("https://mastodon.social/api/v1/trends/statuses?limit=40")),
            any(),
            any()))
        .thenReturn(ok("[" + alice + "]"));
    when(http.getJson(
            eq(URI.create("https://mstdn.jp/api/v1/trends/statuses?limit=40")), any(), any()))
        .thenReturn(ok("[]"));
    when(http.get(eq(URI.create(actor)), any()))
        .thenReturn(ok(person(host("ra"), "alice", KEYS.publicKey())));
    long kept = seed(tag + "k", host("rk"));
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at) VALUES (?, ?, 'kept here', NOW(6))",
        kept,
        "https://" + host("rk") + "/notes/" + tag);
    Long keptId =
        jdbc.queryForObject(
            "SELECT id FROM note WHERE uri = ?",
            Long.class,
            "https://" + host("rk") + "/notes/" + tag);
    captureBackgroundWork("federation-trending-pool-refresh", trending::refresh);

    var first =
        body(
            call(
                "federation-trending-pool-member",
                "GET",
                "/api/v1/federation/trending?limit=20",
                null,
                token,
                200));
    assertThat(trendingUris(first)).containsExactly(status);
    assertThat(first.path("next").asString()).isEqualTo("r");
    var onward =
        body(
            call(
                "federation-trending-kept",
                "GET",
                "/api/v1/federation/trending?limit=20&cursor=r",
                null,
                token,
                200));
    assertThat(onward.path("items").findValues("id").stream().map(JsonNode::asLong).toList())
        .contains(keptId);

    var resolved =
        body(
            call(
                "federation-trending-resolve",
                "GET",
                "/api/v1/federation/resolve?q=" + query(status),
                null,
                token,
                200));
    long noteId = resolved.path("note").path("id").asLong();
    assertThat(resolved.path("note").path("body").asString()).isEqualTo("alice says hi");
    verify(http, never()).get(eq(URI.create(status)), any());

    call(
        "federation-trending-repost",
        "PUT",
        "/api/v1/notes/" + noteId + "/repost",
        null,
        token,
        200);
    call(
        "federation-trending-unrepost",
        "DELETE",
        "/api/v1/notes/" + noteId + "/repost",
        null,
        token,
        200);
    assertThat(
            jdbc.queryForList(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(body, '$.type')) FROM federation_delivery"
                    + " WHERE signer_user_id = ? AND inbox = ? ORDER BY id",
                String.class,
                owner.getId(),
                "https://" + host("ra") + "/inbox"))
        .containsExactly("Announce", "Undo");
  }

  @Test
  void anyoneReadsTheLatestPostsOfTheChosenServersAndAMemberBringsOneOverFromMemory()
      throws Exception {
    ObjectNode fresh = (ObjectNode) json.readTree(trendingStatus("mstdn.jp", "yui" + tag, 0));
    fresh.put("id", "101");
    ObjectNode warned = (ObjectNode) json.readTree(trendingStatus("mstdn.jp", "rin" + tag, 0));
    warned.put("id", "100");
    warned.put("spoiler_text", "ネタバレ");
    when(http.getJson(
            eq(URI.create("https://mstdn.jp/api/v1/timelines/public?local=true&limit=40")),
            any(),
            any()))
        .thenReturn(
            ok("[" + json.writeValueAsString(fresh) + "," + json.writeValueAsString(warned) + "]"));
    String status = "https://mstdn.jp/users/yui" + tag + "/statuses/1";
    when(http.get(eq(URI.create("https://mstdn.jp/users/yui" + tag)), any()))
        .thenReturn(ok(person("mstdn.jp", "yui" + tag, KEYS.publicKey())));

    var latest =
        body(
            call(
                "federation-latest-first",
                "GET",
                "/api/v1/federation/latest?limit=20",
                null,
                null,
                200));
    assertThat(trendingUris(latest)).containsExactly(status);
    assertThat(latest.path("next").isNull()).isTrue();

    var resolved =
        body(
            call(
                "federation-latest-resolve",
                "GET",
                "/api/v1/federation/resolve?q=" + query(status),
                null,
                token,
                200));
    assertThat(resolved.path("note").path("id").asLong()).isPositive();
    verify(http, never()).get(eq(URI.create(status)), any());
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
    String carolDocument =
        """
        {"@context":["https://www.w3.org/ns/activitystreams"],
         "id":"%1$s","type":"Person","preferredUsername":"carol","name":"Carol",
         "inbox":"%1$s/inbox","url":"%2$s",
         "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%3$s}}"""
            .formatted(carol, profile, json.writeValueAsString(KEYS.publicKey()));
    when(http.get(eq(profile), any())).thenReturn(ok(carolDocument));
    when(http.get(eq(URI.create(carol)), any())).thenReturn(ok(carolDocument));
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
  void anAdminReadsAnInstanceDirectoryAndItsPeopleAreFoundByName() throws Exception {
    String admin = jwt.createAccessToken(createUser().getId(), "ADMIN");
    String dir = host("dir");
    seed(tag + "known", dir);
    String fresh = "https://" + dir + "/users/" + tag + "new";
    when(http.getJson(
            eq(
                URI.create(
                    "https://"
                        + dir
                        + "/api/v1/directory?local=true&order=active&limit=80&offset=0")),
            any(),
            any()))
        .thenReturn(
            ok(
                """
                [{"username":"%1$sknown","discoverable":true,"uri":"https://%2$s/users/%1$sknown"},
                 {"username":"%1$snew","discoverable":true,"bot":true,"uri":"%3$s"},
                 {"username":"%1$sgone","discoverable":false,"uri":"https://%2$s/users/%1$sgone"}]"""
                    .formatted(tag, dir, fresh)));
    when(http.get(eq(URI.create(fresh)), any()))
        .thenReturn(
            ok(
                """
                {"@context":["https://www.w3.org/ns/activitystreams"],
                 "id":"%1$s","type":"Service","preferredUsername":"%2$snew","name":"New",
                 "inbox":"%1$s/inbox","discoverable":true,
                 "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%3$s}}"""
                    .formatted(fresh, tag, json.writeValueAsString(KEYS.publicKey()))));
    try {
      var added =
          body(
              call(
                  "admin-directory-put",
                  "PUT",
                  "/api/v1/admin/federation/directories/" + dir,
                  Map.of("enabled", true, "cap", 80),
                  admin,
                  200));
      assertThat(added.path("host").asString()).isEqualTo(dir);
      assertThat(added.path("cap").asInt()).isEqualTo(80);
      assertThat(
              accts(
                  body(
                      call(
                          "federation-account-search-before-directory",
                          "GET",
                          "/api/v1/federation/accounts/search?q=" + tag,
                          null,
                          null,
                          200))))
          .isEmpty();

      captureBackgroundWork(
          "federation-directory-sync",
          () -> assertThat(directorySync.sync(dir).added()).isEqualTo(1));

      var found =
          body(
              call(
                  "federation-account-search-directory",
                  "GET",
                  "/api/v1/federation/accounts/search?q=" + tag,
                  null,
                  null,
                  200));
      assertThat(accts(found)).containsExactly(tag + "known@" + dir, tag + "new@" + dir);

      var status =
          body(
              call(
                  "admin-directories",
                  "GET",
                  "/api/v1/admin/federation/directories",
                  null,
                  admin,
                  200));
      assertThat(status.path("enabled").asBoolean()).isTrue();
      assertThat(status.path("sources").get(0).path("lastOutcome").asString())
          .isEqualTo("listed 3, known 1, added 1, later 0, skipped 1");
      call(
          "admin-directories-member",
          "GET",
          "/api/v1/admin/federation/directories",
          null,
          token,
          403);
      var run =
          body(
              call(
                  "admin-directory-run",
                  "POST",
                  "/api/v1/admin/federation/directories/run",
                  null,
                  admin,
                  202));
      assertThat(run.path("started").asBoolean()).isFalse();
      call(
          "admin-directory-delete",
          "DELETE",
          "/api/v1/admin/federation/directories/" + dir,
          null,
          admin,
          204);
    } finally {
      jdbc.update("DELETE FROM federation_directory_source WHERE host = ?", dir);
    }
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

  @Test
  void aPostATagRelayAnnouncesIsKeptAndReadInTheOtherServersFeed() throws Exception {
    String server = host("relayed");
    long author = seed("alice", server);
    String alice = "https://" + server + "/users/alice";
    String post = alice + "/statuses/1";
    String relay = "https://" + host("relay") + "/tag/gaebal";
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, domain,"
            + " display_name, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, '#gaebal', NOW(6), NOW(6), NOW(6))",
        relay,
        relay + "#key",
        KEYS.publicKey(),
        relay,
        host("relay"));
    jdbc.update(
        "INSERT INTO federation_relay (actor_uri, inbox, follow_activity_id, state, accepted_at,"
            + " created_at, updated_at) VALUES (?, ?, ?, 'ACCEPTED', NOW(6), NOW(6), NOW(6))",
        relay,
        relay,
        urls.relayFollow(tag));
    relays.load();
    relays.change(RelayMode.STORE, List.of("ko", "ja"));
    when(http.get(eq(URI.create(post)), any(), any(Duration.class)))
        .thenReturn(
            ok(
                """
                {"id":"%s","type":"Note","attributedTo":"%s",
                 "to":["https://www.w3.org/ns/activitystreams#Public"],
                 "published":"2026-10-17T00:00:00Z",
                 "content":"<p>개발 일지 #개발</p>","contentMap":{"ko":"<p>개발 일지 #개발</p>"},
                 "attachment":[{"type":"Document","mediaType":"image/png",
                   "url":"https://%s/media/a.png","name":"screen"}]}"""
                    .formatted(post, alice, server)));
    try {
      Map<String, Object> announce = new LinkedHashMap<>();
      announce.put("@context", "https://www.w3.org/ns/activitystreams");
      announce.put("id", "https://" + host("relay") + "/announce/1");
      announce.put("type", "Announce");
      announce.put("actor", relay);
      announce.put("object", post);
      byte[] signed = json.writeValueAsString(announce).getBytes(StandardCharsets.UTF_8);
      callWithHeaders(
          "federation-inbox-relay-announce-store",
          "POST",
          "/ap/inbox",
          announce,
          SignedInboxRequests.headers(
              relay + "#key",
              KEYS.privateKey(),
              HttpSignatures.host(URI.create(federation.baseUrl())),
              "/ap/inbox",
              signed,
              Instant.now()),
          202);

      captureBackgroundWork(
          "federation-relay-worker-store", () -> assertThat(relayWorker.processNext()).isTrue());
      long note =
          jdbc.queryForObject(
              "SELECT id FROM note WHERE uri = ? AND remote_actor_id = ?",
              Long.class,
              post,
              author);
      assertThat(count("SELECT COUNT(*) FROM federation_relayed_note WHERE note_id = ?", note))
          .isEqualTo(1);

      var feed =
          body(call("notes-federated-relayed", "GET", "/api/v1/notes/federated", null, token, 200));
      List<Long> ids = new ArrayList<>();
      feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
      assertThat(ids).contains(note);
    } finally {
      relays.change(RelayMode.COUNT, List.of("ko", "ja"));
      jdbc.update("UPDATE federation_relay_setting SET chosen_at = NULL");
      jdbc.update("DELETE FROM federation_relay WHERE actor_uri = ?", relay);
      relays.load();
    }
  }
}
