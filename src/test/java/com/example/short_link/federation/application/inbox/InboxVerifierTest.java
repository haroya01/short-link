package com.example.short_link.federation.application.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxVerifierTest {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final ActorKeys.Pem ROTATED = new ActorKeys().generate();
  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  private static final String KEY_ID = "https://mastodon.example/users/alice#main-key";
  private static final String PATH = "/ap/inbox";
  private static final byte[] BODY =
      "{\"type\":\"Follow\",\"id\":\"https://mastodon.example/f/1\"}"
          .getBytes(StandardCharsets.UTF_8);

  @Mock private RemoteActorResolver resolver;
  @Mock private RemoteActorRepository actors;

  private InboxVerifier verifier() {
    return new InboxVerifier(
        resolver,
        actors,
        new FederationProperties("https://kurl.me", null),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RemoteActorEntity alice(String publicKeyPem, Instant fetchedAt) {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            "https://mastodon.example/users/alice",
            KEY_ID,
            publicKeyPem,
            "https://mastodon.example/users/alice/inbox",
            "https://mastodon.example/inbox",
            "alice",
            "mastodon.example",
            null,
            null,
            null),
        fetchedAt);
  }

  private static InboxMessage request(Map<String, String> headers, byte[] body) {
    Map<String, String> withOriginHost = new HashMap<>(headers);
    withOriginHost.put("host", "origin.kurl.me");
    return new InboxMessage(PATH, null, withOriginHost, body);
  }

  private static InboxMessage signed(String privateKeyPem) {
    return request(
        SignedInboxRequests.headers(KEY_ID, privateKeyPem, "kurl.me", PATH, BODY, NOW), BODY);
  }

  @Test
  void verifiesAgainstTheCanonicalHostTheSenderSignedNotTheProxiedOne() {
    RemoteActorEntity alice = alice(KEYS.publicKey(), NOW);
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.of(alice));

    var result = verifier().verify(signed(KEYS.privateKey()), false);

    assertThat(result).isEqualTo(new InboxVerifier.Result.Verified(alice));
  }

  @Test
  void aSignatureForTheOriginHostDoesNotVerify() {
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.of(alice(KEYS.publicKey(), NOW)));
    var headers =
        SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "origin.kurl.me", PATH, BODY, NOW);

    assertThat(verifier().verify(request(headers, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
  }

  @Test
  void acceptsTheSignatureInTheAuthorizationHeader() {
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.of(alice(KEYS.publicKey(), NOW)));
    Map<String, String> headers =
        new HashMap<>(
            SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "kurl.me", PATH, BODY, NOW));
    headers.put("authorization", "Signature " + headers.remove("signature"));

    assertThat(verifier().verify(request(headers, BODY), false))
        .isInstanceOf(InboxVerifier.Result.Verified.class);
  }

  @Test
  void anUnsignedRequestIsRejectedBeforeAnyKeyLookup() {
    Map<String, String> headers =
        new HashMap<>(
            SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "kurl.me", PATH, BODY, NOW));
    headers.remove("signature");
    headers.put("authorization", "Bearer token");

    assertThat(verifier().verify(request(headers, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature"));
    verifyNoInteractions(resolver, actors);
  }

  @Test
  void theDigestMustBeAmongTheSignedHeaders() {
    var headers =
        SignedInboxRequests.headers(
            KEY_ID,
            KEYS.privateKey(),
            "kurl.me",
            PATH,
            BODY,
            NOW,
            List.of(HttpSignatures.REQUEST_TARGET, "host", "date"));

    assertThat(verifier().verify(request(headers, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signed-headers"));
    verifyNoInteractions(resolver);
  }

  @Test
  void dateMustSitWithinAnHourEitherSide() {
    InboxVerifier verifier = verifier();
    for (Instant date :
        List.of(NOW.minus(Duration.ofMinutes(61)), NOW.plus(Duration.ofMinutes(61)))) {
      var headers =
          SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "kurl.me", PATH, BODY, date);
      assertThat(verifier.verify(request(headers, BODY), false))
          .isEqualTo(new InboxVerifier.Result.Rejected("date"));
    }
    Map<String, String> malformed =
        new HashMap<>(
            SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "kurl.me", PATH, BODY, NOW));
    malformed.put("date", "yesterday");
    assertThat(verifier.verify(request(malformed, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("date"));
    malformed.remove("date");
    assertThat(verifier.verify(request(malformed, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("date"));
    verifyNoInteractions(resolver);
  }

  @Test
  void aBodyThatDoesNotMatchItsDigestIsRejected() {
    var headers =
        SignedInboxRequests.headers(KEY_ID, KEYS.privateKey(), "kurl.me", PATH, BODY, NOW);
    byte[] swapped = "{\"type\":\"Delete\"}".getBytes(StandardCharsets.UTF_8);

    assertThat(verifier().verify(request(headers, swapped), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("digest"));
    verifyNoInteractions(resolver);
  }

  @Test
  void digestAcceptsAnySha256EntryAmongSeveral() {
    String sha256 = HttpSignatures.digest(BODY).substring("SHA-256=".length());

    assertThat(InboxVerifier.digestMatches("SHA-512=abc, sha-256=" + sha256, BODY)).isTrue();
    assertThat(InboxVerifier.digestMatches("SHA-512=abc", BODY)).isFalse();
    assertThat(InboxVerifier.digestMatches("garbage", BODY)).isFalse();
    assertThat(InboxVerifier.digestMatches(null, BODY)).isFalse();
  }

  @Test
  void anUnresolvableKeyIsRejected() {
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.empty());

    assertThat(verifier().verify(signed(KEYS.privateKey()), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("unknown-key"));
  }

  @Test
  void aJustFetchedKeyThatFailsIsNotFetchedAgain() {
    when(resolver.byKeyId(KEY_ID, false))
        .thenReturn(Optional.of(alice(KEYS.publicKey(), NOW.minus(Duration.ofMinutes(9)))));

    assertThat(verifier().verify(signed(ROTATED.privateKey()), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
    verify(resolver, never()).byKeyId(KEY_ID, true);
  }

  @Test
  void anOlderCachedKeyIsRefetchedOnceWhenTheRemoteRotated() {
    when(resolver.byKeyId(KEY_ID, false))
        .thenReturn(Optional.of(alice(KEYS.publicKey(), NOW.minus(Duration.ofHours(3)))));
    RemoteActorEntity rotated = alice(ROTATED.publicKey(), NOW);
    when(resolver.byKeyId(KEY_ID, true)).thenReturn(Optional.of(rotated));

    assertThat(verifier().verify(signed(ROTATED.privateKey()), false))
        .isEqualTo(new InboxVerifier.Result.Verified(rotated));
  }

  @Test
  void aForgedSignatureStaysRejectedAfterTheRefetch() {
    when(resolver.byKeyId(KEY_ID, false))
        .thenReturn(Optional.of(alice(KEYS.publicKey(), NOW.minus(Duration.ofHours(3)))));
    when(resolver.byKeyId(KEY_ID, true)).thenReturn(Optional.of(alice(KEYS.publicKey(), NOW)));

    assertThat(verifier().verify(signed(ROTATED.privateKey()), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
  }

  @Test
  void aStoredKeyThatIsNotAPemNeverVerifies() {
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.of(alice("not a key", NOW)));

    assertThat(verifier().verify(signed(KEYS.privateKey()), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
  }

  @Test
  void cachedOnlyVerificationNeverFetches() {
    when(actors.findByKeyId(KEY_ID))
        .thenReturn(Optional.of(alice(KEYS.publicKey(), NOW.minus(Duration.ofDays(3)))));

    assertThat(verifier().verify(signed(KEYS.privateKey()), true))
        .isInstanceOf(InboxVerifier.Result.Verified.class);
    assertThat(verifier().verify(signed(ROTATED.privateKey()), true))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
    when(actors.findByKeyId(KEY_ID)).thenReturn(Optional.empty());
    assertThat(verifier().verify(signed(KEYS.privateKey()), true))
        .isEqualTo(new InboxVerifier.Result.Rejected("unknown-key"));
    verify(resolver, never()).byKeyId(anyString(), anyBoolean());
  }

  @Test
  void theSignatureCoversThePathItWasSentTo() {
    when(resolver.byKeyId(KEY_ID, false)).thenReturn(Optional.of(alice(KEYS.publicKey(), NOW)));
    var headers =
        SignedInboxRequests.headers(
            KEY_ID, KEYS.privateKey(), "kurl.me", "/ap/actors/x/inbox", BODY, NOW);

    assertThat(verifier().verify(request(headers, BODY), false))
        .isEqualTo(new InboxVerifier.Result.Rejected("signature-mismatch"));
  }
}
