package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.TrendingTopics;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(
    properties = {
      "short-link.federation.trends.hosts="
          + "ta.topics.example,tb.topics.example,tc.topics.example,td.topics.example",
      "short-link.federation.trends.patience=2s"
    })
class FederationTopicsHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String A = "ta.topics.example";
  private static final String B = "tb.topics.example";
  private static final String BLOCKED = "tc.topics.example";
  private static final String QUIET = "td.topics.example";
  private static final DateTimeFormatter STORED =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

  @MockitoBean private FederationHttp http;
  @Autowired private TrendingTopics topics;
  @Autowired private SigningKeys signingKeys;
  @LocalServerPort private int serverPort;

  private String sfx;
  private Instant now;

  @BeforeEach
  void instanceKeyAndServersWithNothingTrending() {
    signingKeys.forInstance();
    sfx = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    jdbc.update(
        "INSERT IGNORE INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'LIMIT', NOW(6))",
        BLOCKED);
    when(http.getJson(any(), any(), any()))
        .thenReturn(new FederationHttp.Result.Failed(404, "Not Found"));
    when(http.get(any(), any())).thenReturn(new FederationHttp.Result.Failed(404, "gone"));
    for (String host : List.of(A, B, BLOCKED, QUIET)) {
      trends(host, "tags");
      trends(host, "links");
    }
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
  }

  private static URI trendsOf(String host, String kind) {
    return URI.create("https://" + host + "/api/v1/trends/" + kind + "?limit=20");
  }

  private void trends(String host, String kind, String... rows) {
    when(http.getJson(eq(trendsOf(host, kind)), any(), any()))
        .thenReturn(ok("[" + String.join(",", rows) + "]"));
  }

  private static URI tagTimeline(String host, String tag, int limit) {
    return URI.create(
        "https://"
            + host
            + "/api/v1/timelines/tag/"
            + URLEncoder.encode(tag, StandardCharsets.UTF_8)
            + "?limit="
            + limit);
  }

  private static URI linkTimeline(String host, String url) {
    return URI.create(
        "https://"
            + host
            + "/api/v1/timelines/link?url="
            + URLEncoder.encode(url, StandardCharsets.UTF_8)
            + "&limit=40");
  }

  private void timeline(URI timeline, String... statuses) {
    when(http.getJson(eq(timeline), any(), any()))
        .thenReturn(ok("[" + String.join(",", statuses) + "]"));
  }

  private static String history(int today, int yesterday) {
    return """
        [{"day":"1790000000","accounts":"%d","uses":"%d"},
         {"day":"1789913600","accounts":"%d","uses":"%d"},
         {"day":"1789827200","accounts":"400","uses":"900"}]"""
        .formatted(today, today * 2, yesterday, yesterday * 2);
  }

  private String tagRow(String name, int today, int yesterday) {
    return "{\"name\":%s,\"url\":\"https://ta.topics.example/tags/x\",\"history\":%s}"
        .formatted(json.writeValueAsString(name), history(today, yesterday));
  }

  private String linkRow(String url, String title, String language, int today, int yesterday) {
    return """
        {"url":%s,"title":%s,"description":"","type":"link","provider_name":"Example Daily",
         "image":"https://img.topics.example/a.png","language":%s,"history":%s}"""
        .formatted(
            json.writeValueAsString(url),
            json.writeValueAsString(title),
            json.writeValueAsString(language),
            history(today, yesterday));
  }

  private String authorHost(String name) {
    return name + "-" + sfx + ".example";
  }

  private String uri(String host, String user, int id) {
    return "https://" + host + "/users/" + user + "/statuses/" + id;
  }

  private String status(String host, String user, int id, Instant at, String language) {
    return """
        {"id":"%1$d","uri":"https://%4$s/users/%2$s/statuses/%1$d",
         "url":"https://%4$s/@%2$s/%1$d","created_at":"%3$s","visibility":"public",
         "in_reply_to_id":null,"reblog":null,"sensitive":false,"spoiler_text":"",
         "language":%5$s,"content":"<p>%2$s %1$d</p>","replies_count":0,"reblogs_count":0,
         "favourites_count":0,"media_attachments":[],"card":null,"poll":null,"mentions":[],
         "account":{"acct":"%2$s@%4$s","display_name":"%2$s","discoverable":true,
          "avatar":"https://%4$s/a.png","url":"https://%4$s/@%2$s",
          "uri":"https://%4$s/users/%2$s"}}"""
        .formatted(id, user, at, host, json.writeValueAsString(language));
  }

  private static String reply(String status) {
    return status.replace("\"in_reply_to_id\":null", "\"in_reply_to_id\":\"1\"");
  }

  private long localNote(String tag, String link, Instant at) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, ?, ?)",
        owner.getId(),
        "kept " + sfx,
        STORED.format(at));
    long id =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, owner.getId());
    carries(id, tag, link);
    return id;
  }

  private long remoteNote(String uri, String tag, Instant at) {
    String host = URI.create(uri).getHost();
    String actor = uri.substring(0, uri.indexOf("/statuses/"));
    String user = actor.substring(actor.lastIndexOf('/') + 1);
    jdbc.update(
        "INSERT IGNORE INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        actor,
        actor + "#main-key",
        actor + "/inbox",
        user,
        host);
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " SELECT id, ?, ?, ?, 'PUBLIC' FROM federation_remote_actor WHERE actor_uri = ?",
        uri,
        "kept copy " + sfx,
        STORED.format(at),
        actor);
    long id = jdbc.queryForObject("SELECT id FROM note WHERE uri = ?", Long.class, uri);
    carries(id, tag, null);
    return id;
  }

  private void carries(long noteId, String tag, String link) {
    if (tag != null) {
      jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, ?)", noteId, tag);
    }
    if (link != null) {
      jdbc.update(
          "INSERT INTO note_link_preview (note_id, url, title, fetched_at)"
              + " VALUES (?, ?, 'A headline', NOW(6))",
          noteId,
          link);
    }
  }

  private static String query(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private List<String> mine(JsonNode list) {
    List<String> ids = new ArrayList<>();
    for (JsonNode topic : list.path("items")) {
      if (topic.path("id").asString().contains(sfx)) {
        ids.add(topic.path("id").asString());
      }
    }
    return ids;
  }

  private static JsonNode topic(JsonNode list, String id) {
    for (JsonNode topic : list.path("items")) {
      if (topic.path("id").asString().equals(id)) {
        return topic;
      }
    }
    throw new AssertionError("no topic " + id + " in " + list);
  }

  private static List<String> shown(JsonNode page) {
    List<String> shown = new ArrayList<>();
    for (JsonNode item : page.path("items")) {
      shown.add(item.path("id").isNull() ? item.path("uri").asString() : "n" + item.path("id"));
    }
    return shown;
  }

  @Test
  void anyoneReadsWhatGoesAroundOnTheChosenServersTheirOwnLanguageFirst() throws Exception {
    String picnic = "소풍" + sfx;
    String harbor = "harbor" + sfx;
    String koLink = "https://news-" + sfx + ".example/ko";
    String koFolded = "https://news-" + sfx + ".example/picnic";
    String jaLink = "https://news-" + sfx + ".example/ja";
    trends(A, "tags", tagRow(picnic, 5, 4), tagRow(harbor, 4, 3), tagRow("two" + sfx, 1, 1));
    trends(
        A,
        "links",
        linkRow(koLink, "다리가 다시 열린다 " + sfx, "ko", 8, 6),
        linkRow(jaLink, "やまびこの時刻表 " + sfx, null, 30, 20),
        linkRow(koFolded, picnic + " 가는 길", "ko-KR", 12, 8));
    trends(B, "tags", tagRow(harbor.toUpperCase(), 9, 2));
    trends(BLOCKED, "tags", tagRow("gone" + sfx, 50, 50));
    timeline(
        tagTimeline(B, harbor, 20),
        status(authorHost("en"), "ann", 1, now.minusSeconds(60), "en"),
        status(authorHost("en"), "bo", 2, now.minusSeconds(120), "en"),
        status(authorHost("en"), "cy", 3, now.minusSeconds(180), "de"));

    captureBackgroundWork("federation-topics-refresh", topics::refresh);

    JsonNode korean =
        body(
            callWithHeaders(
                "federation-topics",
                "GET",
                "/api/v1/federation/topics?limit=40",
                null,
                Map.of("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8"),
                200));
    assertThat(mine(korean))
        .containsExactly("tag:" + picnic, "link:" + koLink, "link:" + jaLink, "tag:" + harbor);
    JsonNode tag = topic(korean, "tag:" + picnic);
    assertThat(tag.path("kind").asString()).isEqualTo("tag");
    assertThat(tag.path("tag").asString()).isEqualTo(picnic);
    assertThat(tag.path("url").isNull()).isTrue();
    assertThat(tag.path("language").asString()).isEqualTo("ko");
    assertThat(tag.path("own").asBoolean()).isTrue();
    assertThat(tag.path("people").asInt()).isEqualTo(20);
    assertThat(tag.path("servers").toString()).isEqualTo("[\"" + A + "\"]");
    assertThat(tag.path("related").size()).isEqualTo(1);
    assertThat(tag.path("related").get(0).path("url").asString()).isEqualTo(koFolded);
    assertThat(tag.path("related").get(0).path("source").asString()).isEqualTo("Example Daily");
    JsonNode link = topic(korean, "link:" + jaLink);
    assertThat(link.path("kind").asString()).isEqualTo("link");
    assertThat(link.path("tag").isNull()).isTrue();
    assertThat(link.path("title").asString()).isEqualTo("やまびこの時刻表 " + sfx);
    assertThat(link.path("source").asString()).isEqualTo("Example Daily");
    assertThat(link.path("image").asString()).isEqualTo("https://img.topics.example/a.png");
    assertThat(link.path("language").asString()).isEqualTo("ja");
    assertThat(link.path("own").asBoolean()).isFalse();
    assertThat(link.path("people").asInt()).isEqualTo(50);
    JsonNode sampled = topic(korean, "tag:" + harbor);
    assertThat(sampled.path("language").asString()).isEqualTo("en");
    assertThat(sampled.path("people").asInt()).isEqualTo(11);
    assertThat(sampled.path("servers").toString()).isEqualTo("[\"" + B + "\",\"" + A + "\"]");
    verify(http, never()).getJson(eq(trendsOf(BLOCKED, "tags")), any(), any());
    verify(http, never()).getJson(eq(trendsOf(BLOCKED, "links")), any(), any());

    JsonNode japanese =
        body(
            callWithHeaders(
                "federation-topics-member",
                "GET",
                "/api/v1/federation/topics?limit=40",
                null,
                Map.of("Authorization", "Bearer " + token, "Accept-Language", "ja"),
                200));
    assertThat(mine(japanese))
        .containsExactly("link:" + jaLink, "tag:" + picnic, "link:" + koLink, "tag:" + harbor);
    assertThat(topic(japanese, "link:" + jaLink).path("own").asBoolean()).isTrue();
    assertThat(topic(japanese, "tag:" + picnic).path("own").asBoolean()).isFalse();

    JsonNode seeded =
        body(
            call(
                "federation-topic-posts-seeded",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor,
                null,
                null,
                200));
    assertThat(shown(seeded))
        .containsExactly(
            uri(authorHost("en"), "ann", 1),
            uri(authorHost("en"), "bo", 2),
            uri(authorHost("en"), "cy", 3));
    assertThat(seeded.path("topic").path("id").asString()).isEqualTo("tag:" + harbor);
    verify(http, never()).getJson(eq(tagTimeline(B, harbor, 40)), any(), any());
    verify(http, never()).getJson(eq(tagTimeline(A, harbor, 40)), any(), any());

    JsonNode one =
        body(
            call(
                "federation-topics-limit",
                "GET",
                "/api/v1/federation/topics?limit=1",
                null,
                null,
                200));
    assertThat(one.path("items").size()).isEqualTo(1);
    assertThat(one.path("items").get(0).path("own").asBoolean()).isTrue();
  }

  @Test
  void aTopicsPostsJoinKeptNotesWithItsServersAndAnUnknownTagAsksNobody() throws Exception {
    String harbor = "harbor" + sfx;
    String unknown = "unlisted" + sfx;
    String keptUri = uri(authorHost("x"), "kim", 7);
    long older = localNote(harbor, null, now.minusSeconds(600));
    long keptCopy = remoteNote(keptUri, harbor, now.minusSeconds(300));
    long elsewhere = localNote(unknown, null, now.minusSeconds(60));
    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'LIMIT', NOW(6))",
        authorHost("bad"));
    trends(A, "tags", tagRow("소풍" + sfx, 9, 9), tagRow(harbor, 4, 3));
    trends(B, "tags", tagRow(harbor, 2, 2));
    trends(BLOCKED, "tags", tagRow("gone" + sfx, 50, 50));
    timeline(
        tagTimeline(B, harbor, 20),
        reply(status(authorHost("x"), "re", 8, now.minusSeconds(10), "en")),
        reply(status(authorHost("x"), "re", 9, now.minusSeconds(20), "en-GB")));
    captureBackgroundWork("federation-topics-refresh-unseeded", topics::refresh);
    timeline(
        tagTimeline(A, harbor, 40),
        status(authorHost("x"), "ann", 1, now.minusSeconds(120), "en"),
        status(authorHost("x"), "kim", 7, now.minusSeconds(300), "en"),
        status(authorHost("bad"), "mal", 2, now.minusSeconds(30), "en"),
        status(authorHost("y"), "bo", 3, now.minusSeconds(900), "en"));
    when(http.getJson(eq(tagTimeline(B, harbor, 40)), any(), any()))
        .thenReturn(new FederationHttp.Result.Unreachable("timed out"));
    clearInvocations(http);

    JsonNode unlisted =
        body(
            call(
                "federation-topic-posts-unknown",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + unknown,
                null,
                null,
                200));
    assertThat(unlisted.path("topic").isNull()).isTrue();
    assertThat(shown(unlisted)).containsExactly("n" + elsewhere);
    assertThat(unlisted.path("next").isNull()).isTrue();
    JsonNode goneWithItsServer =
        body(
            call(
                "federation-topic-posts-blocked-server",
                "GET",
                "/api/v1/federation/topics/posts?tag=gone" + sfx,
                null,
                token,
                200));
    assertThat(goneWithItsServer.path("topic").isNull()).isTrue();
    assertThat(goneWithItsServer.path("items")).isEmpty();
    verifyNoInteractions(http);

    JsonNode cold =
        body(
            call(
                "federation-topic-posts-cold",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor,
                null,
                null,
                200));
    assertThat(shown(cold))
        .containsExactly(
            uri(authorHost("x"), "ann", 1),
            "n" + keptCopy,
            "n" + older,
            uri(authorHost("y"), "bo", 3));
    assertThat(cold.path("next").isNull()).isTrue();
    assertThat(cold.path("topic").path("id").asString()).isEqualTo("tag:" + harbor);
    assertThat(cold.path("topic").path("language").asString()).isEqualTo("en");
    assertThat(cold.path("topic").path("own").asBoolean()).isFalse();
    assertThat(cold.path("items").get(1).path("uri").asString()).isEqualTo(keptUri);
    JsonNode remote = cold.path("items").get(0);
    assertThat(remote.path("body").asString()).isEqualTo("ann 1");
    assertThat(remote.path("servers").toString()).isEqualTo("[\"" + A + "\"]");
    assertThat(remote.path("author").path("username").asString())
        .isEqualTo("ann@" + authorHost("x"));

    JsonNode warm =
        body(
            call(
                "federation-topic-posts-warm",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + query("#" + harbor) + "&limit=2",
                null,
                null,
                200));
    assertThat(shown(warm)).containsExactly(uri(authorHost("x"), "ann", 1), "n" + keptCopy);
    assertThat(warm.path("next").asString()).isEqualTo("t2");
    verify(http, times(1)).getJson(eq(tagTimeline(A, harbor, 40)), any(), any());
    verify(http, times(1)).getJson(eq(tagTimeline(B, harbor, 40)), any(), any());

    jdbc.update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, ?, NOW(6))",
        owner.getId(),
        authorHost("y"));
    JsonNode member =
        body(
            callWithHeaders(
                "federation-topic-posts-member",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor + "&cursor=t1",
                null,
                Map.of("Authorization", "Bearer " + token, "Accept-Language", "en-US"),
                200));
    assertThat(shown(member)).containsExactly("n" + keptCopy, "n" + older);
    assertThat(member.path("topic").path("own").asBoolean()).isTrue();
    JsonNode first =
        body(
            call(
                "federation-topic-posts-member-first",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor + "&limit=1",
                null,
                token,
                200));
    assertThat(shown(first)).containsExactly(uri(authorHost("x"), "ann", 1));
    verify(http, times(1)).get(eq(URI.create("https://" + authorHost("x") + "/users/ann")), any());

    call(
        "federation-topic-posts-neither",
        "GET",
        "/api/v1/federation/topics/posts",
        null,
        null,
        400);
    assertThat(
            body(call(
                    "federation-topic-posts-both",
                    "GET",
                    "/api/v1/federation/topics/posts?tag=" + harbor + "&url=https://x.example/a",
                    null,
                    token,
                    400))
                .path("code")
                .asString())
        .isEqualTo("TOPIC_ASK_INVALID");
  }

  @Test
  void aTopicWithManyKeptNotesPagesItsWindowAndThenOlderKeptNotesToTheEnd() throws Exception {
    String harbor = "harbor" + sfx;
    String article = "https://news-" + sfx + ".example/a?id=1&ref=" + sfx;
    String keptBelowUri = uri(authorHost("x"), "kim", 7);
    long keptBelow = remoteNote(keptBelowUri, harbor, now.minusSeconds(9_000));
    carries(keptBelow, null, article);
    List<Long> kept = new ArrayList<>();
    for (int n = 0; n < 45; n++) {
      kept.add(localNote(harbor, article, now.minusSeconds(4_000 - n * 10L)));
    }
    trends(A, "tags", tagRow(harbor, 4, 3));
    trends(A, "links", linkRow(article, "A new ferry line " + sfx, "en", 5, 5));
    topics.refresh();
    String[] elsewhere = {
      status(authorHost("x"), "ann", 1, now.minusSeconds(5), "en"),
      status(authorHost("x"), "kim", 7, now.minusSeconds(9_000), "en"),
      status(authorHost("y"), "bo", 3, now.minusSeconds(20_000), "en")
    };
    timeline(tagTimeline(A, harbor, 40), elsewhere);
    timeline(linkTimeline(A, article), elsewhere);
    List<Long> newestFirst = kept.reversed();

    JsonNode first =
        body(
            call(
                "federation-topic-posts-window",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor + "&limit=40",
                null,
                null,
                200));
    assertThat(shown(first)).hasSize(40);
    assertThat(shown(first).getFirst()).isEqualTo(uri(authorHost("x"), "ann", 1));
    assertThat(shown(first).get(1)).isEqualTo("n" + newestFirst.getFirst());
    assertThat(shown(first)).doesNotContain(keptBelowUri);
    assertThat(first.path("next").asString()).isEqualTo("t40");

    JsonNode second =
        body(
            call(
                "federation-topic-posts-window-next",
                "GET",
                "/api/v1/federation/topics/posts?tag=" + harbor + "&limit=40&cursor=t40",
                null,
                token,
                200));
    assertThat(shown(second))
        .containsExactly("n" + newestFirst.get(39), uri(authorHost("y"), "bo", 3));
    assertThat(second.path("next").asString()).isEqualTo("r" + newestFirst.get(39));

    JsonNode third =
        body(
            call(
                "federation-topic-posts-older",
                "GET",
                "/api/v1/federation/topics/posts?tag="
                    + harbor
                    + "&limit=4&cursor="
                    + second.path("next").asString(),
                null,
                null,
                200));
    assertThat(shown(third))
        .containsExactly(
            "n" + newestFirst.get(40),
            "n" + newestFirst.get(41),
            "n" + newestFirst.get(42),
            "n" + newestFirst.get(43));
    assertThat(third.path("next").asString()).isEqualTo("r" + newestFirst.get(43));
    assertThat(third.path("topic").path("id").asString()).isEqualTo("tag:" + harbor);

    JsonNode last =
        body(
            call(
                "federation-topic-posts-older-last",
                "GET",
                "/api/v1/federation/topics/posts?tag="
                    + harbor
                    + "&limit=4&cursor="
                    + third.path("next").asString(),
                null,
                token,
                200));
    assertThat(shown(last)).containsExactly("n" + newestFirst.get(44), "n" + keptBelow);
    assertThat(last.path("items").get(1).path("uri").asString()).isEqualTo(keptBelowUri);
    assertThat(last.path("next").isNull()).isTrue();

    JsonNode byLink =
        body(
            call(
                "federation-topic-posts-link",
                "GET",
                "/api/v1/federation/topics/posts?url=" + query(article) + "&limit=40",
                null,
                null,
                200));
    assertThat(byLink.path("topic").path("kind").asString()).isEqualTo("link");
    assertThat(byLink.path("topic").path("title").asString()).isEqualTo("A new ferry line " + sfx);
    assertThat(shown(byLink)).hasSize(40);
    assertThat(shown(byLink).getFirst()).isEqualTo(uri(authorHost("x"), "ann", 1));
    assertThat(shown(byLink)).doesNotContain(keptBelowUri);
    assertThat(byLink.path("next").asString()).isEqualTo("t40");
    JsonNode linkOlder =
        body(
            call(
                "federation-topic-posts-link-older",
                "GET",
                "/api/v1/federation/topics/posts?url="
                    + query(article)
                    + "&limit=40&cursor=r"
                    + newestFirst.get(43),
                null,
                null,
                200));
    assertThat(shown(linkOlder)).containsExactly("n" + newestFirst.get(44), "n" + keptBelow);
    verify(http, times(1)).getJson(eq(linkTimeline(A, article)), any(), any());
  }

  @Test
  void readersArrivingTogetherShareOneReadOfATopicsServers() throws Exception {
    String harbor = "harbor" + sfx;
    trends(A, "tags", tagRow(harbor, 4, 3));
    trends(B, "tags", tagRow(harbor, 2, 2));
    topics.refresh();
    when(http.getJson(eq(tagTimeline(A, harbor, 40)), any(), any()))
        .thenAnswer(
            asked -> {
              Thread.sleep(400);
              return ok(
                  "["
                      + status(authorHost("x"), "ann", 1, now.minusSeconds(60), "en")
                      + ","
                      + status(authorHost("y"), "bo", 2, now.minusSeconds(90), "en")
                      + "]");
            });
    timeline(tagTimeline(B, harbor, 40));
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + serverPort
                        + "/api/v1/federation/topics/posts?tag="
                        + harbor))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    List<CompletableFuture<HttpResponse<String>>> together = new ArrayList<>();

    captureBackgroundWork(
        "federation-topic-posts-together",
        () -> {
          for (int reader = 0; reader < 6; reader++) {
            together.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
          }
          together.forEach(CompletableFuture::join);
        });

    for (CompletableFuture<HttpResponse<String>> answer : together) {
      assertThat(answer.join().statusCode()).isEqualTo(200);
      assertThat(shown(json.readTree(answer.join().body())))
          .containsExactly(uri(authorHost("x"), "ann", 1), uri(authorHost("y"), "bo", 2));
    }
    verify(http, times(1)).getJson(eq(tagTimeline(A, harbor, 40)), any(), any());
    verify(http, times(1)).getJson(eq(tagTimeline(B, harbor, 40)), any(), any());
  }
}
