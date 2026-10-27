package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayBreaker;
import com.example.short_link.federation.application.relay.RelayIntake;
import com.example.short_link.federation.application.relay.RelayProperties;
import com.example.short_link.federation.application.relay.RelayStore;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RelayWorkerTest {

  private static final String POST = "https://mastodon.example/users/alice/statuses/1";
  private static final String RELAY = "https://relay.example/tag/dev";
  private static final URI POST_URI = URI.create(POST);
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final RelayProperties PROPS =
      new RelayProperties(
          true, 30, 2_000, 10, Duration.ofSeconds(3), Duration.ofSeconds(4), null, List.of());

  @Mock private RelaySubscriptions subscriptions;
  @Mock private RemoteNotes remoteNotes;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private RelayStore store;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T19:20:00Z"), ZoneOffset.UTC);
  private final RelayTally tally = new RelayTally(meters, clock);
  private RelayBreaker breaker;
  private RelayIntake intake;
  private RelayWorker worker;

  @BeforeEach
  void worker() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient()
        .when(subscriptions.setting())
        .thenReturn(new RelaySubscriptions.Setting(RelayMode.COUNT, List.of("ko", "ja")));
    build(PROPS);
  }

  private void build(RelayProperties props) {
    breaker = new RelayBreaker(props, tally, subscriptions, clock);
    intake = new RelayIntake(subscriptions, breaker, tally, props, URLS, meters);
    worker =
        new RelayWorker(
            intake,
            subscriptions,
            breaker,
            tally,
            store,
            props,
            remoteNotes,
            serverBlocks,
            http,
            signingKeys,
            new RemoteNoteParser(URLS),
            JsonMapper.builder().build(),
            meters,
            false);
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
  }

  private static String note(String id, String attributedTo, String extra) {
    return """
        {"id":"%s","type":"Note","attributedTo":"%s",
         "to":["https://www.w3.org/ns/activitystreams#Public"],
         "cc":["https://mastodon.example/users/alice/followers"],
         "content":"<p>안녕</p>","contentMap":{"ko":"<p>안녕</p>"}%s}"""
        .formatted(id, attributedTo, extra);
  }

  private String outcomeOf(FederationHttp.Result answer) {
    when(http.get(POST_URI, instance, PROPS.patience())).thenReturn(answer);
    return processed();
  }

  private String processed() {
    intake.offer(POST, RELAY);
    Map<String, Long> before = tally.today();
    assertThat(worker.processNext()).isTrue();
    Map<String, Long> after = tally.today();
    return after.keySet().stream()
        .filter(outcome -> after.get(outcome) > before.getOrDefault(outcome, 0L))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPublicPostInAWantedLanguageFromItsOwnServerIsWanted() {
    assertThat(outcomeOf(ok(note(POST, "https://mastodon.example/users/alice", ""))))
        .isEqualTo("wanted");
    verify(http).get(POST_URI, instance, PROPS.patience());
    assertThat(meters.get("federation.relay.process").timer().count()).isEqualTo(1);
    assertThat(worker.processNext()).isFalse();
    verify(store, never()).keep(any(), any(), any(), any());
  }

  @Test
  void inStoreAWantedPostIsHandedToTheStoreWithTheRelayThatAnnouncedIt() {
    when(subscriptions.setting())
        .thenReturn(new RelaySubscriptions.Setting(RelayMode.STORE, List.of("ko", "ja")));
    when(store.keep(eq("mastodon.example"), any(), any(), eq(RELAY))).thenReturn("stored");

    assertThat(outcomeOf(ok(note(POST, "https://mastodon.example/users/alice", ""))))
        .isEqualTo("stored");
    assertThat(
            outcomeOf(
                ok(
                    note(POST, "https://mastodon.example/users/alice", "")
                        .replace("\"contentMap\":{\"ko\"", "\"contentMap\":{\"en\""))))
        .isEqualTo("language");
    verify(store).keep(eq("mastodon.example"), any(), any(), eq(RELAY));
  }

  @Test
  void aPostKeptAlreadyOrOnABlockedServerIsNotFetched() {
    when(remoteNotes.exists(POST)).thenReturn(true);
    assertThat(processed()).isEqualTo("kept-already");

    when(remoteNotes.exists(POST)).thenReturn(false);
    when(serverBlocks.severityFor("mastodon.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));
    assertThat(processed()).isEqualTo("suspended");

    when(serverBlocks.severityFor("mastodon.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    assertThat(processed()).isEqualTo("limited");
    verify(http, never()).get(any(), any(), any());
  }

  @Test
  void whatItsServerDoesNotGiveOrGivesAsSomethingElseIsDropped() {
    assertThat(outcomeOf(new FederationHttp.Result.Failed(401, "HTTP 401")))
        .isEqualTo("unanswered");
    assertThat(outcomeOf(ok("{not json"))).isEqualTo("not-a-post");
    assertThat(outcomeOf(ok("{\"id\":\"" + POST + "\",\"type\":\"Person\"}")))
        .isEqualTo("not-a-post");
    assertThat(
            outcomeOf(
                ok(
                    note(
                        "https://elsewhere.example/statuses/1",
                        "https://mastodon.example/users/alice",
                        ""))))
        .isEqualTo("foreign");
    assertThat(outcomeOf(ok(note(POST, "https://elsewhere.example/users/alice", ""))))
        .isEqualTo("foreign");
  }

  @Test
  void aPostThatIsNotPublicARepliesASensitiveOneOrInAnotherLanguageIsDropped() {
    assertThat(
            outcomeOf(
                ok(
                    note(POST, "https://mastodon.example/users/alice", "")
                        .replace(
                            "\"to\":[\"https://www.w3.org/ns/activitystreams#Public\"]",
                            "\"to\":[\"https://mastodon.example/users/alice/followers\"]"))))
        .isEqualTo("not-public");
    assertThat(
            outcomeOf(
                ok(
                    note(
                        POST,
                        "https://mastodon.example/users/alice",
                        ",\"inReplyTo\":\"https://mastodon.example/statuses/0\""))))
        .isEqualTo("reply");
    assertThat(
            outcomeOf(
                ok(note(POST, "https://mastodon.example/users/alice", ",\"sensitive\":true"))))
        .isEqualTo("sensitive");
    assertThat(
            outcomeOf(
                ok(note(POST, "https://mastodon.example/users/alice", ",\"summary\":\"spoiler\""))))
        .isEqualTo("sensitive");
    assertThat(
            outcomeOf(
                ok(
                    note(POST, "https://mastodon.example/users/alice", "")
                        .replace("\"contentMap\":{\"ko\"", "\"contentMap\":{\"en\""))))
        .isEqualTo("language");
  }

  @Test
  void nothingIsFetchedWhileTheSettingIsOffOrTheWorkHasStoppedItself() {
    intake.offer(POST, RELAY);
    when(subscriptions.setting())
        .thenReturn(new RelaySubscriptions.Setting(RelayMode.OFF, List.of("ko")));
    assertThat(worker.processNext()).isTrue();
    assertThat(tally.today("off")).isEqualTo(1);

    when(subscriptions.setting())
        .thenReturn(new RelaySubscriptions.Setting(RelayMode.COUNT, List.of("ko")));
    intake.offer(POST, RELAY);
    for (int i = 0; i < 5; i++) {
      breaker.processed(Duration.ofSeconds(10), 0);
    }
    assertThat(worker.processNext()).isTrue();
    assertThat(tally.today("tripped")).isEqualTo(1);
    verify(http, never()).get(any(), any(), any());
    verify(remoteNotes, never()).exists(any());
  }

  @Test
  void inStoreSlowKeepingStopsTheWorkAndTheStoreAndNothingQueuedIsFetchedOrKeptAfter() {
    build(
        new RelayProperties(
            true, 30, 2_000, 10, Duration.ofSeconds(3), Duration.ofMillis(20), null, List.of()));
    when(subscriptions.setting())
        .thenReturn(new RelaySubscriptions.Setting(RelayMode.STORE, List.of("ko", "ja")));
    when(http.get(eq(POST_URI), eq(instance), any()))
        .thenReturn(ok(note(POST, "https://mastodon.example/users/alice", "")));
    when(store.keep(eq("mastodon.example"), any(), any(), eq(RELAY)))
        .thenAnswer(
            call -> {
              Thread.sleep(30);
              return "stored";
            });

    for (int i = 0; i < 5; i++) {
      assertThat(processed()).isEqualTo("stored");
    }
    assertThat(breaker.tripped()).isTrue();
    verify(subscriptions).stopStoring();

    assertThat(intake.offer(POST, RELAY)).isEqualTo("tripped");
    verify(store, times(5)).keep(any(), any(), any(), any());
    verify(http, times(5)).get(any(), any(), any());
  }

  @Test
  void theThreadDoesNotStartWhereSchedulingIsOff() {
    worker.start();
    assertThat(worker.isRunning()).isTrue();
    assertThat(Thread.getAllStackTraces().keySet())
        .noneMatch(thread -> thread.getName().equals("kurl-relay"));
    worker.stop();
    assertThat(worker.isRunning()).isFalse();
  }
}
