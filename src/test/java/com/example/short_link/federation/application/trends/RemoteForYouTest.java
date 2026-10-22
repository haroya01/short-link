package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import com.example.short_link.federation.domain.repository.ForYouSignalReader.Kind;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counts;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Reach;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class RemoteForYouTest {

  private static final Instant NOW = Instant.parse("2026-10-22T03:00:00.123456Z");
  private static final Instant AS_OF = Instant.parse("2026-10-22T03:00:00.123Z");
  private static final SeenViewer MEMBER = SeenViewer.member(7L);
  private static final SeenViewer DEVICE =
      SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));

  @Mock private LatestPosts latest;
  @Mock private TrendingPosts trending;
  @Mock private TrendingAuthors authors;
  @Mock private NoteViewReader notes;
  @Mock private RemoteNoteReachRepository reach;
  @Mock private ForYouSignalReader signals;
  @Mock private RemoteSeen seen;

  private RemoteForYou forYou(boolean enabled, boolean pulling) {
    return new RemoteForYou(
        new LatestProperties(enabled, List.of(), 40, 0, null, null, pulling, null, 0, 0),
        latest,
        trending,
        authors,
        notes,
        reach,
        signals,
        seen,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static String uri(long id) {
    return "https://h" + id % 3 + ".example/users/a" + id + "/statuses/" + id;
  }

  private static NoteViewReader.Pooled pooled(long id, String language, String... tags) {
    return new NoteViewReader.Pooled(
        id,
        100 + id,
        "h" + id % 3 + ".example",
        language,
        NOW.minus(Duration.ofHours(1)),
        uri(id),
        List.of(tags));
  }

  private void hydrates() {
    when(notes.listed(eq(7L), any()))
        .thenAnswer(
            call -> {
              List<Long> ids = call.getArgument(1);
              return ids.stream()
                  .filter(id -> id != 99L)
                  .map(id -> new NoteViewReader.Listed(id, "view" + id, uri(id)))
                  .toList();
            });
  }

  private static List<Object> views(RemoteTrendingFeed.Page page) {
    return page.items().stream().map(item -> ((RemoteTrendingFeed.KeptNote) item).note()).toList();
  }

  @Test
  void aMembersFeedIsRankedOnceFromTheKeptNotesAndPagedFromThatRanking() {
    when(signals.signals(MEMBER, AS_OF.minus(Duration.ofDays(30)), 40, 8_000))
        .thenReturn(
            List.of(
                new ForYouSignalReader.Signal(Kind.LIKED, "r201", "ko", List.of("java")),
                new ForYouSignalReader.Signal(Kind.DWELT, "r999", "ko", List.of())));
    when(signals.locale(7L)).thenReturn(Optional.of("ja"));
    when(notes.remotePool(7L, AS_OF.minus(Duration.ofHours(72)), AS_OF, 1_000))
        .thenReturn(
            List.of(
                pooled(1, "ja"),
                pooled(2, "en"),
                pooled(3, "ja", "java"),
                pooled(4, "ja"),
                pooled(99, "ja")));
    when(reach.of(List.of(1L, 2L, 3L, 4L, 99L)))
        .thenReturn(
            Map.of(
                2L, new Reach(2L, new Counts(500, 100, 0), false),
                4L, new Reach(4L, new Counts(900, 0, 0), true)));
    when(seen.seenBefore(MEMBER, List.of(uri(1), uri(2), uri(3), uri(99)), AS_OF))
        .thenReturn(Set.of(uri(1)));
    hydrates();
    RemoteForYou forYou = forYou(true, true);

    RemoteTrendingFeed.Page first = forYou.page(7L, MEMBER, "en", null, 2);

    assertThat(views(first)).containsExactly("view3", "view2");
    assertThat(first.next()).isEqualTo("f" + AS_OF.toEpochMilli() + ".2");
    assertThat(first.seenFrom()).isNull();
    assertThat(first.gap()).isNull();

    RemoteTrendingFeed.Page second = forYou.page(7L, MEMBER, "en", first.next(), 2);

    assertThat(views(second)).containsExactly("view1");
    assertThat(second.seenFrom()).isZero();
    assertThat(second.next()).isNull();
    verify(notes, times(1)).remotePool(any(), any(), any(), anyInt());
    verify(signals, times(1)).locale(7L);
  }

  @Test
  void theLineBeforeWhatWasSeenFallsWhereTheFirstSeenNoteIsShown() {
    when(signals.signals(eq(MEMBER), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(signals.locale(7L)).thenReturn(Optional.empty());
    when(notes.remotePool(eq(7L), any(), any(), anyInt()))
        .thenReturn(List.of(pooled(1, "ko"), pooled(2, "ko"), pooled(3, "ko")));
    when(reach.of(any())).thenReturn(Map.of());
    when(seen.seenBefore(eq(MEMBER), any(), eq(AS_OF))).thenReturn(Set.of(uri(1)));
    hydrates();

    RemoteTrendingFeed.Page page = forYou(true, true).page(7L, MEMBER, null, "nonsense", 20);

    assertThat(views(page)).containsExactly("view3", "view2", "view1");
    assertThat(page.seenFrom()).isEqualTo(2);
    assertThat(page.next()).isNull();
  }

  @Test
  void aCursorThatIsNotOursStartsOverAndAFarOffsetIsAnEmptyEnd() {
    when(signals.signals(eq(MEMBER), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(signals.locale(7L)).thenReturn(Optional.empty());
    when(notes.remotePool(eq(7L), any(), any(), anyInt())).thenReturn(List.of(pooled(1, "ko")));
    when(reach.of(any())).thenReturn(Map.of());
    when(seen.seenBefore(any(), any(), any())).thenReturn(Set.of());
    hydrates();
    RemoteForYou forYou = forYou(true, true);

    for (String cursor : List.of("f", "fx.1", "f1.x", "f-1.0", "f1.-2", "r12", "u1.t2")) {
      assertThat(views(forYou.page(7L, MEMBER, null, cursor, 20))).containsExactly("view1");
    }
    RemoteTrendingFeed.Page past =
        forYou.page(7L, MEMBER, null, "f" + AS_OF.toEpochMilli() + ".9", 20);
    assertThat(past.items()).isEmpty();
    assertThat(past.next()).isNull();
    verify(notes, times(1)).remotePool(any(), any(), any(), anyInt());
  }

  private static TrendingStatusReader.Read read(
      String uri, int likes, Instant at, String language, String extra) {
    String host = uri.substring("https://".length(), uri.indexOf('/', "https://".length()));
    String name = uri.substring(uri.indexOf("/users/") + 7, uri.indexOf("/statuses/"));
    ObjectNode status = TrendingStatusReaderTest.status(uri, name);
    status.put("favourites_count", likes);
    status.put("created_at", at.toString());
    status.put("language", language);
    status.put("content", "<p>post " + extra + "</p>");
    if (extra.equals("warned")) {
      status.put("spoiler_text", "spoiler");
    }
    if (extra.equals("sensitive")) {
      status.put("sensitive", true);
    }
    return new TrendingStatusReader(new RemoteNoteParser(URLS), JSON)
        .read(status, host)
        .orElseThrow();
  }

  private static TrendingStatusReader.Read known(TrendingStatusReader.Read read, long remoteId) {
    return new TrendingStatusReader.Read(
        read.note().by(read.note().author().known(remoteId)),
        read.actorUri(),
        read.domain(),
        read.object());
  }

  private static List<String> uris(RemoteTrendingFeed.Page page) {
    return page.items().stream().map(item -> ((TrendingNote) item).uri()).toList();
  }

  @Test
  void whereNothingIsPulledTheLatestAndTrendingHeldInMemoryAreRankedTheSameWay() {
    Instant fresh = NOW.minus(Duration.ofHours(2));
    TrendingStatusReader.Read trendingKo =
        read("https://a.example/users/ko/statuses/1", 300, fresh, "ko", "");
    TrendingStatusReader.Read latestJa =
        read("https://b.example/users/ja/statuses/2", 0, fresh, "ja", "");
    TrendingStatusReader.Read latestKo =
        read("https://c.example/users/kq/statuses/3", 0, fresh, "ko", "#自転車");
    TrendingStatusReader.Read old =
        read(
            "https://c.example/users/old/statuses/4", 999, NOW.minus(Duration.ofDays(4)), "ko", "");
    TrendingStatusReader.Read warned =
        read("https://c.example/users/w/statuses/5", 999, fresh, "ko", "warned");
    TrendingStatusReader.Read sensitive =
        read("https://c.example/users/s/statuses/6", 999, fresh, "ko", "sensitive");
    TrendingStatusReader.Read future =
        read("https://c.example/users/f/statuses/7", 999, NOW.plusSeconds(60), "ko", "");
    when(trending.pool(null)).thenReturn(List.of(trendingKo, old, warned, sensitive, future));
    when(latest.pool(null))
        .thenReturn(
            List.of(
                read("https://a.example/users/ko/statuses/1", 0, fresh, "ko", ""),
                latestJa,
                latestKo));

    RemoteTrendingFeed.Page page = forYou(true, false).page(null, null, "ko-KR,ko;q=0.9", null, 20);

    assertThat(uris(page))
        .containsExactly(
            "https://a.example/users/ko/statuses/1",
            "https://c.example/users/kq/statuses/3",
            "https://b.example/users/ja/statuses/2");
    assertThat(((TrendingNote) page.items().getFirst()).likeCount()).isEqualTo(300);
    verifyNoInteractions(signals, seen, notes, reach, authors);
  }

  @Test
  void aMemberInMemoryNeverSeesAnAccountTheyFollowAndUnknownAuthorsAreFetchedForNext() {
    Instant fresh = NOW.minus(Duration.ofHours(2));
    TrendingStatusReader.Read followed =
        known(read("https://a.example/users/f/statuses/1", 50, fresh, "ko", ""), 41L);
    TrendingStatusReader.Read stranger =
        read("https://b.example/users/s/statuses/2", 0, fresh, "ko", "");
    TrendingStatusReader.Read knownStranger =
        known(read("https://b.example/users/k/statuses/3", 0, fresh, "ko", ""), 42L);
    when(trending.pool(7L)).thenReturn(List.of(followed));
    when(latest.pool(7L)).thenReturn(List.of(stranger, knownStranger));
    when(signals.followed(7L)).thenReturn(Set.of(41L));
    when(signals.signals(eq(MEMBER), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(signals.locale(7L)).thenReturn(Optional.of("ko"));
    when(seen.seenBefore(eq(MEMBER), any(), any())).thenReturn(Set.of());

    RemoteTrendingFeed.Page page = forYou(true, false).page(7L, MEMBER, null, null, 20);

    assertThat(uris(page))
        .containsExactly(
            "https://b.example/users/k/statuses/3", "https://b.example/users/s/statuses/2");
    verify(authors).warm(List.of("https://b.example/users/s"));
  }

  @Test
  void aSignedOutDevicesInterestComesFromWhatItStayedOnAndItsLanguageFromTheBrowser() {
    Instant fresh = NOW.minus(Duration.ofHours(2));
    TrendingStatusReader.Read cycling =
        read("https://a.example/users/c/statuses/1", 0, fresh, "ja", "#自転車");
    TrendingStatusReader.Read other =
        read("https://b.example/users/o/statuses/2", 5, fresh, "ja", "");
    when(trending.pool(null)).thenReturn(List.of());
    when(latest.pool(null)).thenReturn(List.of(other, cycling));
    when(signals.signals(eq(DEVICE), any(), anyInt(), anyInt()))
        .thenReturn(List.of(new ForYouSignalReader.Signal(Kind.DWELT, "r1", "ja", List.of("自転車"))));
    when(seen.seenBefore(eq(DEVICE), any(), any())).thenReturn(Set.of());

    RemoteTrendingFeed.Page page = forYou(true, false).page(null, DEVICE, "ja", null, 20);

    assertThat(uris(page))
        .containsExactly(
            "https://a.example/users/c/statuses/1", "https://b.example/users/o/statuses/2");
    verify(signals, never()).locale(any(Long.class));
    verify(signals, never()).followed(any(Long.class));
  }

  @Test
  void anEmptyPoolAsksNothingOfWhatWasSeenAndAnOffFeedIsEmpty() {
    when(signals.signals(eq(DEVICE), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(notes.remotePool(eq(null), any(), any(), anyInt())).thenReturn(List.of());
    when(reach.of(List.of())).thenReturn(Map.of());

    RemoteTrendingFeed.Page page = forYou(true, true).page(null, DEVICE, null, null, 20);

    assertThat(page.items()).isEmpty();
    assertThat(page.next()).isNull();
    assertThat(forYou(false, true).page(7L, MEMBER, null, null, 20).items()).isEmpty();
    verifyNoInteractions(seen);
    verify(notes, never()).listed(any(), any());
  }

  @Test
  void eachViewerAndEachAsOfHasItsOwnRanking() {
    Map<Long, Integer> asked = new HashMap<>();
    when(signals.signals(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(signals.locale(any(Long.class))).thenReturn(Optional.empty());
    when(notes.remotePool(any(), any(), any(), anyInt()))
        .thenAnswer(
            call -> {
              Long viewer = call.getArgument(0);
              asked.merge(viewer == null ? 0L : viewer, 1, Integer::sum);
              return new ArrayList<NoteViewReader.Pooled>();
            });
    when(reach.of(any())).thenReturn(Map.of());
    RemoteForYou forYou = forYou(true, true);

    forYou.page(7L, MEMBER, null, null, 20);
    forYou.page(8L, SeenViewer.member(8L), null, null, 20);
    forYou.page(null, null, "ko", null, 20);
    forYou.page(null, null, "ko", null, 20);
    forYou.page(null, null, "ja", null, 20);
    forYou.page(7L, MEMBER, null, "f1.0", 20);

    assertThat(asked).containsEntry(7L, 2).containsEntry(8L, 1).containsEntry(0L, 2);
  }
}
