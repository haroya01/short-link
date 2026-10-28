package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.common.seen.SeenParameters;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "short-link.federation.latest.hosts=seen.example")
class RemoteSeenHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final String HOST = "seen.example";
  private static final String DEVICE = "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7";
  private static final String TIMELINE =
      "https://" + HOST + "/api/v1/timelines/public?local=true&limit=40";

  @MockitoBean private FederationHttp http;
  @Autowired private RemoteSeen seen;
  @Autowired private SigningKeys signingKeys;

  @BeforeEach
  void instanceKey() {
    signingKeys.forInstance();
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static String uri(String id) {
    return "https://" + HOST + "/users/yui/statuses/" + id;
  }

  private static String status(String id, String at) {
    return """
        {"id":"%1$s","uri":"https://%3$s/users/yui/statuses/%1$s",
         "url":"https://%3$s/@yui/%1$s","created_at":"%2$s","visibility":"public",
         "in_reply_to_id":null,"reblog":null,"sensitive":false,"spoiler_text":"",
         "language":"ja","content":"<p>yui %1$s</p>","replies_count":0,"reblogs_count":0,
         "favourites_count":0,"media_attachments":[],"card":null,"poll":null,"mentions":[],
         "account":{"acct":"yui","display_name":"yui","discoverable":true,
          "avatar":"https://%3$s/a.png","url":"https://%3$s/@yui",
          "uri":"https://%3$s/users/yui"}}"""
        .formatted(id, at, HOST);
  }

  private static List<String> uris(JsonNode page) {
    List<String> uris = new ArrayList<>();
    page.path("items").forEach(item -> uris.add(item.path("uri").asString()));
    return uris;
  }

  private static Map<String, String> device() {
    return Map.of(SeenParameters.DEVICE_HEADER, DEVICE);
  }

  @Test
  void aReaderGetsWhatTheyHaveNotSeenFirstAndWhatTheySawBelowTheLine() throws Exception {
    when(http.getJson(eq(URI.create(TIMELINE)), any(), any()))
        .thenReturn(
            ok(
                "["
                    + status("103", "2026-10-22T01:03:00Z")
                    + ","
                    + status("102", "2026-10-22T01:02:00Z")
                    + ","
                    + status("101", "2026-10-22T01:01:00Z")
                    + "]"));

    JsonNode plain =
        body(
            call(
                "federation-seen-latest-plain",
                "GET",
                "/api/v1/federation/latest?limit=2",
                null,
                null,
                200));
    assertThat(uris(plain)).containsExactly(uri("103"), uri("102"));
    assertThat(plain.path("next").asString()).isEqualTo("t2");
    assertThat(plain.has("seenFrom")).isFalse();

    callWithHeaders(
        "federation-seen",
        "POST",
        "/api/v1/federation/seen",
        Map.of("uris", List.of(uri("103"), uri("102"), "ftp://" + HOST + "/x")),
        device(),
        202);
    call(
        "federation-seen-member",
        "POST",
        "/api/v1/federation/seen",
        Map.of("uris", List.of(uri("103"))),
        token,
        202);
    call(
        "federation-seen-nobody",
        "POST",
        "/api/v1/federation/seen",
        Map.of("uris", List.of(uri("103"))),
        null,
        401);
    assertThat(count("SELECT COUNT(*) FROM remote_seen")).isZero();

    captureBackgroundWork("federation-seen-flush", () -> assertThat(seen.flush()).isEqualTo(3));
    assertThat(count("SELECT COUNT(*) FROM remote_seen WHERE viewer_kind = 2")).isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM remote_seen WHERE viewer_kind = 1"
                    + " AND viewer_key = UNHEX(LPAD(HEX(?), 32, '0'))",
                owner.getId()))
        .isEqualTo(1);

    JsonNode first =
        body(
            callWithHeaders(
                "federation-seen-latest-first",
                "GET",
                "/api/v1/federation/latest?limit=2",
                null,
                device(),
                200));
    assertThat(uris(first)).containsExactly(uri("101"), uri("103"));
    assertThat(first.path("seenFrom").asInt()).isEqualTo(1);
    assertThat(first.path("next").asString()).startsWith("s").endsWith(".t1");

    JsonNode next =
        body(
            callWithHeaders(
                "federation-seen-latest-next",
                "GET",
                "/api/v1/federation/latest?limit=2&cursor=" + first.path("next").asString(),
                null,
                device(),
                200));
    assertThat(uris(next)).containsExactly(uri("102"));
    assertThat(next.has("seenFrom")).isFalse();
    assertThat(next.path("next").isNull()).isTrue();

    jdbc.update("UPDATE remote_seen SET seen_at = seen_at - INTERVAL 15 DAY WHERE viewer_kind = 2");
    captureBackgroundWork("federation-seen-cleanup", () -> assertThat(seen.sweep()).isEqualTo(2));
    assertThat(count("SELECT COUNT(*) FROM remote_seen")).isEqualTo(1);
  }
}
