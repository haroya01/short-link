package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class LatestPostsTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration PATIENCE = Duration.ofSeconds(3);
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private LatestStore store;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private final MovingClock clock = new MovingClock(Instant.parse("2026-10-20T00:00:00Z"));

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

  private LatestPosts latest(boolean enabled, int perHost, int maxItems, String... hosts) {
    return latest(
        new LatestProperties(
            enabled, List.of(hosts), perHost, maxItems, null, PATIENCE, false, null, 0, 0));
  }

  private LatestPosts latest(LatestProperties props) {
    return new LatestPosts(
        props, http, signingKeys, serverBlocks, standings, reader(), store, URLS, JSON, clock);
  }

  private static TrendingStatusReader reader() {
    return new TrendingStatusReader(new RemoteNoteParser(URLS), JSON);
  }

  private static URI timeline(String host, int limit, String window) {
    return URI.create(
        "https://" + host + "/api/v1/timelines/public?local=true&limit=" + limit + window);
  }

  private static ObjectNode post(String host, int id, String at) {
    ObjectNode status =
        TrendingStatusReaderTest.status(
            "https://" + host + "/users/u" + id + "/statuses/" + id, "u" + id);
    status.put("id", String.valueOf(id));
    status.put("created_at", at);
    return status;
  }

  private void answers(URI uri, ObjectNode... statuses) {
    when(http.getJson(uri, instance, PATIENCE)).thenReturn(ok(statuses));
  }

  private static FederationHttp.Result ok(ObjectNode... statuses) {
    ArrayNode array = JSON.createArrayNode();
    for (ObjectNode status : statuses) {
      array.add(status);
    }
    return new FederationHttp.Result.Ok(
        200, JSON.writeValueAsString(array).getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> uris(List<TrendingNote> notes) {
    return notes.stream().map(TrendingNote::uri).toList();
  }

  @Test
  void nobodyReadingMeansNothingIsAsked() {
    latest(true, 2, 100, "a.example");
    latest(false, 2, 100, "a.example").page(7L, 0, 20);

    verifyNoInteractions(http);
  }

  @Test
  void theFirstReadAsksEachServerOnceAndLeavesOutSensitiveWarnedAndBotPosts() {
    ObjectNode warned = post("a.example", 3, "2026-10-19T23:58:00Z");
    warned.put("spoiler_text", "spoilers");
    ObjectNode sensitive = post("a.example", 4, "2026-10-19T23:57:00Z");
    sensitive.put("sensitive", true);
    ObjectNode bot = post("b.example", 5, "2026-10-19T23:56:00Z");
    ((ObjectNode) bot.path("account")).put("bot", true);
    answers(
        timeline("a.example", 4, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        warned,
        sensitive);
    answers(timeline("b.example", 4, ""), post("b.example", 8, "2026-10-19T23:59:00Z"), bot);

    LatestPosts.Page page = latest(true, 4, 100, "a.example", "b.example").page(null, 0, 20);

    assertThat(uris(page.items()))
        .containsExactly(
            "https://b.example/users/u8/statuses/8", "https://a.example/users/u9/statuses/9");
    assertThat(page.more()).isFalse();
  }

  @Test
  void aReadWithinFreshForAnswersFromMemoryAndALaterOneAsksOnlyForWhatIsNew() throws Exception {
    CountDownLatch staleRead = new CountDownLatch(1);
    answers(timeline("a.example", 2, ""), post("a.example", 9, "2026-10-19T23:50:00Z"));
    when(http.getJson(timeline("a.example", 2, "&since_id=9"), instance, PATIENCE))
        .thenAnswer(
            asked -> {
              staleRead.await(2, TimeUnit.SECONDS);
              return ok(post("a.example", 12, "2026-10-19T23:59:00Z"));
            });
    LatestPosts latest = latest(true, 2, 100, "a.example");

    latest.page(null, 0, 20);
    clock.pass(Duration.ofMinutes(4));
    latest.page(null, 0, 20);
    verify(http, times(1)).getJson(any(), any(), any());

    clock.pass(Duration.ofMinutes(2));
    assertThat(uris(latest.page(null, 0, 20).items()))
        .containsExactly("https://a.example/users/u9/statuses/9");
    staleRead.countDown();
    verify(http, timeout(2_000))
        .getJson(timeline("a.example", 2, "&since_id=9"), instance, PATIENCE);
    clock.pass(Duration.ofSeconds(1));
    assertThat(uris(pageOnceRefreshed(latest, 2)))
        .containsExactly(
            "https://a.example/users/u12/statuses/12", "https://a.example/users/u9/statuses/9");
  }

  private static List<TrendingNote> pageOnceRefreshed(LatestPosts latest, int size) {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    List<TrendingNote> items = latest.page(null, 0, 20).items();
    while (items.size() < size && System.nanoTime() < deadline) {
      Thread.onSpinWait();
      items = latest.page(null, 0, 20).items();
    }
    return items;
  }

  @Test
  void scrollingPastTheOldestAsksForThePageBeforeItUntilAShortPageEndsTheServer() {
    answers(
        timeline("a.example", 2, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        post("a.example", 8, "2026-10-19T23:49:00Z"));
    answers(timeline("a.example", 2, "&max_id=8"), post("a.example", 7, "2026-10-19T23:48:00Z"));
    LatestPosts latest = latest(true, 2, 100, "a.example");

    LatestPosts.Page first = latest.page(null, 0, 2);
    LatestPosts.Page second = latest.page(null, 2, 2);

    assertThat(first.more()).isTrue();
    assertThat(uris(second.items())).containsExactly("https://a.example/users/u7/statuses/7");
    assertThat(second.more()).isFalse();
    latest.page(null, 3, 2);
    verify(http, times(2)).getJson(any(), any(), any());
  }

  @Test
  void memoryKeepsOnlyTheNewestUpToItsCapAndABlockedServerIsNeverAsked() {
    when(serverBlocks.severityFor("b.example")).thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    answers(
        timeline("a.example", 3, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        post("a.example", 8, "2026-10-19T23:49:00Z"),
        post("a.example", 7, "2026-10-19T23:48:00Z"));
    LatestPosts latest = latest(true, 3, 2, "a.example", "b.example");

    LatestPosts.Page page = latest.page(null, 0, 20);

    assertThat(uris(page.items()))
        .containsExactly(
            "https://a.example/users/u9/statuses/9", "https://a.example/users/u8/statuses/8");
    assertThat(page.more()).isFalse();
    verify(http, never()).getJson(eq(timeline("b.example", 3, "")), any(), any());
    assertThat(latest.object("https://a.example/users/u9/statuses/9")).isPresent();
    assertThat(latest.object("https://a.example/users/u7/statuses/7")).isEmpty();
  }

  @Test
  void aServerThatRefusesVisitorsOrShowsThemNothingRestsAndIsAskedAgainLater() {
    when(http.getJson(timeline("a.example", 2, ""), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(422, "requires an authenticated user"));
    answers(timeline("b.example", 2, ""));
    LatestPosts latest = latest(true, 2, 100, "a.example", "b.example");

    latest.page(null, 0, 20);
    clock.pass(Duration.ofMinutes(10));
    latest.refresh();
    verify(http, times(1)).getJson(timeline("a.example", 2, ""), instance, PATIENCE);
    verify(http, times(1)).getJson(timeline("b.example", 2, ""), instance, PATIENCE);

    clock.pass(Duration.ofMinutes(51));
    latest.refresh();
    verify(http, times(2)).getJson(timeline("a.example", 2, ""), instance, PATIENCE);
    verify(http, times(2)).getJson(timeline("b.example", 2, ""), instance, PATIENCE);

    clock.pass(Duration.ofMinutes(61));
    latest.refresh();
    verify(http, times(2)).getJson(timeline("a.example", 2, ""), instance, PATIENCE);
  }

  @Test
  void nothingNewSinceTheLastAskIsNotARefusal() {
    answers(timeline("a.example", 2, ""), post("a.example", 9, "2026-10-19T23:50:00Z"));
    answers(timeline("a.example", 2, "&since_id=9"));
    LatestPosts latest = latest(true, 2, 100, "a.example");

    latest.page(null, 0, 20);
    clock.pass(Duration.ofMinutes(6));
    latest.refresh();
    clock.pass(Duration.ofMinutes(6));
    latest.refresh();

    verify(http, times(2)).getJson(timeline("a.example", 2, "&since_id=9"), instance, PATIENCE);
  }

  private LatestPosts pulling(int perHost, int backfillPages, String... hosts) {
    return latest(
        new LatestProperties(
            true, List.of(hosts), perHost, 0, null, PATIENCE, true, null, backfillPages, 0));
  }

  private List<String> keptInOrder(int times) {
    ArgumentCaptor<TrendingStatusReader.Read> reads =
        ArgumentCaptor.forClass(TrendingStatusReader.Read.class);
    verify(store, times(times)).keep(anyString(), reads.capture());
    return reads.getAllValues().stream().map(read -> read.note().uri()).toList();
  }

  @Test
  void pullingIsOffUnlessTurnedOnAndAsksNothing() {
    assertThat(latest(true, 2, 100, "a.example").pull()).isZero();

    verifyNoInteractions(http, store);
  }

  @Test
  void theFirstPullFillsBackPageByPageUntilItMeetsTheNewestPostKeptFromThatServer() {
    when(store.newest("a.example")).thenReturn(Optional.of(Instant.parse("2026-10-19T23:47:00Z")));
    when(store.newest("b.example")).thenReturn(Optional.empty());
    when(store.leftToday()).thenReturn(100L);
    when(store.keep(anyString(), any())).thenReturn(LatestStore.PULLED);
    answers(
        timeline("a.example", 2, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        post("a.example", 8, "2026-10-19T23:49:00Z"));
    answers(
        timeline("a.example", 2, "&max_id=8"),
        post("a.example", 7, "2026-10-19T23:48:00Z"),
        post("a.example", 6, "2026-10-19T23:47:00Z"));
    answers(
        timeline("b.example", 2, ""),
        post("b.example", 30, "2026-10-19T23:55:00Z"),
        post("b.example", 29, "2026-10-19T23:30:00Z"));
    answers(
        timeline("b.example", 2, "&max_id=29"),
        post("b.example", 28, "2026-10-19T23:20:00Z"),
        post("b.example", 27, "2026-10-19T23:10:00Z"));
    answers(
        timeline("b.example", 2, "&max_id=27"),
        post("b.example", 26, "2026-10-19T23:05:00Z"),
        post("b.example", 25, "2026-10-19T23:00:00Z"));

    assertThat(pulling(2, 3, "a.example", "b.example").pull()).isEqualTo(10);

    assertThat(keptInOrder(10))
        .containsExactly(
            "https://b.example/users/u25/statuses/25",
            "https://b.example/users/u26/statuses/26",
            "https://b.example/users/u27/statuses/27",
            "https://b.example/users/u28/statuses/28",
            "https://b.example/users/u29/statuses/29",
            "https://a.example/users/u6/statuses/6",
            "https://a.example/users/u7/statuses/7",
            "https://a.example/users/u8/statuses/8",
            "https://a.example/users/u9/statuses/9",
            "https://b.example/users/u30/statuses/30");
    verify(http, never()).getJson(eq(timeline("a.example", 2, "&max_id=6")), any(), any());
    verify(http, never()).getJson(eq(timeline("b.example", 2, "&max_id=25")), any(), any());
  }

  @Test
  void laterPullsAskOnlyForWhatCameAfterTheNewestPostSeenWhetherOrNotAnyoneReads() {
    when(store.newest("a.example")).thenReturn(Optional.empty());
    when(store.leftToday()).thenReturn(100L);
    when(store.keep(anyString(), any())).thenReturn(LatestStore.PULLED);
    answers(timeline("a.example", 2, ""), post("a.example", 9, "2026-10-19T23:50:00Z"));
    answers(
        timeline("a.example", 2, "&since_id=9"),
        post("a.example", 12, "2026-10-19T23:59:00Z"),
        post("a.example", 11, "2026-10-19T23:58:00Z"));
    answers(timeline("a.example", 2, "&since_id=12"));
    LatestPosts latest = pulling(2, 5, "a.example");

    latest.pull();
    latest.pull();
    latest.pull();

    verify(http, times(1)).getJson(eq(timeline("a.example", 2, "")), any(), any());
    verify(http, times(1)).getJson(eq(timeline("a.example", 2, "&since_id=9")), any(), any());
    verify(http, times(1)).getJson(eq(timeline("a.example", 2, "&since_id=12")), any(), any());
    verify(store, times(1)).newest("a.example");
    assertThat(keptInOrder(3))
        .containsExactly(
            "https://a.example/users/u9/statuses/9",
            "https://a.example/users/u11/statuses/11",
            "https://a.example/users/u12/statuses/12");
    assertThat(latest.page(null, 0, 20).items()).isEmpty();
  }

  @Test
  void pastTheDaysCapTheNewestAreKeptOldestFirst() {
    when(store.newest("a.example")).thenReturn(Optional.empty());
    when(store.leftToday()).thenReturn(2L);
    when(store.keep(anyString(), any())).thenReturn(LatestStore.PULLED);
    answers(
        timeline("a.example", 3, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        post("a.example", 8, "2026-10-19T23:49:00Z"),
        post("a.example", 7, "2026-10-19T23:48:00Z"));

    assertThat(pulling(3, 1, "a.example").pull()).isEqualTo(2);

    assertThat(keptInOrder(2))
        .containsExactly(
            "https://a.example/users/u8/statuses/8", "https://a.example/users/u9/statuses/9");
  }

  @Test
  void aPullKeepsNothingFromABlockedServerOrAnAccountThatAsksNotToBeFoundOrNamesAMember() {
    when(serverBlocks.severityFor("b.example")).thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    when(store.newest("a.example")).thenReturn(Optional.empty());
    when(store.leftToday()).thenReturn(100L);
    when(store.keep(anyString(), any())).thenReturn(LatestStore.PULLED);
    ObjectNode hidden = post("a.example", 8, "2026-10-19T23:49:00Z");
    ((ObjectNode) hidden.path("account")).put("discoverable", false);
    ObjectNode unindexed = post("a.example", 7, "2026-10-19T23:48:00Z");
    ((ObjectNode) unindexed.path("account")).put("noindex", true);
    ObjectNode nobot = post("a.example", 6, "2026-10-19T23:47:00Z");
    ((ObjectNode) nobot.path("account")).put("note", "<p>no crawlers <a>#NoBot</a></p>");
    ObjectNode naming = post("a.example", 5, "2026-10-19T23:46:00Z");
    naming
        .putArray("mentions")
        .addObject()
        .put("acct", "yuki@kurl.me")
        .put("url", "https://blog.kurl.me/@yuki");
    ObjectNode warned = post("a.example", 4, "2026-10-19T23:45:00Z");
    warned.put("spoiler_text", "spoilers");
    answers(
        timeline("a.example", 6, ""),
        post("a.example", 9, "2026-10-19T23:50:00Z"),
        hidden,
        unindexed,
        nobot,
        naming,
        warned);

    assertThat(pulling(6, 1, "a.example", "b.example").pull()).isEqualTo(1);

    assertThat(keptInOrder(1)).containsExactly("https://a.example/users/u9/statuses/9");
    verify(http, never()).getJson(eq(timeline("b.example", 6, "")), any(), any());
  }
}
