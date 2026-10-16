package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.support.DiscoverableBodies;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class PostAudienceHttpQueryContractTest extends ContentHttpJourneySupport {

  private static final String BODY = DiscoverableBodies.PARAGRAPH;
  private static final String PASSWORD = "열려라 참깨, 200자까지";
  private static final GeoLocation JAPAN = new GeoLocation("JP", null, null);

  private enum Viewer {
    ANONYMOUS,
    NON_FOLLOWER,
    FOLLOWER,
    PENDING_FOLLOWER,
    OWNER,
    GRANT_HOLDER,
    WRONG_KEY,
    RIGHT_KEY
  }

  private static final String[][] TABLE = {
    {"PUBLIC", "open", "F F F F F F F F"},
    {"UNLISTED", "open", "H H H H F H H F"},
    {"FOLLOWERS", "open", "Lf Lf F Lf F Lf Lf Lf"},
    {"PASSWORD", "open", "Lp Lp Lp Lp F F Lp Lp"},
    {"PUBLIC", "blocked", "Lr Lr Lr Lr F Lr Lr Lr"},
    {"UNLISTED", "blocked", "H H H H F H H Lr"},
    {"FOLLOWERS", "blocked", "Lr Lr Lr Lr F Lr Lr Lr"},
    {"PASSWORD", "blocked", "Lr Lr Lr Lr F Lr Lr Lr"},
  };

  private static final Map<String, String> REASONS =
      Map.of("Lf", "FOLLOWERS", "Lp", "PASSWORD", "Lr", "REGION");

  @MockitoBean private GeoIpResolver geoIp;
  @LocalServerPort private int port;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @BeforeEach
  void everyReaderIsInJapan() {
    when(geoIp.resolve(any())).thenReturn(JAPAN);
  }

  @Test
  void anAuthorChoosesWhoReadsEachPostAndEveryReadPathAgrees() throws Exception {
    anAuthorChoosesWhoReadsAPostAndEachReaderGetsTheirShare();
    everyPublicReadPathAnswersEveryReaderAsThePolicyDoes();
    passwordAttemptsStopAtTenAndACountryBlockSpendsNone();
    protectedReadsAreNeverStoredInASharedCache();
    anUnlistedFirstPublishTellsNoFollower();
    theOwnerSettingsRejectWhatTheyCannotStore();

    verifyContracts();
  }

  private void anAuthorChoosesWhoReadsAPostAndEachReaderGetsTheirShare() throws Exception {
    long postId = createPost("audience-post-create", "protected-essay", "A protected essay");
    step(
        "audience-body-write",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", BODY),
        200);
    publish("audience-publish", postId);
    String detail = publicPostPath("protected-essay");

    long version =
        jdbc.queryForObject("SELECT content_version FROM posts WHERE id = ?", Long.class, postId);
    long revisions = count("post_revision", "post_id = ?", postId);
    JsonNode followers =
        step(
            "audience-owner-set-followers",
            "PATCH",
            postPath(postId),
            author,
            Map.of("audience", "FOLLOWERS"),
            200);
    assertThat(followers.path("audience").asText()).isEqualTo("FOLLOWERS");
    assertThat(followers.path("contentVersion").asLong()).isEqualTo(version);
    assertThat(count("post_revision", "post_id = ?", postId)).isEqualTo(revisions);

    JsonNode locked = get("audience-followers-read-locked", detail, outsider);
    assertThat(locked.at("/post/locked/reason").asText()).isEqualTo("FOLLOWERS");
    assertThat(locked.at("/post/excerpt").isNull()).isTrue();
    assertThat(locked.path("blocks")).hasSize(1);
    assertThat(locked.at("/blocks/0/content").asText()).isEqualTo("팔로워만 볼 수 있는 글이에요");

    follow(reader, author);
    JsonNode opened = get("audience-followers-read-follower", detail, reader);
    assertThat(opened.at("/post/locked").isMissingNode()).isTrue();
    assertThat(opened.at("/blocks/0/content").asText()).isEqualTo(BODY);

    JsonNode password =
        step(
            "audience-owner-set-password",
            "PATCH",
            postPath(postId),
            author,
            Map.of("audience", "PASSWORD", "password", PASSWORD),
            200);
    assertThat(password.path("hasPassword").asBoolean()).isTrue();
    assertThat(password.has("password")).isFalse();
    String unlock = detail + "/unlock";
    JsonNode wrong =
        step("audience-unlock-fail", "POST", unlock, null, Map.of("password", "nope"), 401);
    assertThat(wrong.path("code").asText()).isEqualTo("POST_PASSWORD_INVALID");
    JsonNode granted =
        step("audience-unlock-ok", "POST", unlock, null, Map.of("password", PASSWORD), 200);
    String grant = granted.path("grant").asText();
    assertThat(granted.path("expiresAt").asText()).isNotBlank();
    JsonNode unlocked =
        step(
            "audience-password-read-grant",
            "GET",
            detail,
            null,
            null,
            200,
            Map.of("X-Post-Grant", grant));
    assertThat(unlocked.at("/blocks/0/content").asText()).isEqualTo(BODY);

    JsonNode unlisted =
        step(
            "audience-owner-set-unlisted",
            "PATCH",
            postPath(postId),
            author,
            Map.of("audience", "UNLISTED"),
            200);
    String key =
        jdbc.queryForObject("SELECT unlisted_key FROM posts WHERE id = ?", String.class, postId);
    assertThat(key).hasSize(32);
    assertThat(unlisted.path("unlistedUrl").asText())
        .isEqualTo("https://blog.kurl.me/@" + author.username() + "/protected-essay?k=" + key);
    JsonNode hidden = step("audience-unlisted-read-without-key", "GET", detail, null, null, 404);
    assertThat(hidden.path("code").asText()).isEqualTo("POST_NOT_FOUND");
    step(
        "audience-unlisted-read-with-key",
        "GET",
        detail,
        null,
        null,
        200,
        Map.of("X-Post-Key", key));
    JsonNode rotated =
        step(
            "audience-owner-rotate-key",
            "POST",
            postPath(postId) + "/unlisted-key/rotate",
            author,
            null,
            200);
    String newKey =
        jdbc.queryForObject("SELECT unlisted_key FROM posts WHERE id = ?", String.class, postId);
    assertThat(newKey).isNotEqualTo(key);
    assertThat(rotated.path("unlistedUrl").asText()).endsWith("?k=" + newKey);
    assertThat(send("GET", detail, null, null, Map.of("X-Post-Key", key)).statusCode())
        .isEqualTo(404);

    step(
        "audience-owner-block-country",
        "PATCH",
        postPath(postId),
        author,
        Map.of("audience", "PUBLIC", "blockedCountries", List.of("jp", "KR")),
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT blocked_countries FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo("JP,KR");
    JsonNode region = get("audience-region-blocked-read", detail, reader);
    assertThat(region.at("/post/locked/reason").asText()).isEqualTo("REGION");
    assertThat(region.at("/post/regionRestricted").asBoolean()).isTrue();
    assertThat(region.at("/post/blockedCountries").isMissingNode()).isTrue();
    JsonNode render =
        step(
            "audience-region-server-render",
            "GET",
            detail,
            null,
            null,
            200,
            Map.of("X-Kurl-Render", "server"));
    assertThat(render.at("/post/locked/reason").asText()).isEqualTo("REGION_CHECK");
  }

  private void everyPublicReadPathAnswersEveryReaderAsThePolicyDoes() throws Exception {
    lockAccount(author);
    follow(reader, author);
    Actor pending = person("journey-pending");
    jdbc.update(
        "INSERT INTO follow_request (follower_id, following_id, created_at)"
            + " VALUES (?, ?, CURRENT_TIMESTAMP(6))",
        pending.id(),
        author.id());

    List<Row> rows = new ArrayList<>();
    for (String[] row : TABLE) {
      boolean blocked = "blocked".equals(row[1]);
      String slug = row[0].toLowerCase() + (blocked ? "-jp" : "-open");
      rows.add(new Row(protectedPost(slug, row[0], blocked), row[2].split(" ")));
    }

    Map<Viewer, Actor> actors = new LinkedHashMap<>();
    actors.put(Viewer.NON_FOLLOWER, outsider);
    actors.put(Viewer.FOLLOWER, reader);
    actors.put(Viewer.PENDING_FOLLOWER, pending);
    actors.put(Viewer.OWNER, author);
    actors.put(Viewer.GRANT_HOLDER, outsider);
    actors.put(Viewer.WRONG_KEY, outsider);
    actors.put(Viewer.RIGHT_KEY, outsider);

    List<String> mismatches = new ArrayList<>();
    for (Row row : rows) {
      for (Viewer viewer : Viewer.values()) {
        String expected = row.expected()[viewer.ordinal()];
        Actor actor = actors.get(viewer);
        Map<String, String> credentials = credentials(viewer, row.post());
        checkReads(row.post(), viewer, actor, credentials, expected, mismatches);
        if (actor != null) {
          checkWrites(row.post(), viewer, actor, credentials, expected, mismatches);
        }
      }
    }
    checkLists(rows, actors, mismatches);

    assertThat(mismatches).isEmpty();
  }

  private void passwordAttemptsStopAtTenAndACountryBlockSpendsNone() throws Exception {
    Post open = protectedPost("password-limit", "PASSWORD", false);
    Post blocked = protectedPost("password-region", "PASSWORD", true);

    HttpResponse<String> region =
        send("POST", unlockPath(blocked), null, Map.of("password", PASSWORD), Map.of());
    assertThat(region.statusCode()).isEqualTo(403);
    assertThat(json.readTree(region.body()).path("reason").asText()).isEqualTo("REGION");

    for (int attempt = 1; attempt <= 10; attempt++) {
      assertThat(
              send("POST", unlockPath(open), null, Map.of("password", "wrong-" + attempt), Map.of())
                  .statusCode())
          .isEqualTo(401);
    }
    HttpResponse<String> exceeded =
        send("POST", unlockPath(open), null, Map.of("password", PASSWORD), Map.of());
    assertThat(exceeded.statusCode()).isEqualTo(429);
    assertThat(json.readTree(exceeded.body()).path("code").asText())
        .isEqualTo("POST_PASSWORD_ATTEMPTS_EXCEEDED");
    assertThat(Long.parseLong(exceeded.headers().firstValue("Retry-After").orElseThrow()))
        .isBetween(1L, 900L);
  }

  private void protectedReadsAreNeverStoredInASharedCache() throws Exception {
    Post open = protectedPost("cache-open", "PUBLIC", false);
    Post followers = protectedPost("cache-followers", "FOLLOWERS", false);
    Post region = protectedPost("cache-region", "PUBLIC", true);

    HttpResponse<String> openRead = send("GET", detailPath(open), null, null, Map.of());
    assertThat(openRead.headers().firstValue("X-Robots-Tag")).isEmpty();
    assertThat(openRead.headers().firstValue("Cache-Control").orElse("")).doesNotContain("private");
    for (Post post : List.of(followers, region)) {
      for (String path :
          List.of(detailPath(post), "/api/v1/public/posts/" + post.id() + "/comments")) {
        HttpResponse<String> read = send("GET", path, author, null, Map.of());
        assertThat(read.statusCode()).as(path).isEqualTo(200);
        assertThat(read.headers().firstValue("Cache-Control")).contains("private, no-store");
        assertThat(read.headers().firstValue("X-Robots-Tag")).contains("noindex");
      }
    }
    HttpResponse<String> list =
        send(
            "GET", "/api/v1/public/profiles/" + author.username() + "/posts", null, null, Map.of());
    assertThat(list.headers().firstValue("Cache-Control")).contains("private, no-store");
  }

  private void anUnlistedFirstPublishTellsNoFollower() throws Exception {
    follow(reader, author);
    long before = count("notification", "recipient_user_id = ?", reader.id());
    long quiet = createDraft("quiet-unlisted");
    send("PATCH", postPath(quiet), author, Map.of("audience", "UNLISTED"), Map.of());
    publishQuietly(quiet);
    awaitAsyncWork();
    assertThat(count("notification", "recipient_user_id = ?", reader.id())).isEqualTo(before);

    long loud = createDraft("loud-followers");
    send("PATCH", postPath(loud), author, Map.of("audience", "FOLLOWERS"), Map.of());
    publishQuietly(loud);
    awaitAsyncWork();
    assertThat(count("notification", "recipient_user_id = ?", reader.id())).isEqualTo(before + 1);
  }

  private void theOwnerSettingsRejectWhatTheyCannotStore() throws Exception {
    long postId = createDraft("settings-draft");
    Map<String, Object[]> cases = new LinkedHashMap<>();
    cases.put("POST_AUDIENCE_INVALID", new Object[] {Map.of("audience", "FRIENDS")});
    cases.put("POST_PASSWORD_REQUIRED", new Object[] {Map.of("audience", "PASSWORD")});
    cases.put("POST_PASSWORD_LENGTH", new Object[] {Map.of("password", "abc")});
    cases.put(
        "POST_BLOCKED_COUNTRIES_INVALID",
        new Object[] {Map.of("blockedCountries", List.of("JP", "ZZ"))});
    for (Map.Entry<String, Object[]> entry : cases.entrySet()) {
      HttpResponse<String> response =
          send("PATCH", postPath(postId), author, entry.getValue()[0], Map.of());
      assertThat(response.statusCode()).as(entry.getKey()).isEqualTo(400);
      assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(entry.getKey());
    }
    assertThat(
            send("PATCH", postPath(postId), author, Map.of("password", "가".repeat(200)), Map.of())
                .statusCode())
        .isEqualTo(200);
    HttpResponse<String> cleared =
        send(
            "PATCH",
            postPath(postId),
            author,
            Map.of("audience", "PASSWORD", "blockedCountries", List.of()),
            Map.of());
    assertThat(cleared.statusCode()).isEqualTo(200);
    assertThat(json.readTree(cleared.body()).path("blockedCountries")).isEmpty();
  }

  private void checkReads(
      Post post,
      Viewer viewer,
      Actor actor,
      Map<String, String> credentials,
      String expected,
      List<String> mismatches)
      throws Exception {
    String cell = post.slug() + " as " + viewer;
    HttpResponse<String> detail = send("GET", detailPath(post), actor, null, credentials);
    if (expected.equals("H")) {
      expect(mismatches, cell + " detail", detail.statusCode(), 404);
    } else {
      expect(mismatches, cell + " detail", detail.statusCode(), 200);
      JsonNode body = json.readTree(detail.body());
      String reason = body.at("/post/locked/reason").asText(null);
      expect(mismatches, cell + " detail lock", reason, REASONS.get(expected));
      if (reason != null) {
        expect(mismatches, cell + " detail excerpt", body.at("/post/excerpt").isNull(), true);
        expect(mismatches, cell + " detail body", body.path("blocks").size(), 1);
      } else {
        expect(mismatches, cell + " detail body", body.at("/blocks/0/content").asText(), BODY);
      }
    }
    expectSingle(
        mismatches,
        cell + " markdown",
        send("GET", detailPath(post) + "/markdown", actor, null, credentials),
        expected,
        200);
    for (String list :
        List.of(
            "/api/v1/public/posts/" + post.id() + "/comments",
            "/api/v1/public/posts/" + post.id() + "/highlights",
            "/api/v1/public/highlights/" + post.highlightId() + "/replies")) {
      HttpResponse<String> response = send("GET", list, actor, null, credentials);
      if (expected.equals("H")) {
        expect(mismatches, cell + " " + list, response.statusCode() + response.body(), "200[]");
      } else if (expected.equals("F")) {
        expect(mismatches, cell + " " + list, response.statusCode(), 200);
      } else {
        expectLocked(mismatches, cell + " " + list, response, REASONS.get(expected));
      }
    }
    long views = viewCount(post);
    HttpResponse<String> beacon =
        send("POST", detailPath(post) + "/view", actor, null, credentials);
    expect(mismatches, cell + " view beacon", beacon.statusCode(), 202);
    expect(
        mismatches,
        cell + " view counted",
        viewCount(post) - views,
        expected.equals("F") ? 1L : 0L);
  }

  private void checkWrites(
      Post post,
      Viewer viewer,
      Actor actor,
      Map<String, String> credentials,
      String expected,
      List<String> mismatches)
      throws Exception {
    String cell = post.slug() + " as " + viewer;
    String path = postPath(post.id());
    expectSingle(
        mismatches,
        cell + " like status",
        send("GET", path + "/like", actor, null, credentials),
        expected,
        200);
    expectSingle(
        mismatches,
        cell + " like",
        send("PUT", path + "/like", actor, null, credentials),
        expected,
        200);
    expectSingle(
        mismatches,
        cell + " bookmark",
        send("PUT", path + "/bookmark", actor, null, credentials),
        expected,
        200);
    expectSingle(
        mismatches,
        cell + " comment",
        send("POST", path + "/comments", actor, Map.of("body", "a reader's comment"), credentials),
        expected,
        201);
    expectSingle(
        mismatches,
        cell + " highlight",
        send(
            "POST",
            path + "/highlights",
            actor,
            Map.of(
                "blockOrder",
                0,
                "startOffset",
                11 + viewer.ordinal(),
                "endOffset",
                16 + viewer.ordinal(),
                "quote",
                BODY.substring(11 + viewer.ordinal(), 16 + viewer.ordinal())),
            credentials),
        expected,
        201);
    expectSingle(
        mismatches,
        cell + " reply",
        send(
            "POST",
            "/api/v1/highlights/" + post.highlightId() + "/replies",
            actor,
            Map.of("body", "a reader's reply"),
            credentials),
        expected,
        201);
    expectSingle(
        mismatches,
        cell + " comment like",
        send("POST", "/api/v1/comments/" + post.commentId() + "/like", actor, null, credentials),
        expected,
        200);
    HttpResponse<String> unlike = send("DELETE", path + "/like", actor, null, credentials);
    expect(mismatches, cell + " unlike", unlike.statusCode(), expected.equals("H") ? 404 : 200);
    if (expected.startsWith("L")) {
      expect(
          mismatches,
          cell + " unlike count",
          json.readTree(unlike.body()).path("likeCount").asLong(),
          0L);
    }
    expect(
        mismatches,
        cell + " unbookmark",
        send("DELETE", path + "/bookmark", actor, null, credentials).statusCode(),
        200);
    boolean readBefore =
        count("post_read", "user_id = ? AND post_id = ?", actor.id(), post.id()) > 0;
    HttpResponse<String> read = send("POST", path + "/read", actor, null, credentials);
    expect(mismatches, cell + " read beacon", read.statusCode(), 204);
    expect(
        mismatches,
        cell + " read recorded",
        count("post_read", "user_id = ? AND post_id = ?", actor.id(), post.id()) > 0,
        readBefore || expected.equals("F"));
  }

  private void checkLists(List<Row> rows, Map<Viewer, Actor> actors, List<String> mismatches)
      throws Exception {
    for (Viewer viewer : Viewer.values()) {
      Actor actor = actors.get(viewer);
      Map<String, String> credentials =
          viewer == Viewer.GRANT_HOLDER || viewer == Viewer.RIGHT_KEY || viewer == Viewer.WRONG_KEY
              ? credentials(viewer, rows.get(3).post())
              : Map.of();
      Viewer asListed =
          Set.of(Viewer.GRANT_HOLDER, Viewer.WRONG_KEY, Viewer.RIGHT_KEY).contains(viewer)
              ? Viewer.NON_FOLLOWER
              : viewer;
      JsonNode profile =
          json.readTree(
              send(
                      "GET",
                      "/api/v1/public/profiles/" + author.username() + "/posts",
                      actor,
                      null,
                      credentials)
                  .body());
      Map<String, String> listed = new HashMap<>();
      for (JsonNode item : profile.path("posts")) {
        listed.put(item.path("slug").asText(), item.at("/locked/reason").asText(null));
      }
      JsonNode discover =
          json.readTree(
              send("GET", "/api/v1/public/posts?sort=recent&size=100", actor, null, credentials)
                  .body());
      Map<String, JsonNode> discovered = new HashMap<>();
      for (JsonNode item : discover.path("items")) {
        if (item.at("/author/id").asLong() == author.id()) {
          discovered.put(item.path("slug").asText(), item);
        }
      }
      for (Row row : rows) {
        String expected = row.expected()[asListed.ordinal()];
        String cell = row.post().slug() + " listed for " + viewer;
        if (expected.equals("H")) {
          expect(mismatches, cell, listed.containsKey(row.post().slug()), false);
        } else {
          expect(mismatches, cell, listed.containsKey(row.post().slug()), true);
          expect(mismatches, cell + " lock", listed.get(row.post().slug()), REASONS.get(expected));
        }
        boolean discoverable = row.post().audience().equals("PUBLIC");
        expect(
            mismatches,
            row.post().slug() + " discovered by " + viewer,
            discovered.containsKey(row.post().slug()),
            discoverable);
        if (discoverable && row.post().blocked()) {
          JsonNode card = discovered.get(row.post().slug());
          expect(mismatches, cell + " card excerpt", card.path("excerpt").isNull(), true);
          expect(mismatches, cell + " card mark", card.path("regionRestricted").asBoolean(), true);
        }
      }
    }
  }

  private Map<String, String> credentials(Viewer viewer, Post post) {
    return switch (viewer) {
      case GRANT_HOLDER -> post.grant() == null ? Map.of() : Map.of("X-Post-Grant", post.grant());
      case WRONG_KEY -> Map.of("X-Post-Key", "not-the-key-of-this-post-000000");
      case RIGHT_KEY -> post.key() == null ? Map.of() : Map.of("X-Post-Key", post.key());
      default -> Map.of();
    };
  }

  private Post protectedPost(String slug, String audience, boolean blocked) throws Exception {
    long id = createDraft(slug);
    publishQuietly(id);
    long commentId =
        json.readTree(
                send(
                        "POST",
                        postPath(id) + "/comments",
                        author,
                        Map.of("body", "the author's comment"),
                        Map.of())
                    .body())
            .path("id")
            .asLong();
    long highlightId =
        json.readTree(
                send(
                        "POST",
                        postPath(id) + "/highlights",
                        author,
                        Map.of(
                            "blockOrder", 0,
                            "startOffset", 0,
                            "endOffset", 10,
                            "quote", BODY.substring(0, 10)),
                        Map.of())
                    .body())
            .path("id")
            .asLong();
    Map<String, Object> settings = new HashMap<>();
    settings.put("audience", audience);
    settings.put("password", PASSWORD);
    settings.put("blockedCountries", blocked ? List.of("JP") : List.of());
    HttpResponse<String> set = send("PATCH", postPath(id), author, settings, Map.of());
    assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
    String key =
        jdbc.queryForObject("SELECT unlisted_key FROM posts WHERE id = ?", String.class, id);
    String grant = null;
    if (audience.equals("PASSWORD")) {
      when(geoIp.resolve(any())).thenReturn(new GeoLocation("KR", null, null));
      HttpResponse<String> unlocked =
          send(
              "POST",
              "/api/v1/public/profiles/" + author.username() + "/posts/" + slug + "/unlock",
              null,
              Map.of("password", PASSWORD),
              Map.of());
      when(geoIp.resolve(any())).thenReturn(JAPAN);
      assertThat(unlocked.statusCode()).as(unlocked.body()).isEqualTo(200);
      grant = json.readTree(unlocked.body()).path("grant").asText();
    }
    return new Post(id, slug, audience, blocked, key, grant, commentId, highlightId);
  }

  private long createDraft(String slug) throws Exception {
    HttpResponse<String> created =
        send(
            "POST",
            "/api/v1/posts",
            author,
            Map.of("slug", slug, "title", "Title " + slug, "languageTag", "ko"),
            Map.of());
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    long id = json.readTree(created.body()).path("id").asLong();
    HttpResponse<String> body =
        send("PUT", postPath(id) + "/markdown", author, Map.of("markdown", BODY), Map.of());
    assertThat(body.statusCode()).as(body.body()).isEqualTo(200);
    return id;
  }

  private void publishQuietly(long id) throws Exception {
    HttpResponse<String> published =
        send("POST", postPath(id) + "/publish", author, null, Map.of());
    assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
  }

  private void follow(Actor follower, Actor followed) {
    jdbc.update(
        "INSERT IGNORE INTO user_follow (follower_id, following_id, created_at)"
            + " VALUES (?, ?, CURRENT_TIMESTAMP(6))",
        follower.id(),
        followed.id());
  }

  private void lockAccount(Actor actor) {
    jdbc.update("UPDATE users SET locked = TRUE WHERE id = ?", actor.id());
  }

  private long viewCount(Post post) {
    return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, post.id());
  }

  private String detailPath(Post post) {
    return "/api/v1/public/profiles/" + author.username() + "/posts/" + post.slug();
  }

  private String unlockPath(Post post) {
    return detailPath(post) + "/unlock";
  }

  private void expectSingle(
      List<String> mismatches, String cell, HttpResponse<String> response, String expected, int ok)
      throws Exception {
    if (expected.equals("F")) {
      expect(mismatches, cell, response.statusCode(), ok);
    } else if (expected.equals("H")) {
      expect(mismatches, cell, response.statusCode(), 404);
      expect(mismatches, cell + " code", code(response), "POST_NOT_FOUND");
    } else {
      expectLocked(mismatches, cell, response, REASONS.get(expected));
    }
  }

  private void expectLocked(
      List<String> mismatches, String cell, HttpResponse<String> response, String reason)
      throws Exception {
    expect(mismatches, cell, response.statusCode(), 403);
    JsonNode body = json.readTree(response.body());
    expect(mismatches, cell + " code", body.path("code").asText(), "POST_LOCKED");
    expect(mismatches, cell + " reason", body.path("reason").asText(), reason);
  }

  private String code(HttpResponse<String> response) throws Exception {
    return response.body().isBlank() ? null : json.readTree(response.body()).path("code").asText();
  }

  private static void expect(List<String> mismatches, String cell, Object actual, Object expected) {
    if (!java.util.Objects.equals(actual, expected)) {
      mismatches.add(cell + ": expected " + expected + " but was " + actual);
    }
  }

  private HttpResponse<String> send(
      String method, String path, Actor actor, Object body, Map<String, String> headers)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30));
    headers.forEach(request::header);
    if (actor != null) request.header("Authorization", "Bearer " + actor.token());
    HttpRequest.BodyPublisher payload = HttpRequest.BodyPublishers.noBody();
    if (body != null) {
      request.header("Content-Type", "application/json");
      payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
    }
    return http.send(request.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
  }

  private record Post(
      long id,
      String slug,
      String audience,
      boolean blocked,
      String key,
      String grant,
      long commentId,
      long highlightId) {}

  private record Row(Post post, String[] expected) {}
}
