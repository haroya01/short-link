package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteForYou;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import com.example.short_link.federation.domain.foryou.HomeCandidate;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.PooledNote;
import com.example.short_link.post.application.read.PostHomeSlices;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeForYouTest {

  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final SeenViewer READER = SeenViewer.member(7L);

  private final RemoteForYou remote = mock(RemoteForYou.class);
  private final NoteQueryService notes = mock(NoteQueryService.class);
  private final PostHomeSlices posts = mock(PostHomeSlices.class);
  private final NoteViewReader views = mock(NoteViewReader.class);
  private final LocalSeen localSeen = mock(LocalSeen.class);
  private final RemoteSeen remoteSeen = mock(RemoteSeen.class);
  private final HomeForYou forYou =
      new HomeForYou(
          remote, notes, posts, views, localSeen, remoteSeen, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void noRemotePoolAndNoSightings() {
    when(remote.enabled()).thenReturn(true);
    when(remote.homePool(any(), any())).thenReturn(List.of());
    when(remote.reader(any(), any(), any(), any())).thenReturn(ForYouReader.NONE);
    when(remote.follows(any())).thenReturn(Set.of());
    when(posts.pool(any(), any(), any(), anyInt())).thenReturn(List.of());
    when(posts.cards(any(), any())).thenReturn(List.of());
    when(views.listed(any(), any()))
        .thenAnswer(
            call ->
                call.<List<Long>>getArgument(1).stream()
                    .map(id -> new NoteViewReader.Listed(id, "note " + id, null))
                    .toList());
  }

  private static PooledNote note(long id, long author, Duration age) {
    return new PooledNote(id, author, "ko", NOW.minus(age), List.of(), 0, 0, 0);
  }

  private static List<Object> shown(HomeFeedQueryService.Page page) {
    return page.items().stream()
        .map(HomeFeedQueryService.Item::note)
        .map(Object.class::cast)
        .toList();
  }

  @Test
  void aMemberTheReaderFollowsIsLiftedAsTheFollowedAuthorTheSignalsName() {
    when(notes.pool(any(), any(), any(), anyInt()))
        .thenReturn(List.of(note(1, 20, Duration.ofHours(1)), note(2, 30, Duration.ofHours(3))));
    when(remote.follows(7L)).thenReturn(Set.of("u30"));

    HomeFeedQueryService.Page page = forYou.page(7L, READER, null, null, 20, true);

    assertThat(shown(page)).containsExactly("note 2", "note 1");
    verify(notes).pool(7L, NOW.minus(Duration.ofDays(7)), NOW, HomeForYou.LOCAL_POOL);
    verify(posts).pool(7L, NOW.minus(Duration.ofDays(7)), NOW, HomeForYou.LOCAL_POOL);
  }

  @Test
  void kurlIsOneServerForTheTenPerBlockCap() {
    List<PooledNote> kurl = new ArrayList<>();
    for (long id = 1; id <= 12; id++) {
      kurl.add(new PooledNote(id, 100 + id, "ko", NOW.minusSeconds(id), List.of(), 50, 0, 0));
    }
    List<HomeCandidate> elsewhere = new ArrayList<>();
    for (long id = 1; id <= 10; id++) {
      elsewhere.add(
          HomeCandidate.remote(
              new ForYouCandidate(
                  500 + id,
                  "https://h" + id + ".example/s/" + id,
                  "r" + id,
                  "h" + id + ".example",
                  "ko",
                  Set.of(),
                  NOW.minus(Duration.ofHours(1)),
                  0,
                  0,
                  0,
                  500 + id),
              500 + id,
              NOW.minus(Duration.ofHours(1))));
    }
    when(notes.pool(any(), any(), any(), anyInt())).thenReturn(kurl);
    when(remote.homePool(any(), any())).thenReturn(elsewhere);

    HomeFeedQueryService.Page page = forYou.page(7L, READER, null, null, 20, true);

    assertThat(page.items()).hasSize(20);
    assertThat(page.items().stream().filter(item -> item.kind().equals("note"))).hasSize(10);
  }

  @Test
  void theScrollIsCutFromOneRankingAndItsCursorCarriesTheFirstPagesTimeAndHiding() {
    when(notes.pool(any(), any(), any(), anyInt()))
        .thenReturn(
            List.of(
                note(1, 20, Duration.ofHours(1)),
                note(2, 21, Duration.ofHours(2)),
                note(3, 22, Duration.ofHours(3))));

    HomeFeedQueryService.Page first = forYou.page(7L, READER, null, null, 2, true);
    HomeFeedQueryService.Page last = forYou.page(7L, READER, null, first.next(), 2, false);

    assertThat(shown(first)).containsExactly("note 1", "note 2");
    assertThat(first.next()).isEqualTo("rh" + NOW.toEpochMilli() + ".2");
    assertThat(shown(last)).containsExactly("note 3");
    assertThat(last.next()).isNull();
    verify(notes, times(1)).pool(any(), any(), any(), anyInt());
    verify(localSeen, times(1)).seenBefore(eq(READER), any(), eq(NOW));
  }

  @Test
  void aSignedOutReaderWithoutADeviceHidesNothingAndItsCursorSaysSo() {
    when(notes.pool(any(), any(), any(), anyInt()))
        .thenReturn(List.of(note(1, 20, Duration.ofHours(1)), note(2, 21, Duration.ofHours(2))));

    HomeFeedQueryService.Page page = forYou.page(null, null, "ko-KR,en", null, 1, true);

    assertThat(page.next()).isEqualTo("r" + NOW.toEpochMilli() + ".1");
    verifyNoInteractions(localSeen, remoteSeen);
    verify(remote).reader(null, null, List.of("ko", "en"), NOW);
  }

  @Test
  void otherServersStayOutWhenTheirPostsAreOff() {
    when(remote.enabled()).thenReturn(false);
    when(notes.pool(any(), any(), any(), anyInt())).thenReturn(List.of());

    assertThat(forYou.page(7L, READER, null, null, 20, true).items()).isEmpty();
    verify(remote, never()).homePool(any(), any());
  }

  @Test
  void aCursorThatIsNotOneStartsOver() {
    Instant now = Instant.parse("2026-10-23T12:00:00Z");

    assertThat(HomeForYou.position(null, now)).isEqualTo(new HomeForYou.Position(now, 0, null));
    assertThat(HomeForYou.position("rh123.4", now))
        .isEqualTo(new HomeForYou.Position(Instant.ofEpochMilli(123), 4, true));
    assertThat(HomeForYou.position("r123.4", now))
        .isEqualTo(new HomeForYou.Position(Instant.ofEpochMilli(123), 4, false));
    for (String bad :
        List.of("h123.1.1", "r.4", "rh.4", "rx.4", "r123.-1", "r-5.1", "r123", "4.2")) {
      assertThat(HomeForYou.position(bad, now))
          .as(bad)
          .isEqualTo(new HomeForYou.Position(now, 0, null));
    }
  }
}
