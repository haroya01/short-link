package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
    callWithHeaders("federation-host-meta", "GET", "/.well-known/host-meta", null, Map.of(), 200);
    callWithHeaders(
        "federation-nodeinfo-discovery", "GET", "/.well-known/nodeinfo", null, Map.of(), 200);
    callWithHeaders("federation-nodeinfo", "GET", "/ap/nodeinfo/2.1", null, Map.of(), 200);
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
}
