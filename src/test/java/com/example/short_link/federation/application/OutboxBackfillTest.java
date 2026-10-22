package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxBackfillTest {

  private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z");
  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String OUTBOX = ALICE + "/outbox";
  private static final String FIRST = OUTBOX + "?page=true";
  private static final Signer INSTANCE = new Signer("https://kurl.me/ap/instance#main-key", null);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private RemoteActorRepository actors;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private RemoteNotes remoteNotes;
  @Mock private RemoteNoteIntake intake;
  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;

  private RemoteActorEntity alice;

  @BeforeEach
  void aliceIsKnown() {
    alice = actor(ALICE, "mastodon.example");
  }

  private static RemoteActorEntity actor(String uri, String domain) {
    RemoteActorEntity actor =
        new RemoteActorEntity(
            new RemoteActorDocument(
                uri,
                uri + "#main-key",
                "pem",
                uri + "/inbox",
                null,
                "alice",
                domain,
                null,
                null,
                null,
                false,
                false),
            NOW);
    ReflectionTestUtils.setField(actor, "id", 42L);
    return actor;
  }

  private OutboxBackfill backfill(Executor executor, Duration wait) {
    return new OutboxBackfill(
        actors,
        standings,
        remoteNotes,
        intake,
        http,
        signingKeys,
        JSON,
        executor,
        Clock.fixed(NOW, ZoneOffset.UTC),
        wait);
  }

  private OutboxBackfill backfill() {
    return backfill(Runnable::run, Duration.ofSeconds(1));
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
  }

  private void due() {
    when(actors.findById(42L)).thenReturn(Optional.of(alice));
    when(actors.claimOutbox(42L, NOW, NOW.minus(Duration.ofHours(6)))).thenReturn(true);
    when(remoteNotes.countListed(42L, 5)).thenReturn(0);
  }

  private void serves(String uri, FederationHttp.Result result) {
    when(http.get(URI.create(uri), INSTANCE)).thenReturn(result);
  }

  private List<String> keptUris() {
    ArgumentCaptor<JsonNode> objects = ArgumentCaptor.forClass(JsonNode.class);
    verify(intake, atLeastOnce()).fromHistory(any(), objects.capture());
    List<String> uris = new ArrayList<>();
    for (JsonNode object : objects.getAllValues()) {
      uris.add(object.path("id").asString());
    }
    return uris;
  }

  @Test
  void aSparseMastodonAccountIsFilledFromItsOutboxFirstPage() {
    due();
    when(signingKeys.forInstance()).thenReturn(INSTANCE);
    serves(ALICE, ok(RemoteActorFixtures.MASTODON));
    serves(OUTBOX, ok(FederationFixtures.text("mastodon-outbox.json")));
    serves(FIRST, ok(FederationFixtures.text("mastodon-outbox-page.json")));
    when(intake.fromHistory(any(), any())).thenReturn(true);

    backfill().fillIn(42L, 7L);

    assertThat(keptUris())
        .containsExactly(
            ALICE + "/statuses/113300000000000005",
            "https://mastodon.example/users/alice/statuses/113300000000000004/activity",
            ALICE + "/statuses/113300000000000003",
            ALICE + "/statuses/113300000000000002",
            ALICE + "/statuses/113300000000000001");
    verify(signingKeys, times(1)).forInstance();
  }

  @Test
  void anOutboxListingItsItemsOrEmbeddingItsFirstPageNeedsNoOtherFetch() {
    due();
    when(signingKeys.forInstance()).thenReturn(INSTANCE);
    serves(ALICE, ok(RemoteActorFixtures.MASTODON));
    serves(
        OUTBOX,
        ok(
            """
            {"type":"OrderedCollection","orderedItems":[
              {"type":"Create","object":{"id":"https://mastodon.example/s/1"}},
              {"id":"https://mastodon.example/s/2","type":"Page"},
              {"type":"Create","object":"https://mastodon.example/s/3"}]}
            """));
    when(intake.fromHistory(any(), any())).thenReturn(false, true);

    backfill().fillIn(42L, 7L);

    assertThat(keptUris())
        .containsExactly("https://mastodon.example/s/1", "https://mastodon.example/s/2");

    org.mockito.Mockito.reset(intake, http);
    serves(ALICE, ok(RemoteActorFixtures.MASTODON));
    serves(
        OUTBOX,
        ok(
            """
            {"type":"OrderedCollection","first":{"type":"OrderedCollectionPage",
              "items":[{"type":"Create","object":{"id":"https://mastodon.example/s/4"}}]}}
            """));
    backfill().followed(42L);
    assertThat(keptUris()).containsExactly("https://mastodon.example/s/4");
  }

  @Test
  void atMostTwentyNotesAreKept() {
    due();
    when(signingKeys.forInstance()).thenReturn(INSTANCE);
    serves(ALICE, ok(RemoteActorFixtures.MASTODON));
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < 30; i++) {
      items.append(i == 0 ? "" : ",").append("{\"id\":\"https://mastodon.example/s/" + i + "\"}");
    }
    serves(OUTBOX, ok("{\"orderedItems\":[" + items + "]}"));
    when(intake.fromHistory(any(), any())).thenReturn(true);

    backfill().fillIn(42L, 7L);

    verify(intake, times(OutboxBackfill.MAX_NOTES)).fromHistory(any(), any());
  }

  @Test
  void onlyTheAccountsOwnServerIsAskedAndAFailedOrOddAnswerKeepsNothing() {
    when(actors.findById(42L)).thenReturn(Optional.of(alice));
    when(actors.claimOutbox(any(), any(), any())).thenReturn(true);
    when(remoteNotes.countListed(42L, 5)).thenReturn(0);
    when(signingKeys.forInstance()).thenReturn(INSTANCE);
    OutboxBackfill backfill = backfill();

    serves(ALICE, new FederationHttp.Result.Failed(410, "HTTP 410"));
    backfill.fillIn(42L, 7L);

    serves(ALICE, ok("not json"));
    backfill.fillIn(42L, 7L);

    serves(ALICE, ok("[1, 2]"));
    backfill.fillIn(42L, 7L);

    serves(ALICE, ok("{\"outbox\":\"https://elsewhere.example/outbox\"}"));
    backfill.fillIn(42L, 7L);

    serves(ALICE, ok("{\"outbox\":\"" + OUTBOX + "\"}"));
    serves(OUTBOX, ok("{\"first\":\"https://elsewhere.example/page\"}"));
    backfill.fillIn(42L, 7L);

    serves(OUTBOX, ok("{\"type\":\"OrderedCollection\"}"));
    backfill.fillIn(42L, 7L);

    serves(OUTBOX, ok("{\"orderedItems\":{\"id\":\"x\"},\"first\":\"" + FIRST + "\"}"));
    serves(FIRST, new FederationHttp.Result.Unreachable("SocketTimeoutException"));
    backfill.fillIn(42L, 7L);

    verifyNoInteractions(intake);
  }

  @Test
  void aCrowdedRecentlyLookedAtSuspendedOrUnknownAccountIsNotFetched() {
    OutboxBackfill backfill = backfill();

    backfill.fillIn(42L, null);

    when(actors.findById(41L)).thenReturn(Optional.empty());
    backfill.fillIn(41L, 7L);

    RemoteActorEntity suspended = actor("https://gone.example/users/x", "gone.example");
    ReflectionTestUtils.setField(suspended, "id", 43L);
    ReflectionTestUtils.setField(suspended, "serverBlock", "SUSPEND");
    when(actors.findById(43L)).thenReturn(Optional.of(suspended));
    backfill.fillIn(43L, 7L);

    RemoteActorEntity recent = actor(ALICE, "mastodon.example");
    ReflectionTestUtils.setField(recent, "id", 44L);
    ReflectionTestUtils.setField(recent, "outboxCheckedAt", NOW.minus(Duration.ofHours(5)));
    when(actors.findById(44L)).thenReturn(Optional.of(recent));
    backfill.fillIn(44L, 7L);

    RemoteActorEntity raced = actor(ALICE, "mastodon.example");
    ReflectionTestUtils.setField(raced, "id", 45L);
    ReflectionTestUtils.setField(raced, "outboxCheckedAt", NOW.minus(Duration.ofHours(7)));
    when(actors.findById(45L)).thenReturn(Optional.of(raced));
    when(actors.claimOutbox(45L, NOW, NOW.minus(Duration.ofHours(6)))).thenReturn(false);
    backfill.fillIn(45L, 7L);

    when(actors.findById(42L)).thenReturn(Optional.of(alice));
    when(actors.claimOutbox(42L, NOW, NOW.minus(Duration.ofHours(6)))).thenReturn(true);
    when(remoteNotes.countListed(42L, 5)).thenReturn(5);
    backfill.fillIn(42L, 7L);

    verify(actors, times(2)).claimOutbox(any(), any(), any());
    verifyNoInteractions(http, intake, signingKeys);
  }

  @Test
  void nothingIsFetchedForSomeoneWhoBlockedTheAccountOrItsServerButAMuteDoesNotStopIt() {
    when(actors.findById(42L)).thenReturn(Optional.of(alice));
    when(standings.of(7L, List.of(42L), NOW))
        .thenReturn(
            Map.of(42L, new RemoteAccountStanding(false, true, false)),
            Map.of(42L, new RemoteAccountStanding(true, false, false)),
            Map.of(42L, new RemoteAccountStanding(false, false, true)));
    when(actors.claimOutbox(42L, NOW, NOW.minus(Duration.ofHours(6)))).thenReturn(false);
    OutboxBackfill backfill = backfill();

    backfill.fillIn(42L, 7L);
    backfill.fillIn(42L, 7L);
    verify(actors, never()).claimOutbox(any(), any(), any());

    backfill.fillIn(42L, 7L);
    verify(actors).claimOutbox(42L, NOW, NOW.minus(Duration.ofHours(6)));
    verifyNoInteractions(remoteNotes, http);
  }

  @Test
  void aSlowFillIsLeftRunningAndTheRequestGoesOnWithWhatIsKept() {
    due();
    List<Runnable> pending = new ArrayList<>();

    backfill(pending::add, Duration.ofMillis(10)).fillIn(42L, 7L);

    assertThat(pending).hasSize(1);
    verifyNoInteractions(http, intake);
  }

  @Test
  void aFailingFillIsLoggedNotThrownAndAnInterruptIsKept() {
    due();
    when(signingKeys.forInstance()).thenThrow(new IllegalStateException("no instance key"));

    backfill().fillIn(42L, 7L);

    RemoteActorEntity quiet = actor(ALICE, "mastodon.example");
    ReflectionTestUtils.setField(quiet, "id", 46L);
    when(actors.findById(46L)).thenReturn(Optional.of(quiet));
    when(actors.claimOutbox(46L, NOW, NOW.minus(Duration.ofHours(6)))).thenReturn(true);
    when(remoteNotes.countListed(46L, 5)).thenReturn(1);
    Thread.currentThread().interrupt();
    backfill(runnable -> {}, Duration.ofSeconds(5)).fillIn(46L, 7L);
    assertThat(Thread.interrupted()).isTrue();
    verifyNoInteractions(http);
  }
}
