package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counts;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Reach;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

class RemoteForYouPagingPropertiesTest {

  private static final Instant NOW = Instant.parse("2026-10-23T03:00:00.123456Z");
  private static final SeenViewer MEMBER = SeenViewer.member(7L);
  private static final SeenViewer DEVICE =
      SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7");
  private static final int TRIALS = 150;
  private static final List<String> LANGUAGES = List.of("ko", "ja", "en");

  private record Note(
      long id, long actor, String domain, String language, Duration age, Counts counts) {

    String uri() {
      return "https://" + domain + "/users/u" + actor + "/statuses/" + id;
    }
  }

  private record World(List<Note> notes, Set<Long> seen, Set<Long> gone, String locale) {

    Set<String> seenUris() {
      return notes.stream()
          .filter(note -> seen.contains(note.id()))
          .map(Note::uri)
          .collect(Collectors.toSet());
    }
  }

  private static <T> T lenient(Class<T> type) {
    return mock(type, withSettings().strictness(Strictness.LENIENT));
  }

  private static RemoteForYou forYou(World world) {
    NoteViewReader notes = lenient(NoteViewReader.class);
    RemoteNoteReachRepository reach = lenient(RemoteNoteReachRepository.class);
    ForYouSignalReader signals = lenient(ForYouSignalReader.class);
    RemoteSeen seen = lenient(RemoteSeen.class);
    Map<Long, Note> byId = new HashMap<>();
    world.notes().forEach(note -> byId.put(note.id(), note));
    when(notes.remotePool(any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(
            world.notes().stream()
                .map(
                    note ->
                        new NoteViewReader.Pooled(
                            note.id(),
                            note.actor(),
                            note.domain(),
                            note.language(),
                            NOW.minus(note.age()),
                            note.uri(),
                            List.of(),
                            NOW.minus(note.age())))
                .toList());
    when(reach.of(any()))
        .thenReturn(
            world.notes().stream()
                .collect(
                    Collectors.toMap(
                        Note::id,
                        note ->
                            new Reach(
                                note.id(), note.counts(), world.gone().contains(note.id())))));
    when(signals.signals(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
    when(signals.locale(anyLong())).thenReturn(Optional.ofNullable(world.locale()));
    when(seen.seenBefore(any(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<String> asked = call.getArgument(1);
              Set<String> seenUris = world.seenUris();
              return asked.stream().filter(seenUris::contains).collect(Collectors.toSet());
            });
    when(notes.listed(any(), any()))
        .thenAnswer(
            call -> {
              List<Long> ids = call.getArgument(1);
              return ids.stream()
                  .map(byId::get)
                  .map(note -> new NoteViewReader.Listed(note.id(), note.id(), note.uri()))
                  .toList();
            });
    return new RemoteForYou(
        new LatestProperties(true, List.of(), 40, 0, null, null, true, null, 0, 0),
        lenient(LatestPosts.class),
        lenient(TrendingPosts.class),
        lenient(TrendingAuthors.class),
        notes,
        reach,
        signals,
        seen,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static World world(Random random, int size, double seenShare, boolean crowded) {
    List<Note> notes = new ArrayList<>();
    int loud = crowded ? 1 + random.nextInt(4) : 0;
    for (int i = 0; i < size; i++) {
      long id = 1_000 + i;
      boolean heavy = i < loud * 30;
      long actor = heavy ? i % loud : 100 + (crowded ? i : random.nextInt(25));
      String domain =
          heavy
              ? "big" + actor % 2 + ".example"
              : crowded ? "small" + i / 5 + ".example" : "h" + actor % 6 + ".example";
      notes.add(
          new Note(
              id,
              actor,
              domain,
              LANGUAGES.get(random.nextInt(LANGUAGES.size())),
              Duration.ofMinutes(random.nextInt(72 * 60)),
              new Counts(
                  heavy ? 300 + random.nextInt(2_000) : random.nextInt(60), random.nextInt(4), 0)));
    }
    Set<Long> seen = new HashSet<>();
    notes.forEach(
        note -> {
          if (random.nextDouble() < seenShare) {
            seen.add(note.id());
          }
        });
    return new World(notes, seen, Set.of(), LANGUAGES.get(random.nextInt(LANGUAGES.size())));
  }

  private record Scroll(List<Long> ids, List<RemoteTrendingFeed.Page> pages) {}

  private static Scroll scroll(RemoteForYou forYou, SeenViewer viewer, int limit, boolean hide) {
    List<Long> ids = new ArrayList<>();
    List<RemoteTrendingFeed.Page> pages = new ArrayList<>();
    Long viewerId = viewer.kind() == SeenViewer.MEMBER ? 7L : null;
    String cursor = null;
    do {
      RemoteTrendingFeed.Page page = forYou.page(viewerId, viewer, "ko", cursor, limit, hide);
      pages.add(page);
      page.items().forEach(item -> ids.add((Long) ((RemoteTrendingFeed.KeptNote) item).note()));
      cursor = page.next();
    } while (cursor != null && pages.size() < 1_000);
    return new Scroll(ids, pages);
  }

  @Test
  void pagesFollowedByTheirCursorsNeverRepeatANoteAndTogetherHoldTheWholeRanking() {
    for (int t = 0; t < TRIALS; t++) {
      Random random = new Random(7_000 + t);
      World world = world(random, random.nextInt(450), random.nextDouble() / 2, false);
      int limit = 1 + random.nextInt(40);

      Scroll scroll = scroll(forYou(world), MEMBER, limit, false);

      assertThat(new HashSet<>(scroll.ids())).as("seed %d", t).hasSameSizeAs(scroll.ids());
      assertThat(scroll.ids()).as("seed %d", t).hasSize(Math.min(world.notes().size(), 400));
      assertThat(scroll.pages().subList(0, scroll.pages().size() - 1))
          .as("seed %d", t)
          .allMatch(page -> page.items().size() == limit);
    }
  }

  @Test
  void withSeenHiddenNoPageHoldsASeenNoteAndTheScrollEndsWhereTheUnseenEnd() {
    for (int t = 0; t < TRIALS; t++) {
      Random random = new Random(8_000 + t);
      World world = world(random, random.nextInt(300), random.nextDouble(), false);

      Scroll scroll = scroll(forYou(world), MEMBER, 1 + random.nextInt(40), true);

      assertThat(scroll.ids()).as("seed %d", t).noneMatch(world.seen()::contains);
      assertThat(new HashSet<>(scroll.ids()))
          .as("seed %d", t)
          .isEqualTo(
              world.notes().stream()
                  .map(Note::id)
                  .filter(id -> !world.seen().contains(id))
                  .collect(Collectors.toSet()));
      assertThat(scroll.pages()).as("seed %d", t).allMatch(page -> page.seenFrom() == null);
      assertThat(scroll.pages().getLast().next()).as("seed %d", t).isNull();
    }
  }

  @Test
  void withSeenLaterEverySeenNoteComesAfterEveryUnseenOneAndOnlyThePageWhereTheyStartSaysWhere() {
    for (int t = 0; t < TRIALS; t++) {
      Random random = new Random(9_000 + t);
      World world = world(random, random.nextInt(300), random.nextDouble(), false);
      int limit = 1 + random.nextInt(40);

      Scroll scroll = scroll(forYou(world), MEMBER, limit, false);
      List<Long> ids = scroll.ids();
      int firstSeen = -1;
      for (int i = 0; i < ids.size(); i++) {
        if (world.seen().contains(ids.get(i))) {
          firstSeen = i;
          break;
        }
      }

      if (firstSeen < 0) {
        assertThat(scroll.pages()).as("seed %d", t).allMatch(page -> page.seenFrom() == null);
        continue;
      }
      assertThat(ids.subList(firstSeen, ids.size()))
          .as("seed %d", t)
          .allMatch(world.seen()::contains);
      int pageOf = firstSeen / limit;
      for (int p = 0; p < scroll.pages().size(); p++) {
        assertThat(scroll.pages().get(p).seenFrom())
            .as("seed %d page %d", t, p)
            .isEqualTo(p == pageOf ? firstSeen % limit : null);
      }
    }
  }

  @Test
  void everyPageOfTwentyHoldsTwoPerAuthorAndTenPerServerWhileOthersAreLeftToFillIt() {
    for (int t = 0; t < TRIALS; t++) {
      Random random = new Random(10_000 + t);
      int size = 140 + random.nextInt(260);
      World world = world(random, size, 0, true);
      Map<Long, Note> byId =
          world.notes().stream().collect(Collectors.toMap(Note::id, note -> note));
      int heavy = (int) world.notes().stream().filter(n -> n.domain().startsWith("big")).count();
      int fullPages = (size - heavy) / 20;

      Scroll scroll = scroll(forYou(world), MEMBER, 20, false);

      for (int p = 0; p < Math.min(fullPages, scroll.pages().size()); p++) {
        List<Note> page =
            scroll.pages().get(p).items().stream()
                .map(item -> byId.get((Long) ((RemoteTrendingFeed.KeptNote) item).note()))
                .toList();
        assertThat(page.stream().collect(Collectors.groupingBy(Note::actor, Collectors.counting())))
            .as("seed %d page %d", t, p)
            .allSatisfy((actor, count) -> assertThat(count).isLessThanOrEqualTo(2));
        assertThat(
                page.stream().collect(Collectors.groupingBy(Note::domain, Collectors.counting())))
            .as("seed %d page %d", t, p)
            .allSatisfy((domain, count) -> assertThat(count).isLessThanOrEqualTo(10));
      }
    }
  }

  @Test
  void aNoteGoneFromTheOtherServerNeverShowsAndLeavesNoHoleInThePaging() {
    for (int t = 0; t < TRIALS; t++) {
      Random random = new Random(11_000 + t);
      World drawn = world(random, random.nextInt(200), 0, false);
      Set<Long> gone =
          drawn.notes().stream()
              .map(Note::id)
              .filter(id -> random.nextInt(4) == 0)
              .collect(Collectors.toSet());
      World world = new World(drawn.notes(), Set.of(), gone, drawn.locale());

      Scroll scroll = scroll(forYou(world), MEMBER, 1 + random.nextInt(40), false);

      assertThat(scroll.ids()).as("seed %d", t).noneMatch(gone::contains);
      assertThat(scroll.ids()).as("seed %d", t).hasSize(drawn.notes().size() - gone.size());
    }
  }

  private static World twoLanguages(String locale) {
    return new World(
        List.of(
            new Note(1, 1, "a.example", "ja", Duration.ofHours(1), new Counts(5, 0, 0)),
            new Note(2, 2, "b.example", "ko", Duration.ofHours(1), new Counts(6, 0, 0))),
        Set.of(),
        Set.of(),
        locale);
  }

  private static List<Long> firstPage(RemoteForYou forYou, Long viewerId, SeenViewer viewer) {
    return forYou.page(viewerId, viewer, "ja", null, 20, false).items().stream()
        .map(item -> (Long) ((RemoteTrendingFeed.KeptNote) item).note())
        .toList();
  }

  @Test
  void
      pinsTodaysGapTheSameJapaneseBrowserGetsJapaneseFirstSignedOutButTheAccountLocaleOnceSignedIn() {
    World world = twoLanguages("ko");

    assertThat(firstPage(forYou(world), null, DEVICE)).containsExactly(1L, 2L);
    assertThat(firstPage(forYou(world), 7L, MEMBER)).containsExactly(2L, 1L);
    assertThat(firstPage(forYou(twoLanguages(null)), 7L, MEMBER)).containsExactly(2L, 1L);
    assertThat(firstPage(forYou(twoLanguages("ja")), 7L, MEMBER)).containsExactly(1L, 2L);
  }
}
