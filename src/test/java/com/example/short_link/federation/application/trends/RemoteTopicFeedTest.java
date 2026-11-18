package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.trends.TopicFixtures.read;
import static com.example.short_link.federation.application.trends.TopicFixtures.uri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicArticle;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteTopicFeedTest {

  private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");
  private static final NoteTopicReader.Carrying HARBOR =
      new NoteTopicReader.Carrying("harbor", null);
  private static final Topic TOPIC =
      new Topic(
          "Harbor",
          TopicArticle.NONE,
          "en",
          12,
          0,
          List.of("a.example", "kurl.me"),
          List.of(new TopicArticle("https://news.example/a", "Harbor lights", "Example", null)));

  @Mock private TrendingTopics topics;
  @Mock private TopicTimelines timelines;
  @Mock private NoteTopicReader keptNotes;
  @Mock private NoteViewReader views;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private TrendingAuthors authors;
  @Mock private RemoteForYou forYou;

  private RemoteTopicFeed feed;

  @BeforeEach
  void feed() {
    lenient().when(topics.held()).thenReturn(new HeldTopics(List.of(TOPIC)));
    lenient().when(topics.elsewhere(TOPIC)).thenReturn(List.of("a.example"));
    lenient().when(forYou.ownLanguages(any(), any(), any())).thenReturn(Set.of());
    lenient()
        .when(views.listed(any(), any()))
        .thenAnswer(
            call ->
                call.<List<Long>>getArgument(1).stream()
                    .map(id -> new NoteViewReader.Listed(id, "note " + id, keptUri(id)))
                    .toList());
    feed =
        new RemoteTopicFeed(
            topics,
            timelines,
            keptNotes,
            views,
            standings,
            authors,
            forYou,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static String keptUri(long id) {
    return "https://kept.example/users/kim/statuses/" + id;
  }

  private static NoteTopicReader.Kept kept(long id, int minutesAgo) {
    return new NoteTopicReader.Kept(id, keptUri(id), NOW.minusSeconds(minutesAgo * 60L));
  }

  private static List<NoteTopicReader.Kept> keptDown(long fromId, int count) {
    List<NoteTopicReader.Kept> rows = new ArrayList<>();
    for (int n = 0; n < count; n++) {
      rows.add(kept(fromId - n, 100 + n));
    }
    return rows;
  }

  private static TrendingStatusReader.Read remote(String user, int id, int minutesAgo) {
    return read(uri("a.example", user, id), NOW.minusSeconds(minutesAgo * 60L));
  }

  private static TopicAsk harbor() {
    return TopicAsk.of("harbor", null);
  }

  private static List<String> shown(RemoteTopicFeed.TopicPosts page) {
    return page.items().stream()
        .map(
            item ->
                item instanceof RemoteTrendingFeed.KeptNote note
                    ? String.valueOf(note.note())
                    : ((TrendingNote) item).uri())
        .toList();
  }

  @Test
  void aReaderGetsTheirOwnLanguagesFirstAndEachTopicSaysWhetherItIsTheirs() {
    Topic picnic = TopicFixtures.tag("소풍", "b.example").in("ko");
    when(topics.held()).thenReturn(new HeldTopics(List.of(TOPIC, picnic)));
    when(forYou.ownLanguages(7L, "ko-KR,ko;q=0.9", NOW)).thenReturn(Set.of("ko"));

    RemoteTopicFeed.TopicList list = feed.topics(7L, "ko-KR,ko;q=0.9", 20);

    assertThat(list.items()).extracting(TopicView::id).containsExactly("tag:소풍", "tag:harbor");
    assertThat(list.items()).extracting(TopicView::own).containsExactly(true, false);
    TopicView harbor = list.items().get(1);
    assertThat(harbor.kind()).isEqualTo("tag");
    assertThat(harbor.tag()).isEqualTo("Harbor");
    assertThat(harbor.people()).isEqualTo(12);
    assertThat(harbor.servers()).containsExactly("a.example", "kurl.me");
    assertThat(harbor.related()).hasSize(1);
    assertThat(feed.topics(7L, "ko-KR,ko;q=0.9", 1).items())
        .extracting(TopicView::id)
        .containsExactly("tag:소풍");
  }

  @Test
  void withNoTopicsNobodysLanguagesAreRead() {
    when(topics.held()).thenReturn(HeldTopics.NONE);

    assertThat(feed.topics(7L, "ko", 20).items()).isEmpty();

    verifyNoInteractions(forYou);
  }

  @Test
  void aTagThatIsNotATopicAnswersFromKeptNotesAndAsksNoOtherServer() {
    NoteTopicReader.Carrying lantern = new NoteTopicReader.Carrying("lantern", null);
    when(keptNotes.kept(lantern, 7L, null, 41)).thenReturn(List.of(kept(12, 1), kept(11, 2)));

    RemoteTopicFeed.TopicPosts page = feed.posts(7L, "ko", TopicAsk.of("#lantern", " "), null, 20);

    assertThat(page.topic()).isNull();
    assertThat(shown(page)).containsExactly("note 12", "note 11");
    assertThat(page.next()).isNull();
    verifyNoInteractions(timelines, standings, authors, forYou);
  }

  @Test
  void aTopicsKeptNotesAndItsPostsElsewhereMakeOneListNewestFirst() {
    when(keptNotes.kept(HARBOR, null, null, 41)).thenReturn(List.of(kept(12, 10), kept(11, 30)));
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("old", 1, 40), remote("new", 2, 5), remote("mid", 3, 20)));

    RemoteTopicFeed.TopicPosts page = feed.posts(null, "en", harbor(), null, 20);

    assertThat(shown(page))
        .containsExactly(
            uri("a.example", "new", 2),
            "note 12",
            uri("a.example", "mid", 3),
            "note 11",
            uri("a.example", "old", 1));
    assertThat(page.next()).isNull();
    assertThat(page.topic().id()).isEqualTo("tag:harbor");
    assertThat(page.topic().related()).hasSize(1);
    assertThat(((RemoteTrendingFeed.KeptNote) page.items().get(1)).uri()).isEqualTo(keptUri(12));
    verify(keptNotes, never()).keptAmong(any(), any(), any());
    verifyNoInteractions(standings, authors);
  }

  @Test
  void theTopicSaysWhetherItIsInTheReadersLanguageOnEveryPage() {
    when(forYou.ownLanguages(7L, "en", NOW)).thenReturn(Set.of("en"));
    when(keptNotes.kept(eq(HARBOR), eq(7L), any(), anyInt())).thenReturn(List.of());

    assertThat(feed.posts(7L, "en", harbor(), null, 20).topic().own()).isTrue();
    assertThat(feed.posts(7L, "en", harbor(), "r90", 20).topic().own()).isTrue();
  }

  @Test
  void aPostThisServerKeepsIsShownOnceAsTheKeptNote() {
    NoteTopicReader.Kept same =
        new NoteTopicReader.Kept(12L, uri("a.example", "ann", 1), NOW.minusSeconds(60));
    when(keptNotes.kept(HARBOR, null, null, 41)).thenReturn(List.of(same));
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("ann", 1, 1), remote("bo", 2, 2)));

    RemoteTopicFeed.TopicPosts page = feed.posts(null, null, harbor(), null, 20);

    assertThat(shown(page)).containsExactly("note 12", uri("a.example", "bo", 2));
  }

  @Test
  void theWindowPagesByOffsetAndThenOlderKeptNotesByIdToTheEnd() {
    List<NoteTopicReader.Kept> newest = keptDown(141, 41);
    when(keptNotes.kept(HARBOR, null, null, 41)).thenReturn(newest);
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("new", 1, 1), remote("old", 2, 900)));

    RemoteTopicFeed.TopicPosts first = feed.posts(null, null, harbor(), null, 40);
    RemoteTopicFeed.TopicPosts second = feed.posts(null, null, harbor(), first.next(), 40);

    assertThat(first.items()).hasSize(40);
    assertThat(shown(first).getFirst()).isEqualTo(uri("a.example", "new", 1));
    assertThat(first.next()).isEqualTo("t40");
    assertThat(shown(second)).containsExactly("note 102", uri("a.example", "old", 2));
    assertThat(second.next()).isEqualTo("r102");

    when(keptNotes.kept(HARBOR, null, 102L, 3)).thenReturn(keptDown(101, 3));
    when(keptNotes.kept(HARBOR, null, 100L, 3)).thenReturn(keptDown(99, 2));
    RemoteTopicFeed.TopicPosts third = feed.posts(null, null, harbor(), second.next(), 2);
    RemoteTopicFeed.TopicPosts last = feed.posts(null, null, harbor(), third.next(), 2);

    assertThat(shown(third)).containsExactly("note 101", "note 100");
    assertThat(third.next()).isEqualTo("r100");
    assertThat(third.topic().id()).isEqualTo("tag:harbor");
    assertThat(shown(last)).containsExactly("note 99", "note 98");
    assertThat(last.next()).isNull();
    verify(timelines, times(1)).posts(any(), any());
  }

  @Test
  void aPostKeptBelowTheWindowIsLeftForTheOlderPages() {
    when(keptNotes.kept(HARBOR, 7L, null, 41)).thenReturn(keptDown(141, 41));
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("ann", 1, 1), remote("bo", 2, 2)));
    when(keptNotes.keptAmong(
            HARBOR, 7L, List.of(uri("a.example", "ann", 1), uri("a.example", "bo", 2))))
        .thenReturn(Set.of(uri("a.example", "bo", 2)));
    when(standings.hidden(eq(7L), any(), any(), eq(NOW)))
        .thenReturn(RemoteAccountStandingReader.Hidden.NONE);

    RemoteTopicFeed.TopicPosts page = feed.posts(7L, null, harbor(), null, 40);

    assertThat(shown(page)).contains(uri("a.example", "ann", 1));
    assertThat(shown(page)).doesNotContain(uri("a.example", "bo", 2));
    assertThat(page.next()).isEqualTo("t40");
  }

  @Test
  void aMemberDoesNotSeePostsOfAccountsOrServersTheyHid() {
    TrendingStatusReader.Read elsewhere =
        read(uri("hidden.example", "eve", 3), NOW.minusSeconds(180));
    when(keptNotes.kept(HARBOR, 7L, null, 41)).thenReturn(List.of());
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("ann", 1, 1), remote("mal", 2, 2), elsewhere));
    when(standings.hidden(
            7L,
            List.of(
                "https://a.example/users/ann",
                "https://a.example/users/mal",
                "https://hidden.example/users/eve"),
            List.of("a.example", "hidden.example"),
            NOW))
        .thenReturn(
            new RemoteAccountStandingReader.Hidden(
                Set.of("https://a.example/users/mal"), Set.of("hidden.example")));

    assertThat(shown(feed.posts(7L, null, harbor(), null, 20)))
        .containsExactly(uri("a.example", "ann", 1));
    assertThat(shown(feed.posts(null, null, harbor(), null, 20))).hasSize(3);
  }

  @Test
  void aMembersPageWarmsTheAuthorsOnItThisServerHasNotMet() {
    TrendingStatusReader.Read met = remote("met", 3, 3);
    TrendingStatusReader.Read known =
        new TrendingStatusReader.Read(
            met.note().by(met.note().author().known(55L)),
            met.actorUri(),
            met.domain(),
            met.object());
    when(keptNotes.kept(eq(HARBOR), any(), isNull(), eq(41))).thenReturn(List.of());
    when(timelines.posts(TOPIC, List.of("a.example")))
        .thenReturn(List.of(remote("ann", 1, 1), remote("ann", 2, 2), known, remote("bo", 4, 4)));
    when(standings.hidden(eq(7L), any(), any(), eq(NOW)))
        .thenReturn(RemoteAccountStandingReader.Hidden.NONE);

    feed.posts(null, null, harbor(), null, 3);
    verifyNoInteractions(authors);

    feed.posts(7L, null, harbor(), null, 3);
    verify(authors).warm(List.of("https://a.example/users/ann"));
    feed.posts(7L, null, harbor(), "t3", 3);
    verify(authors).warm(List.of("https://a.example/users/bo"));
  }

  @Test
  void aPageHoldsBetweenOneAndFortyAndAnUnreadableCursorStartsOver() {
    List<TrendingStatusReader.Read> many = new ArrayList<>();
    for (int n = 1; n <= 50; n++) {
      many.add(remote("u" + n, n, n));
    }
    when(keptNotes.kept(HARBOR, null, null, 41)).thenReturn(List.of());
    when(timelines.posts(TOPIC, List.of("a.example"))).thenReturn(many);

    assertThat(feed.posts(null, null, harbor(), null, 500).items()).hasSize(40);
    assertThat(feed.posts(null, null, harbor(), null, 0).items()).hasSize(1);
    assertThat(shown(feed.posts(null, null, harbor(), "tabc", 1)))
        .containsExactly(uri("a.example", "u1", 1));
    assertThat(feed.posts(null, null, harbor(), "t49", 20).next()).isNull();
    assertThat(feed.posts(null, null, harbor(), "t900", 20).items()).isEmpty();
  }

  @Test
  void aKeptNoteThatWentAwayBetweenTheListAndItsViewIsLeftOut() {
    when(keptNotes.kept(HARBOR, null, null, 41)).thenReturn(List.of(kept(12, 1), kept(11, 2)));
    when(views.listed(null, List.of(12L, 11L)))
        .thenReturn(List.of(new NoteViewReader.Listed(11L, "note 11", keptUri(11))));
    when(timelines.posts(TOPIC, List.of("a.example"))).thenReturn(List.of());

    assertThat(shown(feed.posts(null, null, harbor(), null, 20))).containsExactly("note 11");
  }

  @Test
  void aLinkIsAskedByItsAddress() {
    Topic article = TopicFixtures.link("https://news.example/a", "a.example");
    when(topics.held()).thenReturn(new HeldTopics(List.of(article)));
    when(topics.elsewhere(article)).thenReturn(List.of("a.example"));
    when(keptNotes.kept(
            new NoteTopicReader.Carrying(null, "https://news.example/a"), null, null, 41))
        .thenReturn(List.of(kept(12, 1)));
    when(timelines.posts(article, List.of("a.example"))).thenReturn(List.of(remote("ann", 1, 9)));

    RemoteTopicFeed.TopicPosts page =
        feed.posts(null, null, TopicAsk.of(null, " https://news.example/a "), null, 20);

    assertThat(page.topic().kind()).isEqualTo("link");
    assertThat(page.topic().id()).isEqualTo("link:https://news.example/a");
    assertThat(page.topic().tag()).isNull();
    assertThat(shown(page)).containsExactly("note 12", uri("a.example", "ann", 1));
  }

  @Test
  void aTopicIsAskedByExactlyOneOfATagOrAnAddress() {
    for (String[] asked :
        new String[][] {
          {null, null}, {" ", ""}, {"#", null}, {"harbor", "https://news.example/a"}
        }) {
      assertThatThrownBy(() -> TopicAsk.of(asked[0], asked[1]))
          .isInstanceOfSatisfying(
              FederationException.class,
              refused ->
                  assertThat(refused.errorCode()).isEqualTo(FederationErrorCode.TOPIC_ASK_INVALID));
    }
    assertThat(TopicAsk.of(" #Harbor ", null)).isEqualTo(new TopicAsk("Harbor", null));
    assertThat(TopicAsk.of("", " https://news.example/a ").url())
        .isEqualTo("https://news.example/a");
    assertThat(FederationErrorCode.TOPIC_ASK_INVALID.status().value()).isEqualTo(400);
  }
}
