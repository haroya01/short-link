package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RelayStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00.123456789Z");
  private static final String HOST = "mastodon.example";
  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String POST = ALICE + "/statuses/1";
  private static final String RELAY = "https://relay.example/tag/dev";
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private RemoteActorResolver resolver;
  @Mock private RemoteNoteIntake intake;
  @Mock private FederationRelayedNoteRepository relayed;
  @Mock private RelaySubscriptions subscriptions;
  @Mock private RemoteNoteReachRepository reach;
  @Mock private PlatformTransactionManager transactions;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RelayTally tally = new RelayTally(meters, clock);

  private RelayStore store(boolean enabled, int perDay) {
    return new RelayStore(
        resolver,
        intake,
        relayed,
        subscriptions,
        tally,
        new RelayProperties(
            enabled, 30, perDay, 10, Duration.ofSeconds(5), Duration.ofSeconds(4), null, List.of()),
        reach,
        new TransactionTemplate(transactions),
        clock);
  }

  private static JsonNode post(String extra) {
    return JSON.readTree(
        """
        {"id":"%s","type":"Note","attributedTo":"%s",
         "to":["https://www.w3.org/ns/activitystreams#Public"],
         "content":"<p>안녕</p>","contentMap":{"ko":"<p>안녕</p>"}%s}"""
            .formatted(POST, ALICE, extra));
  }

  private static RemoteNoteParser.Parsed parsed(JsonNode post) {
    return new RemoteNoteParser(URLS).parse(post, post);
  }

  private static RemoteActorEntity actor(String uri, String domain) {
    return new RemoteActorEntity(
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
            true,
            true),
        NOW);
  }

  private String kept(RelayStore store, JsonNode post) {
    return store.keep(HOST, post, parsed(post), RELAY);
  }

  @Test
  void aWantedPostIsKeptForNoOneAndMarkedWithItsRelayInTheSameTransaction() {
    JsonNode post = post("");
    RemoteActorEntity alice = actor(ALICE, HOST);
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(alice));
    when(subscriptions.relayId(RELAY)).thenReturn(7L);
    when(intake.relayed(any(), any())).thenReturn(Optional.of(42L));

    assertThat(kept(store(true, 2_000), post)).isEqualTo("stored");

    Instant kept = Instant.parse("2026-10-17T03:00:00.123456Z");
    InOrder order = inOrder(intake, relayed, reach, transactions);
    order.verify(intake).relayed(any(), any());
    order.verify(relayed).mark(42L, 7L, kept);
    order
        .verify(reach)
        .track(
            42L,
            new ReachTarget(HOST, "1"),
            RemoteNoteReachRepository.Counts.NONE,
            kept,
            kept.plusSeconds(3_600));
    order.verify(transactions).commit(any());
  }

  @Test
  void aKeptPostWhoseIdIsNotAMastodonStatusHasNoCountsToAskFor() {
    JsonNode post =
        JSON.readTree(
            """
            {"id":"https://mastodon.example/notes/9","type":"Note","attributedTo":"%s",
             "to":["https://www.w3.org/ns/activitystreams#Public"],"content":"<p>hi</p>"}"""
                .formatted(ALICE));
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(ALICE, HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.of(43L));

    assertThat(kept(store(true, 2_000), post)).isEqualTo("stored");

    verify(relayed).mark(any(), any(), any());
    verifyNoInteractions(reach);
  }

  @Test
  void aPostThatNamesAMemberIsLeftToTheDeliveryItsServerMakesToThem() {
    JsonNode post =
        post(
            ",\"tag\":[{\"type\":\"Mention\",\"href\":\"https://kurl.me/ap/actors/abc\","
                + "\"name\":\"@yuki@kurl.me\"}]");

    assertThat(kept(store(true, 2_000), post)).isEqualTo("addressed");
    verifyNoInteractions(resolver, intake, relayed);
  }

  @Test
  void noMoreAreKeptOnceADaysWorthHasBeenEvenAcrossARestart() {
    when(relayed.relayedSince(Instant.parse("2026-10-16T15:00:00Z"))).thenReturn(2L);
    RelayStore store = store(true, 2);

    store.load();

    assertThat(tally.today("stored")).isEqualTo(2);
    assertThat(meters.find("federation.relay").counter()).isNull();
    assertThat(kept(store, post(""))).isEqualTo("store-cap");
    verifyNoInteractions(resolver, intake);
  }

  @Test
  void nothingIsCountedAtStartWhereRelaysAreOff() {
    store(false, 2_000).load();

    verifyNoInteractions(relayed);
    assertThat(tally.today()).isEmpty();
  }

  @Test
  void aPostIsKeptOnlyWithItsAuthorAsTheirOwnServerGivesThem() {
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.empty());
    assertThat(kept(store(true, 2_000), post(""))).isEqualTo("no-author");

    when(resolver.byActorUri(ALICE))
        .thenReturn(
            Optional.of(actor("https://elsewhere.example/users/alice", "elsewhere.example")));
    assertThat(kept(store(true, 2_000), post(""))).isEqualTo("no-author");

    RemoteActorEntity suspended = actor(ALICE, HOST);
    ReflectionTestUtils.setField(suspended, "serverBlock", "SUSPEND");
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(suspended));
    assertThat(kept(store(true, 2_000), post(""))).isEqualTo("no-author");

    verifyNoInteractions(intake, relayed);
  }

  @Test
  void aPostThatIsNotKeptLeavesNoMark() {
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(actor(ALICE, HOST)));
    when(intake.relayed(any(), any())).thenReturn(Optional.empty());

    assertThat(kept(store(true, 2_000), post(""))).isEqualTo("not-kept");
    verify(relayed, never()).mark(any(), any(), any());
  }
}
