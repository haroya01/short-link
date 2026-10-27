package com.example.short_link.home;

import static com.example.short_link.home.application.read.HomeAskFixtures.ask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteForYou;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouProfile;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import com.example.short_link.federation.domain.foryou.HomeCandidate;
import com.example.short_link.federation.domain.foryou.HomeRanking;
import com.example.short_link.home.application.read.HomeFeedQueryService;
import com.example.short_link.home.application.read.HomeForYou;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteViewFixtures;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.example.short_link.post.application.read.PublicFeedItem;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HomeRankVectorsTest {

  static final Path VECTORS = Path.of("src/test/resources/feed-vectors/home-rank.json");

  private static JsonNode doc;

  @BeforeAll
  static void read() throws IOException {
    doc = JsonMapper.builder().build().readTree(VECTORS.toFile());
  }

  static Stream<JsonNode> cases() throws IOException {
    read();
    List<JsonNode> cases = new ArrayList<>();
    doc.get("tables").get("rank").get("cases").forEach(cases::add);
    return cases.stream();
  }

  record Case(
      Instant asOf,
      HomeRanking.Reader reader,
      SeenViewer viewer,
      List<HomeCandidate> candidates,
      Set<String> seen,
      boolean hide,
      int limit) {}

  static Case input(JsonNode node) {
    Instant asOf = Instant.parse(node.get("asOf").asString());
    JsonNode reader = node.get("reader");
    JsonNode who = reader.get("viewer");
    boolean member = who.get("kind").asString().equals("member");
    Long memberId = member ? who.get("id").asLong() : null;
    SeenViewer viewer =
        member ? SeenViewer.member(memberId) : SeenViewer.of(null, who.get("id").asString());
    Set<String> languages = new LinkedHashSet<>();
    reader
        .get("languages")
        .forEach(
            lang -> {
              String primary = ForYouProfile.language(lang.asString());
              if (primary != null) {
                languages.add(primary);
              }
            });
    Map<String, Double> tags = new HashMap<>();
    reader
        .get("tags")
        .properties()
        .forEach(
            tag ->
                tags.merge(
                    tag.getKey().toLowerCase(Locale.ROOT), tag.getValue().asDouble(), Double::sum));
    Map<String, Double> authors = new HashMap<>();
    reader
        .get("authors")
        .properties()
        .forEach(a -> authors.put(a.getKey(), a.getValue().asDouble()));
    Set<String> follows = new HashSet<>();
    reader.get("follows").forEach(f -> follows.add(f.asString()));
    List<HomeCandidate> candidates = new ArrayList<>();
    for (JsonNode c : node.get("candidates")) {
      candidates.add(candidate(c));
    }
    Set<String> seen = new HashSet<>();
    for (JsonNode s : node.get("seen")) {
      if (s.get("viewer").equals(who) && Instant.parse(s.get("at").asString()).isBefore(asOf)) {
        seen.add(s.get("key").asString());
      }
    }
    return new Case(
        asOf,
        new HomeRanking.Reader(new ForYouReader(languages, tags, authors), memberId, follows),
        viewer,
        candidates,
        seen,
        node.get("hide").asBoolean(),
        node.get("limit").asInt());
  }

  static HomeCandidate candidate(JsonNode c) {
    String key = c.get("key").asString();
    String source = c.get("source").asString();
    Set<String> tags = new HashSet<>();
    c.get("tags").forEach(t -> tags.add(t.asString()));
    String language = c.get("language").isNull() ? null : c.get("language").asString();
    Instant createdAt = Instant.parse(c.get("createdAt").asString());
    long likes = c.get("likes").asLong();
    long reposts = c.get("reposts").asLong();
    long replies = c.get("replies").asLong();
    String author = c.get("author").asString();
    if (source.equals("remote")) {
      long noteId = c.get("noteId").asLong();
      return new HomeCandidate(
          HomeCandidate.Source.REMOTE,
          new ForYouCandidate(
              noteId,
              key,
              author,
              c.get("host").asString(),
              language,
              tags,
              createdAt,
              likes,
              reposts,
              replies,
              noteId),
          noteId,
          null,
          Instant.parse(c.get("receivedAt").asString()));
    }
    long id = Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
    boolean note = source.equals("kurl-note");
    return new HomeCandidate(
        note ? HomeCandidate.Source.NOTE : HomeCandidate.Source.POST,
        new ForYouCandidate(
            id,
            key,
            author,
            HomeCandidate.LOCAL_HOST,
            language,
            tags,
            createdAt,
            likes,
            reposts,
            replies,
            id),
        note ? id : null,
        c.get("userId").asLong(),
        null);
  }

  static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(item -> out.add(item.asString()));
    return out;
  }

  @Test
  void theVectorsUseTheWeightsTheServerRanksWith() {
    JsonNode w = doc.get("weights");
    HomeRanking.Weights weights = HomeRanking.Weights.DEFAULT;
    assertThat(doc.get("schema").asString()).isEqualTo("kurl.home-rank");
    assertThat(doc.get("version").asInt()).isEqualTo(1);
    assertThat(w.get("reaction").asDouble()).isEqualTo(weights.reaction());
    assertThat(w.get("freshness").asDouble()).isEqualTo(weights.freshness());
    assertThat(w.get("freshnessOffsetHours").asDouble()).isEqualTo(weights.freshnessOffsetHours());
    assertThat(w.get("language").asDouble()).isEqualTo(weights.language());
    assertThat(w.get("interest").asDouble()).isEqualTo(weights.interest());
    assertThat(w.get("follow").asDouble()).isEqualTo(weights.follow());
    assertThat(w.get("local").asDouble()).isEqualTo(weights.local());
    assertThat(w.get("block").asInt()).isEqualTo(weights.block());
    assertThat(w.get("authorSlots").asInt()).isEqualTo(weights.authorSlots());
    assertThat(w.get("hostSlots").asInt()).isEqualTo(weights.hostSlots());
    assertThat(w.get("maxRanked").asInt()).isEqualTo(weights.maxRanked());
    assertThat(Duration.ofMinutes(w.get("minePinMinutes").asLong()))
        .isEqualTo(weights.minePinned());
    assertThat(Duration.ofDays(w.get("kurlWindowDays").asLong())).isEqualTo(weights.localWindow());
    assertThat(Duration.ofHours(w.get("remoteWindowHours").asLong()))
        .isEqualTo(weights.remoteWindow());
    assertThat(w.get("remotePool").asInt()).isEqualTo(weights.remotePool());
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("cases")
  void theRankingScoresAndOrdersEachCaseAsTheVectorSays(JsonNode node) {
    Case in = input(node);
    JsonNode expected = node.get("expected");

    HomeRanking.Ranked ranked =
        HomeRanking.rank(in.candidates(), in.reader(), in.seen(), in.hide(), in.asOf());

    assertThat(ranked.items().stream().map(HomeCandidate::key).toList())
        .as(node.get("name").asString())
        .containsExactlyElementsOf(strings(expected.get("order")));
    assertThat(ranked.pinned()).isEqualTo(expected.get("pinned").asInt());
    Map<String, Double> scores = new HashMap<>();
    expected
        .get("scores")
        .properties()
        .forEach(s -> scores.put(s.getKey(), s.getValue().asDouble()));
    assertThat(ranked.scores().keySet()).isEqualTo(scores.keySet());
    scores.forEach(
        (key, score) ->
            assertThat(ranked.scores().get(key)).as(key).isCloseTo(score, within(1e-6)));
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("cases")
  void eachPageOfTheScrollIsTheVectorsPageAndTheCursorEndsWithTheList(JsonNode node) {
    Case in = input(node);
    List<List<String>> pages = new ArrayList<>();
    node.get("expected").get("pages").forEach(page -> pages.add(strings(page)));
    HomeForYou forYou = served(in);

    List<List<String>> scrolled = new ArrayList<>();
    String cursor = null;
    for (int turn = 0; turn <= pages.size(); turn++) {
      HomeFeedQueryService.Page page =
          forYou.page(
              ask(
                  in.reader().member(),
                  in.viewer(),
                  "for-you",
                  cursor,
                  null,
                  in.limit(),
                  in.hide()));
      scrolled.add(page.items().stream().map(HomeRankVectorsTest::key).toList());
      cursor = page.next();
      if (cursor == null) {
        break;
      }
    }

    assertThat(scrolled).as(node.get("name").asString()).isEqualTo(pages);
  }

  private static HomeForYou served(Case in) {
    RemoteForYou remote = mock(RemoteForYou.class);
    NoteQueryService notes = mock(NoteQueryService.class);
    PostHomeSlices posts = mock(PostHomeSlices.class);
    NoteViewReader views = mock(NoteViewReader.class);
    LocalSeen localSeen = mock(LocalSeen.class);
    RemoteSeen remoteSeen = mock(RemoteSeen.class);
    when(remote.enabled()).thenReturn(true);
    when(remote.homePool(in.reader().member(), in.asOf())).thenReturn(in.candidates());
    when(remote.reader(any(), any(), any(), any())).thenReturn(in.reader().taste());
    when(remote.follows(in.reader().member())).thenReturn(in.reader().follows());
    when(notes.pool(any(), any(), any(), anyInt())).thenReturn(List.of());
    when(posts.pool(any(), any(), any(), anyInt())).thenReturn(List.of());
    when(localSeen.seenBefore(any(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<SeenItem> asked = call.getArgument(1);
              Set<SeenItem> seen = new HashSet<>();
              for (SeenItem item : asked) {
                String key =
                    (item.kind() == SeenItem.NOTE ? "kurl:note:" : "kurl:post:") + item.id();
                if (in.seen().contains(key)) {
                  seen.add(item);
                }
              }
              return seen;
            });
    when(remoteSeen.seenBefore(any(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<String> asked = call.getArgument(1);
              Set<String> seen = new HashSet<>(asked);
              seen.retainAll(in.seen());
              return seen;
            });
    Map<Long, HomeCandidate> notesById = new HashMap<>();
    Map<Long, HomeCandidate> postsById = new HashMap<>();
    for (HomeCandidate candidate : in.candidates()) {
      (candidate.source() == HomeCandidate.Source.POST ? postsById : notesById)
          .merge(
              (Long) candidate.item().ref(),
              candidate,
              (first, again) -> first.local() ? first : again);
    }
    when(views.listed(any(), any()))
        .thenAnswer(
            call -> {
              List<Long> ids = call.getArgument(1);
              return ids.stream()
                  .map(
                      id ->
                          new NoteViewReader.Listed(
                              id,
                              NoteViewFixtures.note(id, notesById.get(id).key(), null, null, 0L),
                              notesById.get(id).key()))
                  .toList();
            });
    when(posts.cards(any(), any()))
        .thenAnswer(
            call -> {
              List<Long> ids = call.getArgument(1);
              return ids.stream()
                  .map(
                      id -> {
                        PublicFeedItem item = mock(PublicFeedItem.class);
                        when(item.id()).thenReturn(id);
                        return new PostCard(item, 1, 0, null);
                      })
                  .toList();
            });
    return new HomeForYou(
        remote, notes, posts, views, localSeen, remoteSeen, Clock.fixed(in.asOf(), ZoneOffset.UTC));
  }

  private static String key(HomeFeedQueryService.Item item) {
    return switch (item.kind()) {
      case "note" -> item.note().body();
      case "post" -> "kurl:post:" + item.post().item().id();
      default -> ((RemoteTrendingFeed.KeptNote) item.remote()).uri();
    };
  }
}
