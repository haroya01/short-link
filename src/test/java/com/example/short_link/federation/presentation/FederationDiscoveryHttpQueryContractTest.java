package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class FederationDiscoveryHttpQueryContractTest extends AccountHttpJourneySupport {

  private static final Map<String, String> ACTIVITY_JSON =
      Map.of("Accept", "application/activity+json, application/ld+json");

  @Autowired private FederationUrls urls;

  @Test
  void aRemoteServerFindsFollowsAndReadsAnActor() throws Exception {
    String acct = "acct:" + owner.getUsername() + "@" + urls.domain();

    var first =
        body(
            callWithHeaders(
                "federation-webfinger-first",
                "GET",
                "/.well-known/webfinger?resource=" + acct,
                null,
                Map.of(),
                200));
    String actorId = first.path("links").get(0).path("href").asText();
    String publicId = actorId.substring(actorId.lastIndexOf('/') + 1);
    assertThat(first.path("subject").asText()).isEqualTo(acct);
    assertThat(count("SELECT COUNT(*) FROM federation_actor WHERE user_id = ?", owner.getId()))
        .isEqualTo(1);

    var again =
        body(
            callWithHeaders(
                "federation-webfinger-known",
                "GET",
                "/.well-known/webfinger?resource=" + acct,
                null,
                Map.of(),
                200));
    assertThat(again.path("links").get(0).path("href").asText()).isEqualTo(actorId);

    var actor =
        body(
            callWithHeaders(
                "federation-actor", "GET", "/ap/actors/" + publicId, null, ACTIVITY_JSON, 200));
    assertThat(actor.path("id").asText()).isEqualTo(actorId);
    assertThat(actor.path("preferredUsername").asText()).isEqualTo(owner.getUsername());
    assertThat(actor.path("publicKey").path("publicKeyPem").asText())
        .startsWith("-----BEGIN PUBLIC KEY-----");
    assertThat(actor.path("indexable").asBoolean()).isTrue();

    var html =
        callWithHeaders(
            "federation-actor-html",
            "GET",
            "/ap/actors/" + publicId,
            null,
            Map.of("Accept", "text/html"),
            302);
    assertThat(html.headers().firstValue("Location").orElseThrow())
        .endsWith("/@" + owner.getUsername());

    callWithHeaders(
        "federation-actor-followers",
        "GET",
        "/ap/actors/" + publicId + "/followers",
        null,
        ACTIVITY_JSON,
        200);
    callWithHeaders(
        "federation-actor-following",
        "GET",
        "/ap/actors/" + publicId + "/following",
        null,
        ACTIVITY_JSON,
        200);
    callWithHeaders(
        "federation-actor-outbox",
        "GET",
        "/ap/actors/" + publicId + "/outbox",
        null,
        ACTIVITY_JSON,
        200);
    callWithHeaders(
        "federation-actor-featured",
        "GET",
        "/ap/actors/" + publicId + "/featured",
        null,
        ACTIVITY_JSON,
        200);
    callWithHeaders("federation-host-meta", "GET", "/.well-known/host-meta", null, Map.of(), 200);
    callWithHeaders(
        "federation-nodeinfo-discovery", "GET", "/.well-known/nodeinfo", null, Map.of(), 200);
    var nodeInfo =
        body(
            callWithHeaders("federation-nodeinfo", "GET", "/ap/nodeinfo/2.1", null, Map.of(), 200));
    assertThat(nodeInfo.path("usage").path("users").path("total").asLong()).isPositive();
    assertThat(nodeInfo.path("usage").path("localPosts").isIntegralNumber()).isTrue();
    var cached =
        body(
            callWithHeaders(
                "federation-nodeinfo-cached", "GET", "/ap/nodeinfo/2.1", null, Map.of(), 200));
    assertThat(cached.path("usage")).isEqualTo(nodeInfo.path("usage"));
  }

  @Test
  void theInstanceActorIsCreatedOnceThenServed() throws Exception {
    jdbc.update("DELETE FROM federation_instance_actor");
    var created =
        body(
            callWithHeaders(
                "federation-instance-first", "GET", "/ap/instance", null, ACTIVITY_JSON, 200));
    var served =
        body(
            callWithHeaders(
                "federation-instance-known", "GET", "/ap/instance", null, ACTIVITY_JSON, 200));

    var found =
        body(
            callWithHeaders(
                "federation-webfinger-instance",
                "GET",
                "/.well-known/webfinger?resource=acct:"
                    + created.path("preferredUsername").asText()
                    + "@"
                    + urls.domain(),
                null,
                Map.of(),
                200));

    assertThat(created.path("type").asText()).isEqualTo("Application");
    assertThat(served.path("publicKey").path("publicKeyPem").asText())
        .isEqualTo(created.path("publicKey").path("publicKeyPem").asText());
    assertThat(count("SELECT COUNT(*) FROM federation_instance_actor")).isEqualTo(1);
    assertThat(found.path("links").get(0).path("href").asText())
        .isEqualTo(created.path("id").asText());
  }

  @Test
  void aDeletedAccountStopsResolvingAtOnce() throws Exception {
    String acct = "acct:" + stranger.getUsername() + "@" + urls.domain();
    var found =
        body(
            callWithHeaders(
                "federation-webfinger-before-delete",
                "GET",
                "/.well-known/webfinger?resource=" + acct,
                null,
                Map.of(),
                200));
    String actorId = found.path("links").get(0).path("href").asText();
    String publicId = actorId.substring(actorId.lastIndexOf('/') + 1);

    call("federation-account-delete", "DELETE", "/api/v1/users/me", null, strangerToken, 204);

    callWithHeaders(
        "federation-webfinger-after-delete",
        "GET",
        "/.well-known/webfinger?resource=" + acct,
        null,
        Map.of(),
        404);
    callWithHeaders(
        "federation-actor-after-delete", "GET", "/ap/actors/" + publicId, null, ACTIVITY_JSON, 404);
  }

  private void remoteAccounts(String domain, int count) {
    for (int i = 0; i < count; i++) {
      String actor = "https://" + domain + "/users/u" + i;
      jdbc.update(
          "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
              + " username, domain, fetched_at, created_at, updated_at)"
              + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
          actor,
          actor + "#main-key",
          actor + "/inbox",
          "u" + i,
          domain);
    }
  }

  private static List<String> domains(JsonNode found) {
    List<String> domains = new ArrayList<>();
    for (JsonNode item : found.path("items")) {
      domains.add(item.path("domain").asText());
    }
    return domains;
  }

  private JsonNode domainsFor(String id, String query) throws Exception {
    return body(call(id, "GET", "/api/v1/federation/domains" + query, null, null, 200));
  }

  @Test
  void anyoneCompletesTheServerOfAHandleFromKnownServersAndAFewLargeOnes() throws Exception {
    String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    remoteAccounts(tag + "-a.example", 3);
    remoteAccounts(tag + "-b.example", 1);
    remoteAccounts(tag + "-gone.example", 5);
    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'SUSPEND', NOW(6))",
        tag + "-gone.example");

    var typed =
        domainsFor(
            "federation-domains-first",
            "?prefix=" + URLEncoder.encode("@gargron@" + tag, StandardCharsets.UTF_8));
    assertThat(domains(typed)).containsExactly(tag + "-a.example", tag + "-b.example");

    assertThat(domains(domainsFor("federation-domains", "?prefix=m")))
        .containsExactly("mastodon.social", "misskey.io", "mstdn.jp", "mastodon.online", "mas.to");
    assertThat(domains(domainsFor("federation-domains-top", "?limit=3")))
        .containsExactly(tag + "-a.example", tag + "-b.example", "mastodon.social");
    assertThat(domains(domainsFor("federation-domains-invalid", "?prefix=not%20a%20server")))
        .isEmpty();
  }
}
