package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteAccountFinderTest {

  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final URI WEBFINGER =
      URI.create(
          "https://mastodon.example/.well-known/webfinger?resource=acct%3Aalice%40mastodon.example");

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private RemoteActorRepository actors;
  @Mock private RemoteActorResolver resolver;
  @Mock private RemoteDocuments documents;

  private RemoteAccountFinder finder() {
    return new RemoteAccountFinder(
        actors,
        resolver,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        documents);
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
            null,
            false,
            false),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static JsonNode jrd(String body) {
    return JSON.readTree(body);
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
    verifyNoInteractions(documents, actors, resolver);
  }

  @Test
  void aKnownAccountIsRefreshedThroughTheResolverWithoutWebFinger() {
    RemoteActorEntity cached = alice();
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.of(cached));
    when(resolver.refreshed(cached)).thenReturn(Optional.of(cached));

    assertThat(finder().find("@alice@mastodon.example")).contains(cached);
    verifyNoInteractions(documents);
  }

  @Test
  void webFingerGivesTheActivityPubActorAmongTheLinks() {
    RemoteActorEntity fetched = alice();
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenReturn(
            jrd(
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
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenReturn(
            jrd(
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
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenReturn(
            jrd("{\"links\":[{\"rel\":\"self\",\"type\":\"text/html\",\"href\":\"x\"}]}"),
            jrd("{\"subject\":\"acct:alice@mastodon.example\"}"))
        .thenThrow(
            new FederationException(
                FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, "alice@mastodon.example"),
            new FederationException(
                FederationErrorCode.REMOTE_SERVER_UNREACHABLE, "mastodon.example"));

    RemoteAccountFinder finder = finder();
    for (int i = 0; i < 4; i++) {
      assertThat(finder.find("alice@mastodon.example")).isEmpty();
    }
    verify(resolver, never()).byActorUri(any());
  }

  private static FederationErrorCode codeOf(Runnable resolve) {
    try {
      resolve.run();
    } catch (FederationException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a FederationException");
  }

  @Test
  void resolvingAnAccountOnThisServerIsNotFoundWithoutAskingAnyone() {
    assertThat(codeOf(() -> finder().resolve("@yuki@kurl.me")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    assertThat(codeOf(() -> finder().resolve("yuki")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
    verifyNoInteractions(actors, resolver, documents);
  }

  @Test
  void resolvingAKnownAccountRefreshesItAndKeepsTheCopyWhenItsServerIsSilent() {
    RemoteActorEntity cached = alice();
    RemoteActorEntity refreshed = alice();
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.of(cached));
    when(resolver.refreshed(cached)).thenReturn(Optional.of(refreshed), Optional.empty());

    assertThat(finder().resolve("@alice@mastodon.example")).isSameAs(refreshed);
    assertThat(finder().resolve("@alice@mastodon.example")).isSameAs(cached);
    verifyNoInteractions(documents);
  }

  @Test
  void resolvingAKnownAccountOnASuspendedServerIsNotFound() {
    RemoteActorEntity cached = alice();
    ReflectionTestUtils.setField(cached, "serverBlock", "SUSPEND");
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.of(cached));
    when(resolver.refreshed(cached)).thenReturn(Optional.of(cached));

    assertThat(codeOf(() -> finder().resolve("@alice@mastodon.example")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
  }

  @Test
  void resolvingANewAccountFollowsWebFingerToTheActorDocument() {
    JsonMapper json = JsonMapper.builder().build();
    RemoteActorEntity stored = alice();
    URI actor = URI.create(ALICE);
    var person = json.readTree("{\"type\":\"Person\"}");
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenReturn(
            json.readTree(
                """
                {"links":[{"rel":"self","type":"application/activity+json","href":"%s#me"}]}"""
                    .formatted(ALICE)));
    when(documents.fetch(actor, "alice@mastodon.example")).thenReturn(person);
    when(resolver.stored(person, actor)).thenReturn(Optional.of(stored));

    assertThat(finder().resolve("alice@mastodon.example")).isSameAs(stored);
    verify(actors, never()).saveAndFlush(any());
  }

  @Test
  void resolvingANewAccountWithoutAnActorOrAUsableDocumentIsNotFound() {
    JsonMapper json = JsonMapper.builder().build();
    URI actor = URI.create(ALICE);
    var notAnActor = json.readTree("{\"type\":\"Note\"}");
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenReturn(
            json.readTree("{\"subject\":\"acct:alice@mastodon.example\"}"),
            json.readTree(
                """
                {"links":[{"rel":"self","type":"application/activity+json","href":"%s"}]}"""
                    .formatted(ALICE)));
    when(documents.fetch(actor, "alice@mastodon.example")).thenReturn(notAnActor);
    when(resolver.stored(notAnActor, actor)).thenReturn(Optional.empty());

    RemoteAccountFinder finder = finder();
    assertThat(codeOf(() -> finder.resolve("alice@mastodon.example")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    assertThat(codeOf(() -> finder.resolve("alice@mastodon.example")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
  }

  @Test
  void resolvingANewAccountOnAServerThatDoesNotAnswerSaysSo() {
    when(actors.findByAcct("alice", "mastodon.example")).thenReturn(Optional.empty());
    when(documents.webFinger(WEBFINGER, "alice@mastodon.example"))
        .thenThrow(
            new FederationException(
                FederationErrorCode.REMOTE_SERVER_UNREACHABLE, "mastodon.example"));

    assertThat(codeOf(() -> finder().resolve("alice@mastodon.example")))
        .isEqualTo(FederationErrorCode.REMOTE_SERVER_UNREACHABLE);
    verifyNoInteractions(resolver);
  }

  private static final String SERVED = "https://social.example.com/users/gotosocial";
  private static final URI ASKED_WEBFINGER =
      URI.create(
          "https://example.com/.well-known/webfinger?resource=acct%3Agotosocial%40example.com");
  private static final URI SERVED_WEBFINGER =
      URI.create(
          "https://social.example.com/.well-known/webfinger?resource=acct%3Agotosocial%40social.example.com");

  private static RemoteActorEntity servedElsewhere() {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            SERVED,
            SERVED + "#main-key",
            "pem",
            SERVED + "/inbox",
            null,
            "gotosocial",
            "social.example.com",
            null,
            null,
            null,
            false,
            false),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static JsonNode answer(String subject, String self) {
    return jrd(
        """
        {"subject":"%s","links":[{"rel":"self","type":"application/activity+json","href":"%s"}]}"""
            .formatted(subject, self));
  }

  private RemoteActorEntity resolvedServedElsewhere(JsonNode servedAnswer) {
    RemoteActorEntity stored = servedElsewhere();
    var person = JSON.readTree("{\"type\":\"Person\"}");
    when(actors.findByAcct("gotosocial", "example.com")).thenReturn(Optional.empty());
    when(documents.webFinger(ASKED_WEBFINGER, "gotosocial@example.com"))
        .thenReturn(answer("acct:gotosocial@example.com", SERVED));
    when(documents.fetch(URI.create(SERVED), "gotosocial@example.com")).thenReturn(person);
    when(resolver.stored(person, URI.create(SERVED))).thenReturn(Optional.of(stored));
    if (servedAnswer == null) {
      when(documents.webFinger(SERVED_WEBFINGER, "gotosocial@social.example.com"))
          .thenThrow(
              new FederationException(
                  FederationErrorCode.REMOTE_SERVER_UNREACHABLE, "social.example.com"));
    } else {
      when(documents.webFinger(SERVED_WEBFINGER, "gotosocial@social.example.com"))
          .thenReturn(servedAnswer);
    }
    lenient().when(actors.saveAndFlush(stored)).thenReturn(stored);
    return finder().resolve("@gotosocial@example.com");
  }

  @Test
  void anAccountServedFromAnotherHostIsKnownByTheAskedHandleOnceThatHostNamesIt() {
    RemoteActorEntity resolved =
        resolvedServedElsewhere(answer("acct:GoToSocial@Example.com", SERVED + "#me"));

    assertThat(resolved.getAccountDomain()).isEqualTo("example.com");
    verify(actors).saveAndFlush(resolved);
  }

  @Test
  void theAskedHandleIsNotKeptUnlessTheServingHostAnswersWithItAndTheSameActor() {
    JsonNode[] unconfirmed = {
      answer("acct:gotosocial@social.example.com", SERVED),
      answer("acct:gotosocial@example.com", "https://social.example.com/users/someone"),
      jrd("{\"subject\":\"acct:gotosocial@example.com\"}"),
      null
    };
    for (JsonNode servedAnswer : unconfirmed) {
      reset(actors, resolver, documents);
      RemoteActorEntity resolved = resolvedServedElsewhere(servedAnswer);

      assertThat(resolved.getActorUri()).isEqualTo(SERVED);
      assertThat(resolved.getAccountDomain()).isNull();
      verify(actors, never()).saveAndFlush(any());
    }
  }

  @Test
  void aLookupKnowsTheAskedHandleTooAndAnAccountOnItsOwnHostAsksNothingMore() {
    RemoteActorEntity served = servedElsewhere();
    when(actors.findByAcct("gotosocial", "example.com")).thenReturn(Optional.empty());
    when(documents.webFinger(ASKED_WEBFINGER, "gotosocial@example.com"))
        .thenReturn(answer("acct:gotosocial@example.com", SERVED));
    when(resolver.byActorUri(SERVED)).thenReturn(Optional.of(served));
    when(documents.webFinger(SERVED_WEBFINGER, "gotosocial@social.example.com"))
        .thenReturn(answer("acct:gotosocial@example.com", SERVED));
    when(actors.saveAndFlush(served)).thenReturn(served);

    assertThat(finder().find("gotosocial@example.com")).contains(served);
    assertThat(served.getAccountDomain()).isEqualTo("example.com");

    RemoteActorEntity local = servedElsewhere();
    when(actors.findByAcct("gotosocial", "social.example.com")).thenReturn(Optional.empty());
    when(documents.webFinger(SERVED_WEBFINGER, "gotosocial@social.example.com"))
        .thenReturn(answer("acct:gotosocial@example.com", SERVED));
    when(resolver.byActorUri(SERVED)).thenReturn(Optional.of(local));

    assertThat(finder().find("gotosocial@social.example.com")).contains(local);
    assertThat(local.getAccountDomain()).isNull();
    verify(documents, times(2)).webFinger(SERVED_WEBFINGER, "gotosocial@social.example.com");
  }
}
