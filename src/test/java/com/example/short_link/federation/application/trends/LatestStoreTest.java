package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class LatestStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-21T03:00:00.123456789Z");
  private static final String HOST = "mstdn.example";
  private static final String ALICE = "https://mstdn.example/users/alice";
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private RemoteActorResolver resolver;
  @Mock private RemoteNoteIntake intake;
  @Mock private FederationRelayedNoteRepository relayed;
  @Mock private RemoteNoteReachRepository reach;
  @Mock private RemoteNotes remoteNotes;
  @Mock private PlatformTransactionManager transactions;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RelayTally tally = new RelayTally(new SimpleMeterRegistry());
  private final RemoteNoteParser parser = new RemoteNoteParser(URLS);

  private LatestStore store(int perDay) {
    return store(perDay, 0);
  }

  private LatestStore store(int perDay, int trendedPerDay) {
    return new LatestStore(
        resolver,
        intake,
        parser,
        relayed,
        tally,
        new LatestProperties(true, List.of(HOST), 40, 0, null, null, true, null, 0, perDay),
        new ReachProperties(true, 0, trendedPerDay, null, 0, null, null),
        reach,
        remoteNotes,
        new TransactionTemplate(transactions),
        clock);
  }

  private static TrendingStatusReader.Read read(String domain) {
    return new TrendingStatusReader(new RemoteNoteParser(URLS), JSON)
        .read(TrendingStatusReaderTest.status(ALICE + "/statuses/1", "alice"), domain)
        .orElseThrow();
  }

  private static RemoteActorEntity actor(String domain) {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            ALICE,
            ALICE + "#main-key",
            "pem",
            ALICE + "/inbox",
            null,
            "alice",
            domain,
            null,
            null,
            null,
            true,
            true),
        NOW);
  }

  @Test
  void aPulledPostIsKeptUnderItsAuthorAndMarkedWithItsServerInTheSameTransaction() {
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.of(42L));

    assertThat(store(2_000).keep(HOST, read(HOST))).isEqualTo("pulled");

    ArgumentCaptor<RemoteNoteParser.Parsed> note =
        ArgumentCaptor.forClass(RemoteNoteParser.Parsed.class);
    Instant kept = Instant.parse("2026-10-21T03:00:00.123456Z");
    InOrder order = inOrder(intake, relayed, reach, remoteNotes, transactions);
    order.verify(intake).relayed(any(), note.capture());
    order.verify(relayed).markPulled(42L, HOST, kept);
    order
        .verify(reach)
        .track(
            42L,
            new ReachTarget(HOST, "1"),
            new RemoteNoteReachRepository.Counts(3, 2, 1),
            kept,
            kept.plusSeconds(3_600));
    order.verify(remoteNotes).preview(42L, "hello", false, null);
    order.verify(transactions).commit(any());
    assertThat(note.getValue().uri()).isEqualTo(ALICE + "/statuses/1");
    assertThat(tally.today("pulled")).isEqualTo(1);
  }

  @Test
  void anAuthorThatCannotBeFetchedOrLivesElsewhereKeepsNothing() {
    when(resolver.byActorUri(ALICE))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(actor("other.example")));
    LatestStore store = store(2_000);

    assertThat(store.keep(HOST, read(HOST))).isEqualTo("pull-no-author");
    assertThat(store.keep(HOST, read(HOST))).isEqualTo("pull-no-author");
    verifyNoInteractions(intake);
  }

  @Test
  void aDaysWorthCountedAtStartFromWhatIsKeptEndsThePullsUntilTomorrow() {
    when(relayed.pulledSince(tally.dayStart())).thenReturn(1L);
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.of(42L));
    LatestStore store = store(2);

    store.load();
    assertThat(store.leftToday()).isEqualTo(1);
    assertThat(store.keep(HOST, read(HOST))).isEqualTo("pulled");
    assertThat(store.leftToday()).isZero();
    assertThat(store.keep(HOST, read(HOST))).isEqualTo("pull-cap");

    verify(relayed, times(1)).pulledSince(any());
    verify(intake, times(1)).relayed(any(), any());
    verify(relayed, never()).mark(any(), any(), any());
  }

  @Test
  void aTrendingPostIsKeptAsTrendedUnderACapOfItsOwnThatPullsDoNotSpend() {
    when(relayed.pulledSince(tally.dayStart())).thenReturn(5L);
    when(relayed.trendedSince(tally.dayStart())).thenReturn(1L);
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.of(42L));
    LatestStore store = store(5, 2);

    store.load();
    assertThat(store.leftToday()).isZero();
    assertThat(store.keepTrended(HOST, read(HOST))).isEqualTo("trended");
    assertThat(store.keepTrended(HOST, read(HOST))).isEqualTo("trend-cap");

    verify(relayed).markTrended(42L, HOST, Instant.parse("2026-10-21T03:00:00.123456Z"));
    verify(relayed, never()).markPulled(any(), any(), any());
    verify(intake, times(1)).relayed(any(), any());
  }

  @Test
  void aTrendingPostWhoseAuthorCannotBeFetchedOrWhichIsNotKeptSaysSoAsATrend() {
    when(resolver.byActorUri(ALICE))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(actor(HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.empty());
    LatestStore store = store(5, 10);

    assertThat(store.keepTrended(HOST, read(HOST))).isEqualTo("trend-no-author");
    assertThat(store.keepTrended(HOST, read(HOST))).isEqualTo("trend-not-kept");
    verifyNoInteractions(reach);
  }

  @Test
  void theCardThePostsServerMadeGoesAlongAsItsLinkCard() {
    ObjectNode status = TrendingStatusReaderTest.status(ALICE + "/statuses/1", "alice");
    status.put("content", "<p>see https://youtu.be/abc</p>");
    status.set(
        "card",
        JSON.readTree(
            "{\"url\":\"https://www.youtube.com/watch?v=abc\",\"title\":\"A video\","
                + "\"description\":\"About it\",\"image\":\"https://i.yt/a.jpg\"}"));
    TrendingStatusReader.Read read =
        new TrendingStatusReader(new RemoteNoteParser(URLS), JSON).read(status, HOST).orElseThrow();
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.of(42L));

    assertThat(store(2_000, 10).keepTrended(HOST, read)).isEqualTo("trended");

    verify(remoteNotes)
        .preview(
            42L,
            "see https://youtu.be/abc",
            false,
            new RemoteNotes.LinkCard(
                "https://www.youtube.com/watch?v=abc",
                "A video",
                "About it",
                "https://i.yt/a.jpg"));
  }
}
