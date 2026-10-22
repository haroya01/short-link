package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForYouRankingTest {

  private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  private static final ForYouReader KOREAN = new ForYouReader(Set.of("ko"), Map.of(), Map.of());

  private static long ids;

  private static ForYouCandidate note(
      String author, String host, String language, long likes, Duration age, String... tags) {
    long id = ++ids;
    return new ForYouCandidate(
        id,
        "https://" + host + "/users/" + author + "/statuses/" + id,
        author,
        host,
        language,
        Set.of(tags),
        NOW.minus(age),
        likes,
        0,
        0,
        id);
  }

  private static ForYouCandidate note(String author, String language, long likes) {
    return note(author, author + ".example", language, likes, Duration.ofHours(1));
  }

  private static List<String> authors(ForYouRanking.Ranked ranked) {
    return ranked.items().stream().map(ForYouCandidate::author).toList();
  }

  private static ForYouRanking.Ranked rank(List<ForYouCandidate> pool, ForYouReader reader) {
    return ForYouRanking.rank(pool, reader, Set.of(), NOW);
  }

  @Test
  void aBigForeignPostBeatsAQuietOneInTheReadersLanguageButNotOneWithSomeLikes() {
    ForYouCandidate viral = note("en", "en", 1_000);
    ForYouCandidate liked = note("ko-liked", "ko", 10);
    ForYouCandidate quiet = note("ko-quiet", "ko", 0);

    assertThat(authors(rank(List.of(quiet, viral, liked), KOREAN)))
        .containsExactly("ko-liked", "en", "ko-quiet");
  }

  @Test
  void aReaderWithNoSignalsGetsReactionsLanguageAndFreshnessOnly() {
    ForYouCandidate older = note("a", "a.example", "ja", 5, Duration.ofHours(30));
    ForYouCandidate newer = note("b", "b.example", "ja", 5, Duration.ofHours(2));
    ForYouCandidate unknownLanguage = note("c", "c.example", null, 5, Duration.ofHours(2));

    assertThat(
            authors(
                rank(
                    List.of(older, unknownLanguage, newer),
                    new ForYouReader(Set.of("ja"), Map.of(), Map.of()))))
        .containsExactly("b", "a", "c");
    assertThat(authors(rank(List.of(older, unknownLanguage, newer), ForYouReader.NONE)))
        .containsExactly("c", "b", "a");
  }

  @Test
  void aTagTheReaderCaresForLiftsAPostAndACommonTagLiftsItLess() {
    List<ForYouCandidate> pool = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      pool.add(note("filler" + i, "h" + i + ".example", "ko", 3, Duration.ofHours(1), "일상"));
    }
    ForYouCandidate rare = note("rare", "r.example", "ko", 0, Duration.ofHours(1), "자바", "일상");
    ForYouCandidate common = note("common", "c.example", "ko", 0, Duration.ofHours(1), "일상");
    pool.add(common);
    pool.add(rare);
    ForYouReader reader = new ForYouReader(Set.of("ko"), Map.of("자바", 1.0, "일상", 1.0), Map.of());

    List<String> order = authors(rank(pool, reader));

    assertThat(order.getFirst()).isEqualTo("rare");
    assertThat(order.indexOf("common")).isGreaterThan(order.indexOf("rare"));
  }

  @Test
  void anAuthorTheReaderKeepsComingBackToIsLiftedUpToThreeSignals() {
    ForYouCandidate known = note("known", "ko", 0);
    ForYouCandidate stranger = note("stranger", "ko", 4);

    assertThat(
            authors(
                rank(
                    List.of(stranger, known),
                    new ForYouReader(Set.of("ko"), Map.of(), Map.of("known", 3.0)))))
        .containsExactly("known", "stranger");
    assertThat(
            authors(
                rank(
                    List.of(stranger, known),
                    new ForYouReader(Set.of("ko"), Map.of(), Map.of("known", 0.5)))))
        .containsExactly("stranger", "known");
  }

  @Test
  void aBlockOfTwentyHoldsAtMostTwoByOneAuthorAndTenFromOneServer() {
    List<ForYouCandidate> pool = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      pool.add(note("loud", "big.example", "ko", 100, Duration.ofMinutes(i)));
    }
    for (int i = 0; i < 20; i++) {
      pool.add(note("big" + i, "big.example", "ko", 50, Duration.ofMinutes(i)));
    }
    for (int i = 0; i < 10; i++) {
      pool.add(note("small" + i, "small" + i + ".example", "ko", 0, Duration.ofMinutes(i)));
    }

    List<ForYouCandidate> first = rank(pool, KOREAN).items().subList(0, 20);

    assertThat(first.stream().filter(c -> c.author().equals("loud"))).hasSize(2);
    assertThat(first.stream().filter(c -> c.host().equals("big.example"))).hasSize(10);
    assertThat(first.getFirst().author()).isEqualTo("loud");
  }

  @Test
  void whenNothingElseIsLeftTheServerLimitGivesWayFirstAndThenTheAuthors() {
    List<ForYouCandidate> pool = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      pool.add(note("solo", "one.example", "ko", 10, Duration.ofMinutes(i)));
    }
    for (int i = 0; i < 12; i++) {
      pool.add(note("one" + i, "one.example", "ko", 1, Duration.ofMinutes(i)));
    }

    List<ForYouCandidate> ranked = rank(pool, KOREAN).items();

    assertThat(ranked).hasSize(15);
    assertThat(ranked.subList(0, 14).stream().filter(c -> c.author().equals("solo"))).hasSize(2);
    assertThat(ranked.getLast().author()).isEqualTo("solo");
  }

  @Test
  void whatTheReaderSawComesAfterEverythingElseAndSaysWhereItStarts() {
    ForYouCandidate best = note("best", "ko", 100);
    ForYouCandidate good = note("good", "ko", 10);
    ForYouCandidate plain = note("plain", "ko", 0);

    ForYouRanking.Ranked ranked =
        ForYouRanking.rank(List.of(best, good, plain), KOREAN, Set.of(best.uri()), NOW);

    assertThat(authors(ranked)).containsExactly("good", "plain", "best");
    assertThat(ranked.seenFrom()).isEqualTo(2);
    assertThat(rank(List.of(best, good), KOREAN).seenFrom()).isEqualTo(-1);
  }

  @Test
  void theRankingStopsAtFourHundredAndLeavesTheSeenOutWhenItIsFull() {
    List<ForYouCandidate> pool = new ArrayList<>();
    for (int i = 0; i < 450; i++) {
      pool.add(note("a" + i, "h" + (i % 30) + ".example", "ko", i % 7, Duration.ofMinutes(i)));
    }

    ForYouRanking.Ranked ranked =
        ForYouRanking.rank(pool, KOREAN, Set.of(pool.getFirst().uri()), NOW);

    assertThat(ranked.items()).hasSize(ForYouRanking.MAX_RANKED);
    assertThat(ranked.seenFrom()).isEqualTo(-1);
  }

  @Test
  void equalScoresGoByTheNewerNoteSoTheOrderNeverDependsOnThePoolsOrder() {
    ForYouCandidate first = note("x", "x.example", "ko", 0, Duration.ofHours(1));
    ForYouCandidate second = note("y", "y.example", "ko", 0, Duration.ofHours(1));

    assertThat(authors(rank(List.of(first, second), KOREAN))).containsExactly("y", "x");
    assertThat(authors(rank(List.of(second, first), KOREAN))).containsExactly("y", "x");
  }

  @Test
  void boostsCountTwiceAndRepliesOnce() {
    ForYouCandidate boosted =
        new ForYouCandidate(1L, "https://a/1", "a", "a", "ko", Set.of(), NOW, 1, 2, 3, 1L);

    assertThat(boosted.engagement()).isEqualTo(8);
    assertThat(
            new ForYouCandidate(2L, "https://b/2", null, null, "ko-KR", null, NOW, -1, -1, -1, 2)
                .engagement())
        .isZero();
  }
}
