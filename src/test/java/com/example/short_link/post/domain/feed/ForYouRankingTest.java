package com.example.short_link.post.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.feed.ForYouRanking.Reader;
import com.example.short_link.post.domain.feed.ForYouRanking.Weights;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ForYouRankingTest {

  private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
  private static final long VIEWER = 9L;

  private static FeedCandidate post(
      long id, long author, String lang, long hoursAgo, String... tags) {
    return new FeedCandidate(
        id, author, List.of(tags), lang, NOW.minus(Duration.ofHours(hoursAgo)), null);
  }

  private static Reader reader(Map<String, Integer> interest, String... hidden) {
    return new Reader(VIEWER, interest, Set.of(hidden), Set.of("ko"), List.of(), Set.of());
  }

  private static List<Long> ids(List<FeedCandidate> ranked) {
    return ranked.stream().map(FeedCandidate::postId).toList();
  }

  @Test
  void ownPostsRecentReadsAndHiddenTagsAreNotCandidates() {
    List<FeedCandidate> pool =
        List.of(
            post(1, VIEWER, "ko", 1, "java"),
            post(2, 2, "ko", 2, "java"),
            post(3, 2, "ko", 3, "java", "crypto"),
            post(4, 2, "ko", 4, "java"));
    Reader reader =
        new Reader(
            VIEWER, Map.of("java", 1), Set.of("crypto"), Set.of("ko"), List.of(2L), Set.of());

    assertThat(ids(ForYouRanking.rank(pool, reader, Map.of(), NOW))).containsExactly(4L);
  }

  @Test
  void aSharedInterestOutranksANewerPostWithout() {
    List<FeedCandidate> pool = List.of(post(1, 2, "ko", 1, "go"), post(2, 3, "ko", 30, "java"));

    assertThat(ids(ForYouRanking.rank(pool, reader(Map.of("java", 2)), Map.of(), NOW)))
        .containsExactly(2L, 1L);
  }

  @Test
  void aRareSharedTagCountsForMoreThanACommonOne() {
    List<FeedCandidate> pool =
        List.of(
            post(1, 2, "ko", 1, "beginner"),
            post(2, 3, "ko", 1, "jpa"),
            post(3, 4, "ko", 1, "beginner", "go"),
            post(4, 5, "ko", 1, "beginner", "rust"),
            post(5, 6, "ko", 1, "beginner", "kotlin"));
    Reader reader = reader(Map.of("beginner", 2, "jpa", 1));
    Weights undiscounted = new Weights(3.0, 1.5, 1.0, 0.3, false, true);

    assertThat(ids(ForYouRanking.rank(pool, reader, Map.of(), NOW))).startsWith(2L, 1L);
    assertThat(ids(ForYouRanking.rank(pool, reader, Map.of(), NOW, undiscounted)))
        .startsWith(1L)
        .endsWith(2L);
  }

  @Test
  void theReadersLanguageComesFirstWhenTopicsTie() {
    List<FeedCandidate> pool = List.of(post(1, 2, "ja", 1, "java"), post(2, 3, "ko", 2, "java"));

    assertThat(ids(ForYouRanking.rank(pool, reader(Map.of("java", 1)), Map.of(), NOW)))
        .containsExactly(2L, 1L);
  }

  @Test
  void humanViewsLiftAPostWhenEverythingElseTies() {
    List<FeedCandidate> pool = List.of(post(1, 2, "ko", 5, "java"), post(2, 3, "ko", 5, "java"));

    assertThat(ids(ForYouRanking.rank(pool, reader(Map.of("java", 1)), Map.of(1L, 30L), NOW)))
        .containsExactly(1L, 2L);
  }

  @Test
  void oneAuthorTakesAtMostTwoOfEveryTenWhileOthersAreLeft() {
    List<FeedCandidate> prolific =
        LongStream.rangeClosed(1, 6).mapToObj(id -> post(id, 2, "ko", id, "java")).toList();
    List<FeedCandidate> others =
        LongStream.rangeClosed(7, 10)
            .mapToObj(id -> post(id, id + 100, "ko", 40 + id, "java"))
            .toList();
    List<FeedCandidate> pool = new ArrayList<>(prolific);
    pool.addAll(others);

    List<FeedCandidate> ranked = ForYouRanking.rank(pool, reader(Map.of("java", 1)), Map.of(), NOW);

    assertThat(ids(ranked)).hasSize(10).startsWith(1L, 2L, 7L, 8L, 9L, 10L, 3L, 4L, 5L, 6L);
  }

  @Test
  void anAuthorTheReaderKeepsReadingIsNotCapped() {
    List<FeedCandidate> pool = new ArrayList<>();
    LongStream.rangeClosed(1, 6).forEach(id -> pool.add(post(id, 2, "ko", id, "java")));
    LongStream.rangeClosed(7, 10)
        .forEach(id -> pool.add(post(id, id + 100, "ko", 40 + id, "java")));
    Reader reader =
        new Reader(VIEWER, Map.of("java", 1), Set.of(), Set.of("ko"), List.of(), Set.of(2L));

    assertThat(ids(ForYouRanking.rank(pool, reader, Map.of(), NOW)))
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
  }

  @Test
  void readersBecomeFamiliarWithAnAuthorAfterThreeOfTheirLastFortyReads() {
    Map<Long, FeedCandidate> read =
        Map.of(
            11L, post(11, 2, "ko", 100, "java"),
            12L, post(12, 2, "ko", 101, "java"),
            13L, post(13, 2, "ko", 102, "java"),
            14L, post(14, 3, "ko", 103, "java"),
            15L, post(15, 3, "ko", 104, "java"));

    Reader reader =
        Reader.of(
            VIEWER, "ko", List.of(), List.of(), List.of(11L, 12L, 13L, 14L, 15L), List.of(), read);

    assertThat(reader.familiarAuthors()).containsExactly(2L);
  }

  @Test
  void postsSharingAnInterestComeBeforeTheRestHoweverTheRestScores() {
    List<FeedCandidate> pool =
        List.of(post(1, 2, "ja", 24 * 20, "java"), post(2, 3, "ko", 0, "go"));

    assertThat(ids(ForYouRanking.rank(pool, reader(Map.of("java", 1)), Map.of(2L, 1_000L), NOW)))
        .containsExactly(1L, 2L);
  }

  @Test
  void withoutInterestsTheReadersLanguageAndFreshnessStillRank() {
    List<FeedCandidate> pool =
        List.of(post(1, 2, "ja", 1, "java"), post(2, 3, "ko", 50, "go"), post(3, 4, "ko", 2, "go"));

    assertThat(ids(ForYouRanking.rank(pool, reader(Map.of()), Map.of(), NOW)))
        .containsExactly(3L, 2L, 1L);
  }

  @Test
  void looksOnlyAtTheNewestPoolAndTheNewestReads() {
    List<FeedCandidate> pool =
        LongStream.rangeClosed(1, FeedRanking.CANDIDATE_POOL_SIZE + 1)
            .mapToObj(id -> post(id, id + 100, "ko", 10_000 - id, "java"))
            .toList();
    List<Long> readsNewestFirst =
        LongStream.rangeClosed(2, FeedRanking.EXCLUDED_READS + 2).boxed().toList().reversed();
    Reader reader =
        new Reader(VIEWER, Map.of("java", 1), Set.of(), Set.of("ko"), readsNewestFirst, Set.of());

    List<Long> ranked = ids(ForYouRanking.rank(pool, reader, Map.of(), NOW));

    assertThat(ranked)
        .hasSize(FeedRanking.CANDIDATE_POOL_SIZE - FeedRanking.EXCLUDED_READS)
        .contains(2L)
        .doesNotContain(1L, 3L, (long) FeedRanking.EXCLUDED_READS + 2);
  }
}
