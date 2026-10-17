package com.example.short_link.federation.application.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DirectorySyncTest {

  private static final String HOST = "qdon.example";
  private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
  private static final Duration PATIENCE = Duration.ofSeconds(20);
  private static final Duration PAGE_GAP = Duration.ofSeconds(12);
  private static final Duration FETCH_GAP = Duration.ofSeconds(3);

  @Mock private FederationDirectorySourceRepository sources;
  @Mock private RemoteActorRepository actors;
  @Mock private RemoteActorResolver resolver;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private final List<Duration> pauses = new ArrayList<>();
  private final DirectoryPacer pacer =
      new DirectoryPacer() {
        @Override
        public void pause(Duration gap) {
          pauses.add(gap);
        }
      };

  @BeforeEach
  void instanceSigner() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(serverBlocks.severityFor(any())).thenReturn(Optional.empty());
  }

  private DirectorySync sync(int newPerRun) {
    return new DirectorySync(
        sources,
        actors,
        resolver,
        serverBlocks,
        http,
        signingKeys,
        JsonMapper.builder().build(),
        new DirectoryProperties(
            true, newPerRun, 1_000, Duration.ofDays(3), PAGE_GAP, FETCH_GAP, PATIENCE),
        pacer,
        new SimpleMeterRegistry(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private FederationDirectorySourceEntity source(int cap) {
    FederationDirectorySourceEntity source = new FederationDirectorySourceEntity(HOST, true, cap);
    when(sources.find(HOST)).thenReturn(Optional.of(source));
    return source;
  }

  private static URI page(int limit, int offset) {
    return URI.create(
        "https://"
            + HOST
            + "/api/v1/directory?local=true&order=active&limit="
            + limit
            + "&offset="
            + offset);
  }

  private static String account(String username, String extra) {
    return """
        {"username":"%1$s","acct":"%1$s","discoverable":true,"uri":"https://%2$s/users/%1$s"%3$s}"""
        .formatted(username, HOST, extra);
  }

  private static FederationHttp.Result ok(List<String> accounts) {
    return new FederationHttp.Result.Ok(
        200, ("[" + String.join(",", accounts) + "]").getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> accounts(int from, int count) {
    return IntStream.range(from, from + count).mapToObj(i -> account("u" + i, "")).toList();
  }

  private static RemoteActorEntity actor(long id, String username) {
    String uri = "https://" + HOST + "/users/" + username;
    RemoteActorEntity actor =
        new RemoteActorEntity(
            new RemoteActorDocument(
                uri,
                uri + "#main-key",
                "pem",
                uri + "/inbox",
                null,
                username,
                HOST,
                null,
                null,
                null,
                true,
                false),
            NOW);
    ReflectionTestUtils.setField(actor, "id", id);
    return actor;
  }

  @SuppressWarnings("unchecked")
  private List<List<Long>> listed() {
    ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
    verify(actors, atLeastOnce()).markListed(ids.capture(), any());
    return ids.getAllValues().stream().map(List::copyOf).toList();
  }

  @Test
  void theDirectoryIsReadMostActiveFirstPageByPageUpToItsCapWithAPauseBetweenRequests() {
    FederationDirectorySourceEntity source = source(200);
    when(http.getJson(page(80, 0), instance, PATIENCE)).thenReturn(ok(accounts(0, 80)));
    when(http.getJson(page(80, 80), instance, PATIENCE)).thenReturn(ok(accounts(80, 80)));
    when(http.getJson(page(40, 160), instance, PATIENCE)).thenReturn(ok(accounts(160, 40)));
    when(actors.findAllByActorUri(anyCollection()))
        .thenAnswer(
            call -> {
              Collection<String> uris = call.getArgument(0);
              return uris.stream()
                  .map(uri -> uri.substring(uri.lastIndexOf('/') + 1))
                  .map(name -> actor(Long.parseLong(name.substring(1)), name))
                  .toList();
            });

    DirectorySync.Outcome outcome = sync(100).sync(HOST);

    assertThat(outcome).isEqualTo(new DirectorySync.Outcome(HOST, 200, 200, 0, 0, 0, null));
    assertThat(pauses).containsExactly(PAGE_GAP, PAGE_GAP);
    assertThat(source.getLastOutcome())
        .isEqualTo("listed 200, known 200, added 0, later 0, skipped 0");
    assertThat(source.getLastSyncedAt()).isEqualTo(NOW);
    assertThat(listed()).extracting(List::size).containsExactly(80, 80, 40);
    verifyNoInteractions(resolver);
  }

  @Test
  void aShortPageEndsTheRead() {
    source(1_000);
    when(http.getJson(page(80, 0), instance, PATIENCE)).thenReturn(ok(accounts(0, 3)));
    when(actors.findAllByActorUri(anyCollection())).thenReturn(List.of());
    when(resolver.byActorUri(any())).thenAnswer(call -> Optional.of(actor(7, "x")));

    assertThat(sync(100).sync(HOST).listed()).isEqualTo(3);
    verify(http, never()).getJson(page(80, 80), instance, PATIENCE);
  }

  @Test
  void aNewAccountIsStoredOnlyAsItsOwnServerGivesItAndOnlySoManyARun() {
    source(80);
    RemoteActorEntity onSuspended = actor(9, "u2");
    ReflectionTestUtils.setField(onSuspended, "serverBlock", "SUSPEND");
    when(http.getJson(page(80, 0), instance, PATIENCE)).thenReturn(ok(accounts(0, 5)));
    when(actors.findAllByActorUri(anyCollection())).thenReturn(List.of(actor(1, "u0")));
    when(resolver.byActorUri("https://" + HOST + "/users/u1"))
        .thenReturn(Optional.of(actor(2, "u1")));
    when(resolver.byActorUri("https://" + HOST + "/users/u2")).thenReturn(Optional.of(onSuspended));
    when(resolver.byActorUri("https://" + HOST + "/users/u3")).thenReturn(Optional.empty());

    DirectorySync.Outcome outcome = sync(3).sync(HOST);

    assertThat(outcome).isEqualTo(new DirectorySync.Outcome(HOST, 5, 1, 1, 1, 2, null));
    assertThat(listed()).containsExactly(List.of(1L, 2L));
    assertThat(pauses).containsExactly(FETCH_GAP, FETCH_GAP, FETCH_GAP);
    verify(resolver, never()).byActorUri("https://" + HOST + "/users/u4");
  }

  @Test
  void anAccountListedWithoutConsentFromElsewhereOrThatMovedIsSkipped() {
    source(80);
    when(http.getJson(page(80, 0), instance, PATIENCE))
        .thenReturn(
            ok(
                List.of(
                    account("a", "").replace("\"discoverable\":true", "\"discoverable\":false"),
                    account("b", "").replace("\"discoverable\":true,", ""),
                    account("c", "").replace("https://" + HOST, "https://elsewhere.example"),
                    account("d", "").replace(",\"uri\":\"https://" + HOST + "/users/d\"", ""),
                    account("e", ",\"moved\":{\"acct\":\"e@new.example\"}"),
                    account("f", ",\"moved_acct\":\"f\""),
                    account("g", ",\"bot\":true,\"group\":true,\"moved\":null"))));
    when(actors.findAllByActorUri(anyCollection()))
        .thenReturn(List.of(actor(5, "g"), actor(6, "f")));

    DirectorySync.Outcome outcome = sync(100).sync(HOST);

    assertThat(outcome).isEqualTo(new DirectorySync.Outcome(HOST, 7, 2, 0, 0, 5, null));
    assertThat(listed()).containsExactly(List.of(6L, 5L));
    verifyNoInteractions(resolver);
  }

  @Test
  void aServerThatDoesNotAnswerWaitsLongerEachTimeAndAnAnswerResetsIt() {
    FederationDirectorySourceEntity source = source(80);
    when(http.getJson(page(80, 0), instance, PATIENCE))
        .thenReturn(
            new FederationHttp.Result.Failed(429, "HTTP 429"),
            new FederationHttp.Result.Unreachable("SocketTimeoutException"),
            ok(List.of()),
            new FederationHttp.Result.Ok(200, "{\"error\":\"x\"}".getBytes(StandardCharsets.UTF_8)),
            ok(List.of()));
    DirectorySync sync = sync(100);
    List<Instant> retries = new ArrayList<>();
    List<String> outcomes = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      sync.sync(HOST);
      retries.add(source.getRetryAfter());
      outcomes.add(source.getLastOutcome().split(";")[0]);
    }

    assertThat(retries)
        .containsExactly(
            NOW.plus(Duration.ofHours(1)),
            NOW.plus(Duration.ofHours(6)),
            null,
            NOW.plus(Duration.ofHours(1)),
            null);
    assertThat(outcomes)
        .containsExactly(
            "HTTP 429",
            "unreachable: SocketTimeoutException",
            "listed 0, known 0, added 0, later 0, skipped 0",
            "not a directory",
            "listed 0, known 0, added 0, later 0, skipped 0");
  }

  @Test
  void aBlockedServerIsNotAskedAtAll() {
    FederationDirectorySourceEntity source = source(80);
    when(serverBlocks.severityFor(HOST)).thenReturn(Optional.of(ServerBlockSeverity.LIMIT));

    assertThat(sync(100).sync(HOST).failure()).isEqualTo("blocked here");
    assertThat(source.getRetryAfter()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    verifyNoInteractions(http, resolver);
  }

  @Test
  void aRunReadsEveryEnabledSourceWhoseWaitIsOverThenForgetsWhoeverGaveNoConsentInThreeDays() {
    FederationDirectorySourceEntity due =
        new FederationDirectorySourceEntity("due.example", true, 80);
    FederationDirectorySourceEntity off =
        new FederationDirectorySourceEntity("off.example", false, 80);
    FederationDirectorySourceEntity waiting =
        new FederationDirectorySourceEntity("wait.example", true, 80);
    waiting.failed("HTTP 503", NOW.plusSeconds(60));
    when(sources.list()).thenReturn(List.of(due, off, waiting));
    when(sources.find("due.example")).thenReturn(Optional.of(due));
    when(http.getJson(any(), any(), any())).thenReturn(ok(List.of()));
    when(actors.forgetUnlisted(NOW.minus(Duration.ofDays(3)))).thenReturn(4);

    List<DirectorySync.Outcome> outcomes = sync(100).run();

    assertThat(outcomes).extracting(DirectorySync.Outcome::host).containsExactly("due.example");
    verify(actors).forgetUnlisted(NOW.minus(Duration.ofDays(3)));
  }

  @Test
  void eachSourceIsRecordedAsItEndsAndOneThatBreaksDoesNotStopTheRest() {
    FederationDirectorySourceEntity first =
        new FederationDirectorySourceEntity("first.example", true, 80);
    FederationDirectorySourceEntity broken =
        new FederationDirectorySourceEntity("broken.example", true, 80);
    FederationDirectorySourceEntity last =
        new FederationDirectorySourceEntity("last.example", true, 80);
    when(sources.list()).thenReturn(List.of(first, broken, last));
    when(sources.find("first.example")).thenReturn(Optional.of(first));
    when(sources.find("broken.example")).thenThrow(new IllegalStateException("lost connection"));
    when(sources.find("last.example")).thenReturn(Optional.of(last));
    when(http.getJson(any(), any(), any())).thenReturn(ok(List.of()));
    List<String> saved = new ArrayList<>();
    when(sources.save(any()))
        .thenAnswer(
            call -> {
              saved.add(call.<FederationDirectorySourceEntity>getArgument(0).getHost());
              return call.getArgument(0);
            });

    List<DirectorySync.Outcome> outcomes = sync(100).run();

    assertThat(outcomes)
        .extracting(DirectorySync.Outcome::host)
        .containsExactly("first.example", "last.example");
    assertThat(saved).containsExactly("first.example", "last.example");
    assertThat(first.getLastSyncedAt()).isEqualTo(NOW);
    assertThat(last.getLastSyncedAt()).isEqualTo(NOW);
    verify(actors).forgetUnlisted(NOW.minus(Duration.ofDays(3)));
  }

  private static FederationDirectorySourceEntity row(
      String host, boolean enabled, Instant lastSyncedAt, Instant retryAfter) {
    FederationDirectorySourceEntity row = new FederationDirectorySourceEntity(host, enabled, 100);
    ReflectionTestUtils.setField(row, "lastSyncedAt", lastSyncedAt);
    ReflectionTestUtils.setField(row, "retryAfter", retryAfter);
    return row;
  }

  @Test
  void aSourceNeverReadOrNotReadSinceTheGivenTimeIsUnreadUnlessItIsOffOrWaitingOutAFailure() {
    Instant since = NOW.minus(Duration.ofMinutes(30));
    FederationDirectorySourceEntity read = row("read.example", true, since.plusSeconds(1), null);
    DirectorySync sync = sync(100);

    when(sources.list()).thenReturn(List.of(read));
    assertThat(sync.unreadSince(since)).isFalse();

    when(sources.list()).thenReturn(List.of(read, row("new.example", true, null, null)));
    assertThat(sync.unreadSince(since)).isTrue();

    when(sources.list())
        .thenReturn(List.of(read, row("cut.example", true, since.minusSeconds(1), null)));
    assertThat(sync.unreadSince(since)).isTrue();

    when(sources.list()).thenReturn(List.of(read, row("off.example", false, null, null)));
    assertThat(sync.unreadSince(since)).isFalse();

    when(sources.list())
        .thenReturn(List.of(read, row("waits.example", true, null, NOW.plusSeconds(1))));
    assertThat(sync.unreadSince(since)).isFalse();

    when(sources.list()).thenReturn(List.of(read, row("waited.example", true, null, NOW)));
    assertThat(sync.unreadSince(since)).isTrue();
  }
}
