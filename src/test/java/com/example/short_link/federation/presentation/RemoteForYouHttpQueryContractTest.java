package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.federation.application.trends.RemoteReach;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(
    properties = {
      "short-link.federation.latest.pull=true",
      "short-link.federation.latest.hosts=fy.example",
      "short-link.federation.latest.backfill-pages=1",
      "short-link.federation.reach.pause=0s"
    })
class RemoteForYouHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final String HOST = "fy.example";
  private static final String DEVICE = "0b6f2a3b-4d5e-4f60-9a71-92b3c4d5e6f7";
  private static final String TIMELINE =
      "https://" + HOST + "/api/v1/timelines/public?local=true&limit=40";

  @MockitoBean private FederationHttp http;
  @Autowired private LatestPosts latest;
  @Autowired private RemoteSeen seen;
  @Autowired private RemoteReach reach;
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

  private static List<String> uris(JsonNode page) {
    List<String> uris = new ArrayList<>();
    page.path("items").forEach(item -> uris.add(item.path("uri").asString()));
    return uris;
  }

  private static Map<String, String> device() {
    return Map.of(RemoteTrendsController.DEVICE_HEADER, DEVICE);
  }

  @Test
  void keptPostsAreRankedForEachReaderPagedFromOneRankingAndTheirCountsAskedAgain()
      throws Exception {
    when(http.getJson(eq(URI.create(TIMELINE)), any(), any()))
        .thenReturn(
            ok(
                "["
                    + status(103, "yui", "ko", 3, Duration.ofMinutes(10))
                        .replace("<p>yui 103</p>", "<p>yui 103 https://youtu.be/abc</p>")
                        .replace(
                            "\"card\":null",
                            "\"card\":{\"url\":\"https://www.youtube.com/watch?v=abc\","
                                + "\"title\":\"A video\",\"description\":\"About it\","
                                + "\"image\":\"https://i.example/a.jpg\"}")
                    + ","
                    + status(102, "rin", "ja", 50, Duration.ofMinutes(20))
                    + ","
                    + status(101, "yui", "ko", 0, Duration.ofMinutes(30))
                    + "]"));
    for (String user : List.of("yui", "rin", "mio")) {
      when(http.get(eq(URI.create("https://" + HOST + "/users/" + user)), any()))
          .thenReturn(ok(person(user)));
    }
    assertThat(latest.pull()).isEqualTo(3);

    JsonNode anonymous =
        body(
            callWithHeaders(
                "federation-for-you-anonymous",
                "GET",
                "/api/v1/federation/for-you?limit=2",
                null,
                Map.of("Accept-Language", "ja-JP,ja;q=0.9"),
                200));
    assertThat(uris(anonymous)).containsExactly(uri("rin", 102), uri("yui", 103));
    assertThat(anonymous.path("items").get(1).path("linkPreview").path("url").asString())
        .isEqualTo("https://www.youtube.com/watch?v=abc");
    assertThat(anonymous.path("items").get(1).path("linkPreview").path("title").asString())
        .isEqualTo("A video");
    assertThat(anonymous.path("next").asString()).startsWith("f").endsWith(".2");

    JsonNode first =
        body(
            call(
                "federation-for-you-first",
                "GET",
                "/api/v1/federation/for-you?limit=2",
                null,
                token,
                200));
    assertThat(uris(first)).containsExactly(uri("yui", 103), uri("yui", 101));
    JsonNode next =
        body(
            call(
                "federation-for-you-next",
                "GET",
                "/api/v1/federation/for-you?limit=2&cursor=" + first.path("next").asString(),
                null,
                token,
                200));
    assertThat(uris(next)).containsExactly(uri("rin", 102));
    assertThat(next.path("next").isNull()).isTrue();

    callWithHeaders(
        "federation-seen-dwell",
        "POST",
        "/api/v1/federation/seen",
        Map.of(
            "uris",
            List.of(uri("yui", 103)),
            "feed",
            "for-you",
            "dwell",
            List.of(Map.of("uri", uri("yui", 103), "ms", 12_000))),
        device(),
        202);
    captureBackgroundWork(
        "federation-seen-flush-dwell", () -> assertThat(seen.flush()).isEqualTo(1));
    assertThat(count("SELECT COUNT(*) FROM remote_dwell WHERE dwell_ms = 12000")).isEqualTo(1);

    Thread.sleep(5);
    JsonNode deviceFirst =
        body(
            callWithHeaders(
                "federation-for-you-device",
                "GET",
                "/api/v1/federation/for-you?limit=20",
                null,
                device(),
                200));
    assertThat(uris(deviceFirst))
        .containsExactly(uri("yui", 101), uri("rin", 102), uri("yui", 103));
    assertThat(deviceFirst.path("seenFrom").asInt()).isEqualTo(2);
    Thread.sleep(5);
    JsonNode deviceHome =
        body(
            callWithHeaders(
                "federation-for-you-device-hide",
                "GET",
                "/api/v1/federation/for-you?limit=20&seen=hide",
                null,
                device(),
                200));
    assertThat(uris(deviceHome)).containsExactly(uri("yui", 101), uri("rin", 102));
    assertThat(deviceHome.has("seenFrom")).isFalse();
    assertThat(deviceHome.path("next").isNull()).isTrue();

    StringJoiner more = new StringJoiner(",", "[", "]");
    for (int id = 200; id < 230; id++) {
      more.add(status(id, id % 2 == 0 ? "mio" : "rin", "ko", id % 5, Duration.ofMinutes(5)));
    }
    when(http.getJson(eq(URI.create(TIMELINE + "&since_id=103")), any(), any()))
        .thenReturn(ok(more.toString()));
    assertThat(latest.pull()).isEqualTo(30);
    JsonNode wide =
        body(
            call(
                "federation-for-you-wide",
                "GET",
                "/api/v1/federation/for-you?limit=2",
                null,
                strangerToken,
                200));
    assertThat(uris(wide)).hasSize(2);

    jdbc.update(
        "UPDATE remote_note_reach SET next_check_at = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE"
            + " WHERE ask_host = ?",
        HOST);
    when(http.getJson(
            argThat(asked -> asked != null && asked.getPath().equals("/api/v1/statuses")),
            any(),
            any()))
        .thenAnswer(
            call -> {
              StringJoiner answered = new StringJoiner(",", "[", "]");
              for (String part : call.getArgument(0, URI.class).getRawQuery().split("&")) {
                String id = part.substring(part.indexOf('=') + 1);
                answered.add(
                    "{\"id\":\"%s\",\"favourites_count\":7,\"reblogs_count\":1,\"replies_count\":2}"
                        .formatted(id));
              }
              return ok(answered.toString());
            });
    captureBackgroundWork("federation-reach-round", () -> assertThat(reach.round()).isEqualTo(33));
    assertThat(
            count(
                "SELECT COUNT(*) FROM remote_note_reach WHERE ask_host = ? AND likes = 7"
                    + " AND checks = 1",
                HOST))
        .isEqualTo(33);
  }
}
