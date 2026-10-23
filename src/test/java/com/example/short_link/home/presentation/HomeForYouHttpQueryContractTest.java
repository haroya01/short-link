package com.example.short_link.home.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.presentation.RemoteTrendsController;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(
    properties = {
      "short-link.federation.latest.pull=true",
      "short-link.federation.latest.hosts=hr.example",
      "short-link.federation.latest.backfill-pages=1"
    })
class HomeForYouHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final String HOST = "hr.example";
  private static final String DEVICE = "2c7d9e1f-3a4b-4c5d-8e6f-7a8b9c0d1e2f";
  private static final String TIMELINE =
      "https://" + HOST + "/api/v1/timelines/public?local=true&limit=40";

  @MockitoBean private FederationHttp http;
  @Autowired private LatestPosts latest;
  @Autowired private LocalSeen localSeen;
  @Autowired private RemoteSeen remoteSeen;
  @Autowired private SigningKeys signingKeys;

  @BeforeEach
  void instanceKey() {
    signingKeys.forInstance();
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
  }

  private static String uri(String user, int id) {
    return "https://" + HOST + "/users/" + user + "/statuses/" + id;
  }

  private static String status(int id, String user, String language, int likes, Duration age) {
    Instant at = Instant.now().minus(age).truncatedTo(ChronoUnit.MILLIS);
    return """
        {"id":"%1$d","uri":"https://%4$s/users/%2$s/statuses/%1$d",
         "url":"https://%4$s/@%2$s/%1$d","created_at":"%3$s","visibility":"public",
         "in_reply_to_id":null,"reblog":null,"sensitive":false,"spoiler_text":"",
         "language":"%5$s","content":"<p>%2$s %1$d</p>","replies_count":0,"reblogs_count":0,
         "favourites_count":%6$d,"media_attachments":[],"card":null,"poll":null,"mentions":[],
         "account":{"acct":"%2$s","display_name":"%2$s","discoverable":true,
          "avatar":"https://%4$s/a.png","url":"https://%4$s/@%2$s",
          "uri":"https://%4$s/users/%2$s"}}"""
        .formatted(id, user, at, HOST, language, likes);
  }

  private String person(String user) {
    String actor = "https://" + HOST + "/users/" + user;
    return """
        {"@context":["https://www.w3.org/ns/activitystreams","https://w3id.org/security/v1"],
         "id":"%1$s","type":"Person","preferredUsername":"%2$s","inbox":"%1$s/inbox",
         "endpoints":{"sharedInbox":"https://%3$s/inbox"},"url":"https://%3$s/@%2$s",
         "discoverable":true,
         "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%4$s}}"""
        .formatted(actor, user, HOST, json.writeValueAsString(KEYS.publicKey()));
  }

  private long note(UserEntity author, Duration age) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'here', ?)",
        author.getId(),
        LocalDateTime.ofInstant(Instant.now().minus(age), ZoneOffset.UTC));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.getId());
  }

  private long post(UserEntity author, Duration age) {
    String slug = "fy-" + UUID.randomUUID().toString().substring(0, 8);
    LocalDateTime at = LocalDateTime.ofInstant(Instant.now().minus(age), ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?, 500, 'ko')",
        author.getId(),
        slug,
        slug,
        at,
        at,
        at);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }

  private static List<String> keys(JsonNode page) {
    List<String> keys = new ArrayList<>();
    for (JsonNode item : page.path("items")) {
      String kind = item.path("kind").asString();
      keys.add(
          kind.equals("remote")
              ? item.path("remote").path("uri").asString()
              : kind + ":" + item.path(kind).path("id").asLong());
    }
    return keys;
  }

  private static Map<String, String> device() {
    return Map.of(RemoteTrendsController.DEVICE_HEADER, DEVICE);
  }

  @Test
  void theHomeRanksKurlNotesPostsAndKeptPostsAsOneListPinningTheReadersFreshNote()
      throws Exception {
    when(http.getJson(eq(URI.create(TIMELINE)), any(), any()))
        .thenReturn(
            ok(
                "["
                    + status(103, "yui", "ko", 3, Duration.ofMinutes(10))
                    + ","
                    + status(102, "rin", "ja", 200, Duration.ofMinutes(20))
                    + ","
                    + status(101, "mio", "ko", 0, Duration.ofMinutes(40))
                    + "]"));
    for (String user : List.of("yui", "rin", "mio")) {
      when(http.get(eq(URI.create("https://" + HOST + "/users/" + user)), any()))
          .thenReturn(ok(person(user)));
    }
    assertThat(latest.pull()).isEqualTo(3);
    long mine = note(owner, Duration.ofMinutes(5));
    long theirs = note(stranger, Duration.ofMinutes(15));
    long post = post(stranger, Duration.ofMinutes(25));

    JsonNode first =
        body(
            call(
                "home-for-you-first",
                "GET",
                "/api/v1/public/home?tab=for-you&limit=3&seen=hide",
                null,
                token,
                200));
    assertThat(keys(first)).containsExactly("note:" + mine, uri("rin", 102), uri("yui", 103));
    assertThat(first.path("items").get(1).path("kind").asString()).isEqualTo("remote");
    assertThat(first.path("items").get(1).path("remote").path("author").isObject()).isTrue();
    assertThat(first.has("newest")).isFalse();
    String next = first.path("next").asString();
    assertThat(next).startsWith("rh");

    JsonNode onward =
        body(
            call(
                "home-for-you-next",
                "GET",
                "/api/v1/public/home?tab=for-you&limit=3&seen=hide&cursor=" + next,
                null,
                token,
                200));
    assertThat(keys(onward)).containsExactly("post:" + post, uri("mio", 101), "note:" + theirs);
    assertThat(onward.path("next").isNull()).isTrue();

    JsonNode signedOut =
        body(
            callWithHeaders(
                "home-for-you-device",
                "GET",
                "/api/v1/public/home?tab=for-you&limit=20&seen=hide",
                null,
                device(),
                200));
    assertThat(keys(signedOut))
        .containsExactly(
            uri("rin", 102),
            uri("yui", 103),
            "post:" + post,
            uri("mio", 101),
            "note:" + mine,
            "note:" + theirs);

    callWithHeaders(
        "home-for-you-seen",
        "POST",
        "/api/v1/federation/seen",
        Map.of("uris", List.of("kurl:note:" + theirs, uri("rin", 102))),
        device(),
        202);
    captureBackgroundWork(
        "home-for-you-seen-flush",
        () -> {
          assertThat(localSeen.flush()).isEqualTo(1);
          assertThat(remoteSeen.flush()).isEqualTo(1);
        });
    Thread.sleep(5);
    JsonNode hidden =
        body(
            callWithHeaders(
                "home-for-you-device-hide",
                "GET",
                "/api/v1/public/home?tab=for-you&limit=20&seen=hide",
                null,
                device(),
                200));
    assertThat(keys(hidden))
        .containsExactly(uri("yui", 103), "post:" + post, uri("mio", 101), "note:" + mine);
  }
}
