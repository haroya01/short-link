package com.example.short_link.post.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class FeedRankingTest {

  private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

  private static FeedCandidate post(long id, long author, long hour, Long series, String... tags) {
    return new FeedCandidate(id, author, List.of(tags), "ko", T.plusSeconds(hour * 3600), series);
  }

  private static List<Long> ids(List<FeedCandidate> ranked) {
    return ranked.stream().map(FeedCandidate::postId).toList();
  }

  @Test
  void recentPutsTheNewestFirstAndTheHigherIdFirstOnATie() {
    List<FeedCandidate> pool =
        List.of(post(1, 1, 5, null), post(2, 1, 9, null), post(3, 1, 5, null), post(4, 1, 1, null));

    assertThat(ids(FeedRanking.recent(pool))).containsExactly(2L, 3L, 1L, 4L);
  }

  @Test
  void recentShowsOnlyTheNewestEpisodeOfASeries() {
    List<FeedCandidate> pool =
        List.of(
            post(1, 1, 1, 7L),
            post(2, 1, 3, 7L),
            post(3, 1, 3, 7L),
            post(4, 1, 2, null),
            post(5, 2, 0, 8L));

    assertThat(ids(FeedRanking.recent(pool))).containsExactly(3L, 4L, 5L);
  }

  @Test
  void trendingRanksByRecentViewsThenByRecency() {
    List<FeedCandidate> pool =
        List.of(post(1, 1, 1, null), post(2, 1, 2, null), post(3, 1, 3, null), post(4, 1, 4, null));

    List<FeedCandidate> ranked = FeedRanking.trending(pool, Map.of(1L, 5L, 3L, 5L, 2L, 1L));

    assertThat(ids(ranked)).containsExactly(3L, 1L, 2L, 4L);
  }

  @Test
  void trendingCollapsesSeriesBeforeRanking() {
    List<FeedCandidate> pool = List.of(post(2, 1, 2, 7L), post(1, 1, 1, 7L), post(3, 1, 0, null));

    List<FeedCandidate> ranked = FeedRanking.trending(pool, Map.of(1L, 50L, 3L, 1L));

    assertThat(ids(ranked)).containsExactly(3L, 2L);
  }

  @Test
  void forYouKeepsUnreadPostsByOthersThatShareAnInterest() {
    List<FeedCandidate> pool =
        List.of(
            post(1, 2, 1, null, "Java"),
            post(2, 9, 2, null, "java"),
            post(3, 2, 3, null, "java"),
            post(4, 2, 4, null, "go"),
            post(5, 3, 5, 7L, "spring", "go"),
            post(6, 3, 6, 7L, "SPRING"));

    List<FeedCandidate> ranked =
        FeedRanking.forYou(pool, 9L, List.of("java", "spring"), List.of(3L));

    assertThat(ids(ranked)).containsExactly(6L, 5L, 1L);
  }

  @Test
  void forYouLooksOnlyAtTheNewestPoolAndTheNewestReads() {
    List<FeedCandidate> pool =
        LongStream.rangeClosed(1, FeedRanking.CANDIDATE_POOL_SIZE + 1)
            .mapToObj(id -> post(id, 2, id, null, "java"))
            .toList();
    List<Long> readsNewestFirst =
        LongStream.rangeClosed(2, FeedRanking.EXCLUDED_READS + 2).boxed().toList().reversed();

    List<Long> ranked = ids(FeedRanking.forYou(pool, 9L, List.of("java"), readsNewestFirst));

    assertThat(ranked)
        .hasSize(FeedRanking.CANDIDATE_POOL_SIZE - FeedRanking.EXCLUDED_READS)
        .contains(2L)
        .doesNotContain(1L, 3L, (long) FeedRanking.EXCLUDED_READS + 2);
  }

  @Test
  void forYouWithoutInterestsIsEmpty() {
    assertThat(FeedRanking.forYou(List.of(post(1, 2, 1, null, "java")), 9L, List.of(), List.of()))
        .isEmpty();
  }
}
