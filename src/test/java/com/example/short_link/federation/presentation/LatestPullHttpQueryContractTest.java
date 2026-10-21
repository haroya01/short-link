package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(
    properties = {
      "short-link.federation.latest.pull=true",
      "short-link.federation.latest.hosts=pull.example",
      "short-link.federation.latest.backfill-pages=2"
    })
class LatestPullHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final String HOST = "pull.example";
  private static final String TIMELINE =
      "https://" + HOST + "/api/v1/timelines/public?local=true&limit=40";

  @MockitoBean private FederationHttp http;
  @Autowired private LatestPosts latest;
  @Autowired private SigningKeys signingKeys;

  @BeforeEach
  void instanceKey() {
    signingKeys.forInstance();
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static String status(String id, String user, String at, String extra) {
    return """
        {"id":"%1$s","uri":"https://%4$s/users/%2$s/statuses/%1$s",
         "url":"https://%4$s/@%2$s/%1$s","created_at":"%3$s","visibility":"public",
         "in_reply_to_id":null,"reblog":null,"sensitive":false,"spoiler_text":"",
         "language":"ja","content":"<p>%2$s %1$s</p>","replies_count":0,"reblogs_count":0,
         "favourites_count":0,"media_attachments":[],"card":null,"poll":null,"mentions":[],
         "account":{"acct":"%2$s","display_name":"%2$s","discoverable":true%5$s,
          "avatar":"https://%4$s/a.png","url":"https://%4$s/@%2$s",
          "uri":"https://%4$s/users/%2$s"}}"""
        .formatted(id, user, at, HOST, extra);
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

  private long noteOf(String id, String user) {
    return jdbc.queryForObject(
        "SELECT id FROM note WHERE uri = ?",
        Long.class,
        "https://" + HOST + "/users/" + user + "/statuses/" + id);
  }

  private static List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  @Test
  void pullsKeepTheChosenServersPostsAndAnyonePagesThemFromTheStore() throws Exception {
    when(http.getJson(eq(URI.create(TIMELINE)), any(), any()))
        .thenReturn(
            ok(
                "["
                    + status("101", "yui", "2026-10-21T01:00:00Z", "")
                    + ","
                    + status("100", "rin", "2026-10-21T00:59:00Z", ",\"noindex\":true")
                    + "]"));
    when(http.getJson(eq(URI.create(TIMELINE + "&since_id=101")), any(), any()))
        .thenReturn(ok("[" + status("102", "yui", "2026-10-21T01:05:00Z", "") + "]"));
    when(http.get(eq(URI.create("https://" + HOST + "/users/yui")), any()))
        .thenReturn(ok(person("yui")));

    captureBackgroundWork(
        "federation-latest-pull-first", () -> assertThat(latest.pull()).isEqualTo(1));
    captureBackgroundWork(
        "federation-latest-pull-since", () -> assertThat(latest.pull()).isEqualTo(1));

    long first = noteOf("101", "yui");
    long second = noteOf("102", "yui");
    assertThat(
            jdbc.queryForList(
                "SELECT pulled_from FROM federation_relayed_note WHERE note_id IN (?, ?)",
                String.class,
                first,
                second))
        .containsExactly(HOST, HOST);
    assertThat(
            count("SELECT COUNT(*) FROM note WHERE uri LIKE ?", "https://" + HOST + "/users/rin%"))
        .isZero();
    verify(http, times(1)).get(eq(URI.create("https://" + HOST + "/users/yui")), any());
    verify(http, never()).getJson(eq(URI.create(TIMELINE + "&max_id=101")), any(), any());

    JsonNode top =
        body(
            call(
                "federation-latest-stored",
                "GET",
                "/api/v1/federation/latest?limit=1",
                null,
                null,
                200));
    assertThat(ids(top)).containsExactly(second);
    assertThat(top.path("next").asString()).isEqualTo("r" + second);

    JsonNode next =
        body(
            call(
                "federation-latest-stored-next",
                "GET",
                "/api/v1/federation/latest?limit=1&cursor=r" + second,
                null,
                token,
                200));
    assertThat(ids(next)).containsExactly(first);
    assertThat(next.path("next").isNull()).isTrue();

    JsonNode since =
        body(
            call(
                "federation-latest-stored-since",
                "GET",
                "/api/v1/federation/latest?limit=20&since=" + first,
                null,
                token,
                200));
    assertThat(ids(since)).containsExactly(second);
    assertThat(since.path("gap").asBoolean()).isFalse();

    JsonNode kept =
        body(
            call(
                "federation-trending-kept-pulled",
                "GET",
                "/api/v1/federation/trending?limit=20&cursor=r",
                null,
                token,
                200));
    assertThat(ids(kept)).containsExactly(second, first);

    jdbc.update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " created_at) SELECT ?, remote_actor_id, FALSE, NOW(6) FROM note WHERE id = ?",
        owner.getId(),
        second);
    JsonNode muted =
        body(
            call(
                "federation-latest-stored-muted",
                "GET",
                "/api/v1/federation/latest?limit=20",
                null,
                token,
                200));
    assertThat(ids(muted)).isEmpty();
  }
}
