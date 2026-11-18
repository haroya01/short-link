package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HomeRankingTest {

  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

  private static HomeCandidate remote(String uri, long order, Long noteId) {
    return HomeCandidate.remote(
        new ForYouCandidate(
            uri,
            uri,
            null,
            "a.example",
            "en",
            Set.of(),
            NOW.minus(Duration.ofHours(2)),
            0,
            0,
            0,
            order),
        noteId,
        NOW.minus(Duration.ofHours(1)));
  }

  private static List<HomeCandidate> ranked(HomeCandidate... candidates) {
    return HomeRanking.rank(List.of(candidates), HomeRanking.Reader.NONE, Set.of(), false, NOW)
        .items();
  }

  private static final HomeRanking.Reader KOREAN = reading("ko");

  private static HomeRanking.Reader reading(String... languages) {
    return new HomeRanking.Reader(
        new ForYouReader(Set.of(languages), Map.of(), Map.of()), null, Set.of());
  }

  private static HomeCandidate written(String name, String language, String author, long likes) {
    String uri = "https://" + author + ".example/notes/" + name;
    return HomeCandidate.remote(
        new ForYouCandidate(
            uri,
            uri,
            author,
            author + ".example",
            language,
            Set.of(),
            NOW.minus(Duration.ofHours(2)),
            likes,
            0,
            0,
            likes),
        null,
        NOW.minus(Duration.ofHours(1)));
  }

  private static List<HomeCandidate> rankedFor(
      HomeRanking.Reader reader, HomeCandidate... candidates) {
    return HomeRanking.rank(List.of(candidates), reader, Set.of(), false, NOW).items();
  }

  @Test
  void aPostInTheReadersLanguageComesBeforeAForeignOneHoweverManyReactionsThatOneHas() {
    HomeCandidate quiet = written("quiet", "ko", "a", 0);
    HomeCandidate viral = written("viral", "en", "b", 50_000);
    HomeCandidate liked = written("liked", "ko-KR", "c", 3);

    HomeRanking.Ranked ranked =
        HomeRanking.rank(List.of(quiet, viral, liked), KOREAN, Set.of(), false, NOW);

    assertThat(ranked.items()).containsExactly(liked, quiet, viral);
    assertThat(ranked.scores().get(viral.key())).isGreaterThan(ranked.scores().get(liked.key()));
  }

  @Test
  void anUnknownLanguageComesAfterTheReadersAndBeforeAForeignOne() {
    HomeCandidate foreign = written("foreign", "en", "a", 50_000);
    HomeCandidate unknown = written("unknown", null, "b", 500);
    HomeCandidate own = written("own", "ko", "c", 0);

    assertThat(rankedFor(KOREAN, foreign, unknown, own)).containsExactly(own, unknown, foreign);
  }

  @Test
  void everyLanguageOfTheReaderIsTheOneFirstTierOrderedByScore() {
    HomeCandidate korean = written("korean", "ko", "a", 1);
    HomeCandidate japanese = written("japanese", "ja", "b", 9);
    HomeCandidate english = written("english", "en", "c", 50_000);

    assertThat(rankedFor(reading("ko", "ja"), korean, japanese, english))
        .containsExactly(japanese, korean, english);
  }

  @Test
  void theAuthorCapIsCountedInsideEachTierAndNeverPullsAForeignPostAboveOneOfTheReaders() {
    HomeCandidate a9 = written("a9", "ko", "a", 9);
    HomeCandidate a8 = written("a8", "ko", "a", 8);
    HomeCandidate a7 = written("a7", "ko", "a", 7);
    HomeCandidate a6 = written("a6", "ko", "a", 6);
    HomeCandidate b1 = written("b1", "ko", "b", 1);
    HomeCandidate c = written("c", "en", "c", 102);
    HomeCandidate d = written("d", "en", "d", 101);
    HomeCandidate e = written("e", "en", "e", 100);

    assertThat(rankedFor(KOREAN, c, a6, b1, d, a9, e, a7, a8))
        .containsExactly(a9, a8, b1, a7, a6, c, d, e);
  }

  @Test
  void aReaderWithNoLanguageGetsTheOneListByScoreWithItsCapsAsBefore() {
    HomeCandidate a9 = written("a9", "ko", "a", 9);
    HomeCandidate a8 = written("a8", "ko", "a", 8);
    HomeCandidate a7 = written("a7", "ko", "a", 7);
    HomeCandidate a6 = written("a6", "ko", "a", 6);
    HomeCandidate b1 = written("b1", "ko", "b", 1);
    HomeCandidate c = written("c", "en", "c", 102);
    HomeCandidate d = written("d", "en", "d", 101);
    HomeCandidate e = written("e", null, "e", 100);

    assertThat(rankedFor(HomeRanking.Reader.NONE, c, a6, b1, d, a9, e, a7, a8))
        .containsExactly(c, d, e, a9, a8, b1, a7, a6);
  }

  @Test
  void theListStopsAtItsCapCountedAcrossTheTiers() {
    HomeRanking.Weights three =
        new HomeRanking.Weights(
            1.0,
            1.5,
            2.0,
            3.0,
            3.0,
            2.0,
            1.0,
            20,
            2,
            10,
            3,
            Duration.ofMinutes(60),
            Duration.ofDays(7),
            Duration.ofHours(72),
            1_000);
    HomeCandidate own = written("own", "ko", "a", 0);
    HomeCandidate another = written("another", "ko", "b", 1);
    HomeCandidate unknown = written("unknown", null, "c", 5);
    HomeCandidate quieter = written("quieter", null, "d", 4);
    HomeCandidate foreign = written("foreign", "en", "e", 50_000);

    assertThat(
            HomeRanking.rank(
                    List.of(foreign, quieter, unknown, own, another),
                    KOREAN,
                    Set.of(),
                    false,
                    NOW,
                    three)
                .items())
        .containsExactly(another, own, unknown);
  }

  @Test
  void myFreshPostStaysOnTopWhateverItsLanguage() {
    HomeCandidate mine =
        HomeCandidate.note(1, 7, "en", Set.of(), NOW.minus(Duration.ofMinutes(5)), 0, 0, 0);
    HomeCandidate own = written("own", "ko", "a", 0);
    HomeCandidate foreign = written("foreign", "en", "b", 50_000);
    HomeRanking.Reader member =
        new HomeRanking.Reader(new ForYouReader(Set.of("ko"), Map.of(), Map.of()), 7L, Set.of());

    HomeRanking.Ranked ranked =
        HomeRanking.rank(List.of(foreign, own, mine), member, Set.of(), false, NOW);

    assertThat(ranked.items()).containsExactly(mine, own, foreign);
    assertThat(ranked.pinned()).isEqualTo(1);
  }

  @Test
  void aRemoteNoteThatCameInUnderTwoAddressesIsRankedOnce() {
    HomeCandidate first = remote("https://a.example/notes/1", 3, 7L);
    HomeCandidate again = remote("https://a.example/@x/1", 2, 7L);
    HomeCandidate other = remote("https://a.example/notes/2", 1, 8L);

    assertThat(ranked(first, again, other)).containsExactlyInAnyOrder(first, other);
  }

  @Test
  void remoteNotesNotYetKeptHereAreNotFoldedIntoOne() {
    HomeCandidate one = remote("https://a.example/notes/1", 2, null);
    HomeCandidate two = remote("https://a.example/notes/2", 1, null);

    assertThat(ranked(one, two)).containsExactlyInAnyOrder(one, two);
  }
}
