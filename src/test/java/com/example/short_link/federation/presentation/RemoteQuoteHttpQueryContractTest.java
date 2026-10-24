package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@TestPropertySource(
    properties = {
      "short-link.federation.latest.pull=true",
      "short-link.federation.latest.hosts=mastodon.example",
      "short-link.federation.latest.backfill-pages=1"
    })
class RemoteQuoteHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final String TIMELINE =
      "https://mastodon.example/api/v1/timelines/public?local=true&limit=40";
  private static final String QUOTING =
      "https://mastodon.example/users/ana/statuses/117491551754057458";
  private static final String QUOTED =
      "https://news.example/users/desk/statuses/117491245825695182";

  @MockitoBean private FederationHttp http;
  @Autowired private LatestPosts latest;
  @Autowired private SigningKeys signingKeys;

  @BeforeEach
  void instanceKey() {
    signingKeys.forInstance();
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
  }

  private String person(String host, String user) {
    String actor = "https://" + host + "/users/" + user;
    return """
        {"@context":["https://www.w3.org/ns/activitystreams","https://w3id.org/security/v1"],
         "id":"%1$s","type":"Person","preferredUsername":"%2$s","inbox":"%1$s/inbox",
         "endpoints":{"sharedInbox":"https://%3$s/inbox"},"url":"https://%3$s/@%2$s",
         "discoverable":true,
         "publicKey":{"id":"%1$s#main-key","owner":"%1$s","publicKeyPem":%4$s}}"""
        .formatted(actor, user, host, json.writeValueAsString(KEYS.publicKey()));
  }

  private static Set<String> fields(JsonNode node) {
    return new HashSet<>(node.propertyNames());
  }

  @Test
  void aPulledQuoteTheServerAcceptedCarriesItsQuotedPostAsAMembersQuoteDoes() throws Exception {
    ObjectNode status = (ObjectNode) FederationFixtures.json("mastodon-quote-status.json");
    status.put("created_at", Instant.now().minus(10, ChronoUnit.MINUTES).toString());
    ObjectNode quoted = (ObjectNode) FederationFixtures.json("mastodon-quoted-note.json");
    quoted.put("published", Instant.now().minus(1, ChronoUnit.HOURS).toString());
    when(http.getJson(eq(URI.create(TIMELINE)), any(), any()))
        .thenReturn(ok("[" + json.writeValueAsString(status) + "]"));
    when(http.get(eq(URI.create("https://mastodon.example/users/ana")), any()))
        .thenReturn(ok(person("mastodon.example", "ana")));
    when(http.get(eq(URI.create("https://news.example/users/desk")), any()))
        .thenReturn(ok(person("news.example", "desk")));
    when(http.get(eq(URI.create(QUOTED)), any())).thenReturn(ok(json.writeValueAsString(quoted)));

    assertThat(latest.pull()).isEqualTo(1);
    long quoting = count("SELECT id FROM note WHERE uri = ?", QUOTING);
    long quotedId = count("SELECT id FROM note WHERE uri = ?", QUOTED);
    assertThat(count("SELECT quoted_note_id FROM note WHERE id = ?", quoting)).isEqualTo(quotedId);
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, quoting))
        .isEqualTo("The answer to that headline is no.");

    long member = createUser().getId();
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, 'quoted here', ?)",
        member,
        LocalDateTime.ofInstant(Instant.now().minusSeconds(120), ZoneOffset.UTC));
    long memberNote = count("SELECT MAX(id) FROM note WHERE user_id = ?", member);
    jdbc.update(
        "INSERT INTO note (user_id, body, quoted_note_id, created_at) VALUES (?, 'quoting', ?, ?)",
        member,
        memberNote,
        LocalDateTime.ofInstant(Instant.now().minusSeconds(60), ZoneOffset.UTC));
    long memberQuote = count("SELECT MAX(id) FROM note WHERE user_id = ?", member);
    JsonNode membersQuote =
        body(call(
                "remote-quote-member-note",
                "GET",
                "/api/v1/public/notes/" + memberQuote,
                null,
                token,
                200))
            .path("note")
            .path("quotedNote");
    assertThat(membersQuote.path("id").asLong()).isEqualTo(memberNote);

    JsonNode detail =
        body(call("remote-quote-note", "GET", "/api/v1/public/notes/" + quoting, null, token, 200))
            .path("note")
            .path("quotedNote");
    assertThat(detail.path("id").asLong()).isEqualTo(quotedId);
    assertThat(detail.path("author").path("username").asString()).isEqualTo("desk@news.example");
    assertThat(fields(detail)).isEqualTo(fields(membersQuote));

    JsonNode remoteForYou =
        body(
            call(
                "remote-quote-federation-for-you",
                "GET",
                "/api/v1/federation/for-you?limit=20",
                null,
                token,
                200));
    JsonNode item = remoteForYou.path("items").get(0);
    assertThat(item.path("uri").asString()).isEqualTo(QUOTING);
    assertThat(item.path("quotedNote").path("id").asLong()).isEqualTo(quotedId);
    assertThat(fields(item.path("quotedNote"))).isEqualTo(fields(membersQuote));

    JsonNode home =
        body(
            call(
                "remote-quote-home-for-you",
                "GET",
                "/api/v1/public/home?tab=for-you&limit=20",
                null,
                token,
                200));
    JsonNode remote = null;
    for (JsonNode candidate : home.path("items")) {
      if (QUOTING.equals(candidate.path("remote").path("uri").asString())) {
        remote = candidate.path("remote");
      }
    }
    assertThat(remote).isNotNull();
    assertThat(remote.path("quotedNote").path("id").asLong()).isEqualTo(quotedId);
    assertThat(fields(remote.path("quotedNote"))).isEqualTo(fields(membersQuote));
    assertThat(List.of(quoting, quotedId)).doesNotHaveDuplicates();
  }
}
