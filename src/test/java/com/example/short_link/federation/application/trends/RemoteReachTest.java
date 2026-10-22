package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counted;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counts;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Due;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Postponed;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteReachTest {

  private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration PATIENCE = Duration.ofSeconds(5);
  private static final String HOST = "qdon.example";
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));

  @Mock private RemoteNoteReachRepository reach;
  @Mock private LatestStore store;
  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MovingClock clock = new MovingClock(NOW);

  private static final class MovingClock extends Clock {
    private Instant now;

    MovingClock(Instant now) {
      this.now = now;
    }

    void pass(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void keys() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(serverBlocks.severityFor(anyString())).thenReturn(Optional.empty());
  }

  private RemoteReach reach(boolean enabled, boolean pulling, int perDay) {
    return new RemoteReach(
        new ReachProperties(enabled, perDay, 0, null, 0, Duration.ZERO, PATIENCE),
        new LatestProperties(true, List.of(HOST), 40, 0, null, null, pulling, null, 0, 0),
        reach,
        store,
        http,
        signingKeys,
        serverBlocks,
        JSON,
        meters,
        clock);
  }

  private RemoteReach reach() {
    return reach(true, true, 0);
  }

  private static Due due(long noteId, String statusId, int checks, int failures) {
    return new Due(noteId, HOST, statusId, checks, failures);
  }

  private static String status(String id, int likes, int boosts, int replies) {
    return """
        {"id":"%s","favourites_count":%d,"reblogs_count":%d,"replies_count":%d}"""
        .formatted(id, likes, boosts, replies);
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  private static URI batch(String... ids) {
    StringBuilder query = new StringBuilder();
    for (String id : ids) {
      query.append(query.isEmpty() ? "" : "&").append("id%5B%5D=").append(id);
    }
    return URI.create("https://" + HOST + "/api/v1/statuses?" + query);
  }

  private static URI single(String id) {
    return URI.create("https://" + HOST + "/api/v1/statuses/" + id);
  }

  private void dueNow(Due... rows) {
    when(reach.due(clock.instant().minus(Duration.ofHours(6)), clock.instant(), 200))
        .thenReturn(List.of(rows));
  }

  @SuppressWarnings("unchecked")
  private List<Counted> counted() {
    ArgumentCaptor<List<Counted>> rows = ArgumentCaptor.forClass(List.class);
    verify(reach).counted(rows.capture());
    return rows.getValue();
  }

  @SuppressWarnings("unchecked")
  private List<Postponed> postponed() {
    ArgumentCaptor<List<Postponed>> rows = ArgumentCaptor.forClass(List.class);
    verify(reach).postponed(rows.capture());
    return rows.getValue();
  }

  private double result(String result) {
    return meters.counter("federation_reach", "result", result).count();
  }

  @Test
  void dueNotesAreAskedTwentyAtATimeAndOneMissingFromTheAnswerIsGoneThere() {
    dueNow(due(1, "101", 0, 0), due(2, "102", 1, 2), due(3, "103", 2, 0));
    when(http.getJson(batch("101", "102", "103"), instance, PATIENCE))
        .thenReturn(ok("[" + status("101", 3, 2, 1) + "," + status("103", 9, 0, 0) + "]"));

    assertThat(reach().round()).isEqualTo(2);

    assertThat(counted())
        .containsExactly(
            new Counted(1L, new Counts(3, 2, 1), NOW, NOW.plus(Duration.ofHours(5)), 1),
            new Counted(3L, new Counts(9, 0, 0), NOW, null, 3));
    assertThat(postponed()).containsExactly(new Postponed(2L, null, 2, NOW));
    assertThat(result("gone")).isEqualTo(1);
    assertThat(meters.counter("federation_reach_requests", "host", HOST).count()).isEqualTo(1);
  }

  @Test
  void aServerWithoutTheManyStatusesPathIsAskedOneByOneFromTheNextRound() {
    dueNow(due(1, "101", 0, 0), due(2, "102", 0, 0));
    when(http.getJson(batch("101", "102"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(404, "not found"));
    RemoteReach reach = reach();

    assertThat(reach.round()).isZero();
    verify(this.reach).counted(List.of());
    verify(this.reach).postponed(List.of());

    when(http.getJson(single("101"), instance, PATIENCE)).thenReturn(ok(status("101", 1, 0, 0)));
    when(http.getJson(single("102"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(410, "gone"));
    assertThat(reach.round()).isEqualTo(1);

    ArgumentCaptor<List<Counted>> counted = ArgumentCaptor.captor();
    verify(this.reach, times(2)).counted(counted.capture());
    assertThat(counted.getValue())
        .containsExactly(
            new Counted(1L, new Counts(1, 0, 0), NOW, NOW.plus(Duration.ofHours(5)), 1));
    ArgumentCaptor<List<Postponed>> postponed = ArgumentCaptor.captor();
    verify(this.reach, times(2)).postponed(postponed.capture());
    assertThat(postponed.getValue()).containsExactly(new Postponed(2L, null, 0, NOW));
  }

  @Test
  void aServerThatLimitsOrRefusesTheAsksRestsAnHourThenTwoAndItsNotesStayDue() {
    dueNow(due(1, "101", 0, 0));
    when(http.getJson(batch("101"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(429, "slow down"))
        .thenReturn(new FederationHttp.Result.Failed(403, "no"))
        .thenReturn(ok("[" + status("101", 1, 1, 1) + "]"));
    RemoteReach reach = reach();

    reach.round();
    clock.pass(Duration.ofMinutes(59));
    dueNow(due(1, "101", 0, 0));
    reach.round();
    clock.pass(Duration.ofMinutes(2));
    dueNow(due(1, "101", 0, 0));
    reach.round();
    clock.pass(Duration.ofMinutes(119));
    dueNow(due(1, "101", 0, 0));
    reach.round();
    clock.pass(Duration.ofMinutes(2));
    dueNow(due(1, "101", 0, 0));
    assertThat(reach.round()).isEqualTo(1);

    verify(http, times(3)).getJson(batch("101"), instance, PATIENCE);
    assertThat(result("limited")).isEqualTo(2);
  }

  @Test
  void aServerThatFailsToAnswerPutsItsNotesOffLongerEachTimeAndStopsAskingTheRest() {
    dueNow(due(1, "101", 0, 0), due(2, "102", 0, 2));
    when(http.getJson(batch("101", "102"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Unreachable("timeout"));

    assertThat(reach().round()).isZero();

    assertThat(postponed())
        .containsExactly(
            new Postponed(1L, NOW.plus(Duration.ofHours(2)), 1, null),
            new Postponed(2L, null, 3, null));
    assertThat(result("failed")).isEqualTo(2);
  }

  @Test
  void anAnswerThatIsNotAListOfStatusesCountsAsAFailure() {
    dueNow(due(1, "101", 0, 0));
    when(http.getJson(batch("101"), instance, PATIENCE))
        .thenReturn(ok("{\"error\":\"nope\"}"))
        .thenReturn(ok("not json"));
    RemoteReach reach = reach();

    reach.round();
    reach.round();

    assertThat(result("failed")).isEqualTo(2);
    assertThat(result("gone")).isZero();
  }

  @Test
  void theDaysAsksStopAtTheCapAndTheRestWaitForTomorrow() {
    Due[] rows = new Due[45];
    for (int i = 0; i < rows.length; i++) {
      rows[i] = due(i, String.valueOf(1_000 + i), 0, 0);
    }
    dueNow(rows);
    when(http.getJson(any(), eq(instance), eq(PATIENCE))).thenReturn(ok("[]"));
    RemoteReach reach = reach(true, true, 2);

    reach.round();

    verify(http, times(2)).getJson(any(), eq(instance), eq(PATIENCE));
    assertThat(result("capped")).isEqualTo(5);
    clock.pass(Duration.ofDays(1));
    dueNow(rows);
    reach.round();
    verify(http, times(4)).getJson(any(), eq(instance), eq(PATIENCE));
  }

  @Test
  void aBlockedServerIsNeverAskedAndItsNotesAreAskedNoMore() {
    dueNow(due(1, "101", 1, 0));
    when(serverBlocks.severityFor(HOST)).thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));

    reach().round();

    assertThat(postponed()).containsExactly(new Postponed(1L, null, 0, null));
    verifyNoInteractions(http);
  }

  @Test
  void nothingIsAskedWhereReachOrPullingIsOff() {
    assertThat(reach(false, true, 0).round()).isZero();
    assertThat(reach(true, false, 0).round()).isZero();

    verifyNoInteractions(reach, http);
  }

  private static TrendingStatusReader.Read trended(String uri, int likes) {
    var status = TrendingStatusReaderTest.status(uri, "alice@" + URI.create(uri).getHost());
    status.put("favourites_count", likes);
    return new TrendingStatusReader(new RemoteNoteParser(URLS), JSON)
        .read(status, "trends.example")
        .orElseThrow();
  }

  @Test
  void aTrendingListRaisesKeptCountsAndKeepsWhatMayBeKeptWherePullingIsOn() {
    String kept = "https://a.example/users/alice/statuses/1";
    String fresh = "https://a.example/users/alice/statuses/2";
    String marked = "https://a.example/users/alice/statuses/3";
    TrendingStatusReader.Read keptRead = trended(kept, 40);
    TrendingStatusReader.Read freshRead = trended(fresh, 5);
    when(reach.idsByUri(Set.of(kept, fresh, marked))).thenReturn(Map.of(kept, 11L));

    reach().trended(List.of(keptRead, freshRead, trended(marked, 1)), Set.of(kept, fresh));

    verify(store).keepTrended("trends.example", freshRead);
    verify(store, times(1)).keepTrended(anyString(), any());
    verify(reach).trended(Map.of(11L, new Counts(40, 2, 1)), NOW);
  }

  @Test
  void whereNothingIsPulledATrendingListOnlyRaisesCountsAndAnEmptyOneAsksNothing() {
    String kept = "https://a.example/users/alice/statuses/1";
    when(reach.idsByUri(Set.of(kept))).thenReturn(Map.of("https://A.example/x", 11L));

    reach(true, false, 0).trended(List.of(trended(kept, 4)), Set.of(kept));
    reach().trended(List.of(), Set.of());

    verify(store, never()).keepTrended(anyString(), any());
    verify(reach).trended(Map.of(), NOW);
    verify(reach, times(1)).idsByUri(any());
    verify(reach, never()).due(any(), any(), anyInt());
  }
}
