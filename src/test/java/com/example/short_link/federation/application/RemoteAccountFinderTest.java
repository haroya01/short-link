package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteAccountFinderTest {

  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final URI WEBFINGER =
      URI.create(
          "https://mastodon.example/.well-known/webfinger?resource=acct%3Aalice%40mastodon.example");

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private RemoteActorRepository actors;
  @Mock private RemoteActorResolver resolver;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);

  @BeforeEach
  void instanceSigner() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
  }

  private RemoteAccountFinder finder() {
    return new RemoteAccountFinder(
        http,
        signingKeys,
        actors,
        resolver,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JsonMapper.builder().build());
  }

  private static RemoteActorEntity alice() {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            ALICE,
            ALICE + "#main-key",
            "pem",
            ALICE + "/inbox",
            null,
            "alice",
            "mastodon.example",
            null,
            null,
            null),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aHandleIsUserAtServerWithAnOptionalLeadingAt() {
    assertThat(RemoteAccountFinder.parse(" @Alice@Mastodon.Example "))
        .isEqualTo(new RemoteAccountFinder.Handle("Alice", "mastodon.example"));
    assertThat(RemoteAccountFinder.parse("bob.k@social.example:8443"))
        .isEqualTo(new RemoteAccountFinder.Handle("bob.k", "social.example:8443"));
    for (String bad : new String[] {"alice", "alice@", "@alice@localhost", "a b@x.example", null}) {
      assertThatThrownBy(() -> RemoteAccountFinder.parse(bad))
          .isInstanceOfSatisfying(
              FederationException.class,
              e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_INVALID));
    }
  }

  @Test
  void anAccountOnThisServerIsNotLookedUpRemotely() {
    assertThat(finder().find("yuki@kurl.me")).isEmpty();
    verifyNoInteractions(http, actors, resolver);
  }

  @Test
  void aKnownAccountIsRefreshedThroughTheResolverWithoutWebFinger() {
    RemoteActorEntity cached = alice();
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.of(cached));
    when(resolver.refreshed(cached)).thenReturn(Optional.of(cached));

    assertThat(finder().find("@alice@mastodon.example")).contains(cached);
    verifyNoInteractions(http);
  }

  @Test
  void webFingerGivesTheActivityPubActorAmongTheLinks() {
    RemoteActorEntity fetched = alice();
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(http.get(WEBFINGER, instance))
        .thenReturn(
            ok(
                """
                {"subject":"acct:alice@mastodon.example","links":[
                  {"rel":"http://webfinger.net/rel/profile-page","type":"text/html",
                   "href":"https://mastodon.example/@alice"},
                  {"rel":"self","type":"application/activity+json","href":"%s"}]}"""
                    .formatted(ALICE)));
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(fetched));

    assertThat(finder().find("alice@mastodon.example")).contains(fetched);
  }

  @Test
  void theLdJsonProfileTypeCountsAsActivityPub() {
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(http.get(WEBFINGER, instance))
        .thenReturn(
            ok(
                """
                {"links":[{"rel":"self",
                  "type":"application/ld+json; profile=\\"https://www.w3.org/ns/activitystreams\\"",
                  "href":"%s"}]}"""
                    .formatted(ALICE)));
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.empty());

    assertThat(finder().find("alice@mastodon.example")).isEmpty();
    verify(resolver).byActorUri(ALICE);
  }

  @Test
  void noActorLinkABrokenDocumentOrAFailedFetchFindsNoOne() {
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(http.get(WEBFINGER, instance))
        .thenReturn(
            ok("{\"links\":[{\"rel\":\"self\",\"type\":\"text/html\",\"href\":\"x\"}]}"),
            ok("{\"subject\":\"acct:alice@mastodon.example\"}"),
            ok("null"),
            ok("{not json"),
            new FederationHttp.Result.Failed(404, "not found"));

    RemoteAccountFinder finder = finder();
    for (int i = 0; i < 5; i++) {
      assertThat(finder.find("alice@mastodon.example")).isEmpty();
    }
    verify(resolver, never()).byActorUri(any());
  }
}
