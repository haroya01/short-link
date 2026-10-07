package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteActorResolverTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  private static final String MASTODON_KEY = "https://mastodon.example/users/alice#main-key";
  private static final URI MASTODON_ACTOR = URI.create("https://mastodon.example/users/alice");

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private RemoteActorRepository actors;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);

  @BeforeEach
  void instanceSigner() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(actors.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private RemoteActorResolver resolver() {
    return new RemoteActorResolver(
        http, signingKeys, actors, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static RemoteActorEntity cached(Instant fetchedAt) {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            MASTODON_ACTOR.toString(),
            MASTODON_KEY,
            RemoteActorFixtures.PEM,
            "https://mastodon.example/users/alice/inbox",
            null,
            "alice",
            "mastodon.example",
            null,
            null,
            null),
        fetchedAt);
  }

  @Test
  void aFreshCachedKeyIsUsedWithoutFetching() {
    when(actors.findByKeyId(MASTODON_KEY)).thenReturn(Optional.of(cached(NOW.minusSeconds(60))));

    assertThat(resolver().byKeyId(MASTODON_KEY, false)).isPresent();
    verify(http, never()).get(any(), any());
  }

  @Test
  void aMastodonKeyResolvesInOneSignedFetch() {
    when(actors.findByKeyId(MASTODON_KEY)).thenReturn(Optional.empty());
    when(actors.findByActorUri(MASTODON_ACTOR.toString())).thenReturn(Optional.empty());
    when(http.get(MASTODON_ACTOR, instance)).thenReturn(ok(RemoteActorFixtures.MASTODON));

    RemoteActorEntity actor = resolver().byKeyId(MASTODON_KEY, false).orElseThrow();

    assertThat(actor.deliveryInbox()).isEqualTo("https://mastodon.example/inbox");
    assertThat(actor.getFetchedAt()).isEqualTo(NOW);
  }

  @Test
  void aGoToSocialKeyFollowsItsOwnerToTheActor() {
    String keyId = "https://gts.example/users/bob/main-key";
    when(actors.findByKeyId(keyId)).thenReturn(Optional.empty());
    when(actors.findByActorUri("https://gts.example/users/bob")).thenReturn(Optional.empty());
    when(http.get(URI.create(keyId), instance))
        .thenReturn(ok(RemoteActorFixtures.GOTOSOCIAL_KEY_STUB));
    when(http.get(URI.create("https://gts.example/users/bob"), instance))
        .thenReturn(ok(RemoteActorFixtures.GOTOSOCIAL_ACTOR));

    RemoteActorEntity actor = resolver().byKeyId(keyId, false).orElseThrow();

    assertThat(actor.getActorUri()).isEqualTo("https://gts.example/users/bob");
    assertThat(actor.deliveryInbox()).isEqualTo("https://gts.example/users/bob/inbox");
  }

  @Test
  void aKeyWhoseOwnerLivesElsewhereIsRefused() {
    String keyId = "https://gts.example/users/bob/main-key";
    when(actors.findByKeyId(keyId)).thenReturn(Optional.empty());
    when(http.get(URI.create(keyId), instance))
        .thenReturn(
            ok(
                RemoteActorFixtures.GOTOSOCIAL_KEY_STUB.replace(
                    "\"owner\": \"https://gts.example/users/bob\"",
                    "\"owner\": \"https://evil.example/users/bob\"")));

    assertThat(resolver().byKeyId(keyId, false)).isEmpty();
  }

  @Test
  void aStaleCacheIsRefreshedAndKeptWhenTheRemoteIsDown() {
    RemoteActorEntity stale = cached(NOW.minus(RemoteActorResolver.FRESH_FOR).minusSeconds(1));
    when(actors.findByKeyId(MASTODON_KEY)).thenReturn(Optional.of(stale));
    when(http.get(MASTODON_ACTOR, instance))
        .thenReturn(new FederationHttp.Result.Failed(503, "HTTP 503"));

    assertThat(resolver().byKeyId(MASTODON_KEY, false)).contains(stale);
    assertThat(resolver().byKeyId(MASTODON_KEY, true)).isEmpty();
  }

  @Test
  void aRefetchUpdatesTheCachedRow() {
    RemoteActorEntity existing = cached(NOW.minusSeconds(60));
    when(actors.findByKeyId(MASTODON_KEY)).thenReturn(Optional.of(existing));
    when(actors.findByActorUri(MASTODON_ACTOR.toString())).thenReturn(Optional.of(existing));
    when(http.get(MASTODON_ACTOR, instance)).thenReturn(ok(RemoteActorFixtures.MASTODON));

    RemoteActorEntity refreshed = resolver().byKeyId(MASTODON_KEY, true).orElseThrow();

    assertThat(refreshed).isSameAs(existing);
    assertThat(refreshed.getSharedInbox()).isEqualTo("https://mastodon.example/inbox");
    assertThat(refreshed.getFetchedAt()).isEqualTo(NOW);
  }

  @Test
  void aConcurrentInsertReadsTheWinner() {
    RemoteActorEntity winner = cached(NOW);
    when(actors.findByKeyId(MASTODON_KEY)).thenReturn(Optional.empty());
    when(actors.findByActorUri(MASTODON_ACTOR.toString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(http.get(MASTODON_ACTOR, instance)).thenReturn(ok(RemoteActorFixtures.MASTODON));
    when(actors.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThat(resolver().byKeyId(MASTODON_KEY, false)).contains(winner);
  }

  @Test
  void malformedResponsesAndKeysResolveToNothing() {
    when(actors.findByKeyId(any())).thenReturn(Optional.empty());
    when(http.get(eq(MASTODON_ACTOR), any())).thenReturn(ok("{not json"));

    assertThat(resolver().byKeyId(MASTODON_KEY, false)).isEmpty();
    assertThat(resolver().byKeyId("not a uri", false)).isEmpty();
    assertThat(RemoteActorResolver.withoutFragment(null)).isNull();
    assertThat(RemoteActorResolver.withoutFragment("relative/path")).isNull();
    assertThat(RemoteActorResolver.withoutFragment(MASTODON_KEY)).isEqualTo(MASTODON_ACTOR);
  }

  @Test
  void anActorIsResolvedByUriWithCacheAndFallback() {
    RemoteActorEntity fresh = cached(NOW.minusSeconds(60));
    when(actors.findByActorUri(MASTODON_ACTOR.toString())).thenReturn(Optional.of(fresh));
    assertThat(resolver().byActorUri(MASTODON_ACTOR.toString())).contains(fresh);
    verify(http, never()).get(any(), any());

    RemoteActorEntity stale = cached(NOW.minus(RemoteActorResolver.FRESH_FOR).minusSeconds(1));
    when(actors.findByActorUri(MASTODON_ACTOR.toString())).thenReturn(Optional.of(stale));
    when(http.get(MASTODON_ACTOR, instance)).thenReturn(new FederationHttp.Result.Unreachable("x"));
    assertThat(resolver().byActorUri(MASTODON_ACTOR.toString())).contains(stale);

    assertThat(resolver().byActorUri("::bad")).isEmpty();
  }

  @Test
  void aCachedActorIsReusedWhileFreshAndRefetchedOnceStale() {
    RemoteActorEntity fresh = cached(NOW.minusSeconds(60));
    assertThat(resolver().refreshed(fresh)).contains(fresh);
    verify(actors, never()).findByActorUri(any());

    RemoteActorEntity stale = cached(NOW.minus(RemoteActorResolver.FRESH_FOR).minusSeconds(1));
    when(actors.findByActorUri(MASTODON_ACTOR.toString())).thenReturn(Optional.of(stale));
    when(http.get(MASTODON_ACTOR, instance)).thenReturn(new FederationHttp.Result.Unreachable("x"));
    assertThat(resolver().refreshed(stale)).contains(stale);
    verify(http).get(MASTODON_ACTOR, instance);
  }
}
