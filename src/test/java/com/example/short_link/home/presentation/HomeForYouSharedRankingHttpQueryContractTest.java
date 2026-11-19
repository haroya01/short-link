package com.example.short_link.home.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.example.short_link.common.seen.SeenParameters;
import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.domain.UserEntity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(
    properties = {
      "short-link.federation.latest.pull=true",
      "short-link.federation.latest.hosts=hr.example",
      "short-link.federation.latest.backfill-pages=1"
    })
class HomeForYouSharedRankingHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final String HOST = "hr.example";
  private static final String DEVICE = "7e1d2c3b-4a5f-4b6c-9d8e-0f1a2b3c4d5e";
  private static final String TIMELINE =
      "https://" + HOST + "/api/v1/timelines/public?local=true&limit=40";
  private static final String FIRST_PAGE = "/api/v1/public/home?tab=for-you&limit=3";

  @MockitoBean private FederationHttp http;
  @MockitoSpyBean private Clock clock;
  @Autowired private LatestPosts latest;
  @Autowired private SigningKeys signingKeys;

  @BeforeEach
  void instanceKey() {
    signingKeys.forInstance();
  }

  @Test
  void readersNobodyCanTellApartShareTheMinutesRankingWhileAMemberAndADeviceAreRankedEveryTime()
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
    Instant written = Instant.now();
    long mine = note(owner, written.minus(Duration.ofMinutes(5)), null);
    long theirs = note(stranger, written.minus(Duration.ofMinutes(15)), null);
    long post = post(stranger, written.minus(Duration.ofMinutes(25)));
    Instant minute = written.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
    Instant nextMinute = minute.plus(1, ChronoUnit.MINUTES);

    doReturn(minute.plusSeconds(5)).when(clock).instant();
    JsonNode first =
        body(
            callWithHeaders(
                "home-for-you-anonymous-first", "GET", FIRST_PAGE, null, Map.of(), 200));
    assertThat(keys(first)).containsExactly(uri("yui", 103), "post:" + post, uri("mio", 101));
    assertThat(first.path("next").asString()).isEqualTo("r" + minute.toEpochMilli() + ".3");

    long arrived = note(stranger, minute.plusSeconds(30), "ko");
    doReturn(minute.plusSeconds(40)).when(clock).instant();
    JsonNode another =
        body(
            callWithHeaders(
                "home-for-you-anonymous-shared",
                "GET",
                FIRST_PAGE,
                null,
                Map.of("Accept-Language", "ko-KR,ko;q=0.9"),
                200));
    assertThat(keys(another)).isEqualTo(keys(first));
    assertThat(another.path("next").asString()).isEqualTo(first.path("next").asString());

    doReturn(nextMinute.plusSeconds(5)).when(clock).instant();
    JsonNode later =
        body(
            callWithHeaders(
                "home-for-you-anonymous-next-minute", "GET", FIRST_PAGE, null, Map.of(), 200));
    assertThat(keys(later)).contains("note:" + arrived);
    assertThat(later.path("next").asString()).isEqualTo("r" + nextMinute.toEpochMilli() + ".3");

    doReturn(nextMinute.plusSeconds(20)).when(clock).instant();
    JsonNode onward =
        body(
            callWithHeaders(
                "home-for-you-anonymous-onward",
                "GET",
                FIRST_PAGE + "&cursor=" + first.path("next").asString(),
                null,
                Map.of(),
                200));
    assertThat(keys(onward)).containsExactly("note:" + mine, "note:" + theirs, uri("rin", 102));
    assertThat(onward.path("next").isNull()).isTrue();

    doCallRealMethod().when(clock).instant();
    List<String> memberCursors = new ArrayList<>();
    List<String> deviceCursors = new ArrayList<>();
    for (int asked = 0; asked < 2; asked++) {
      memberCursors.add(
          body(call("home-for-you-member-again", "GET", FIRST_PAGE, null, token, 200))
              .path("next")
              .asString());
      deviceCursors.add(
          body(callWithHeaders(
                  "home-for-you-device-again",
                  "GET",
                  FIRST_PAGE,
                  null,
                  Map.of(SeenParameters.DEVICE_HEADER, DEVICE),
                  200))
              .path("next")
              .asString());
      Thread.sleep(5);
    }
    assertThat(memberCursors).doesNotHaveDuplicates().allMatch(cursor -> cursor.startsWith("r"));
    assertThat(deviceCursors).doesNotHaveDuplicates().allMatch(cursor -> cursor.startsWith("r"));
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

  private long note(UserEntity author, Instant at, String language) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, language) VALUES (?, 'here', ?, ?)",
        author.getId(),
        LocalDateTime.ofInstant(at, ZoneOffset.UTC),
        language);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.getId());
  }

  private long post(UserEntity author, Instant at) {
    String slug = "fy-" + UUID.randomUUID().toString().substring(0, 8);
    LocalDateTime published = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?, 500, 'ko')",
        author.getId(),
        slug,
        slug,
        published,
        published,
        published);
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
}
