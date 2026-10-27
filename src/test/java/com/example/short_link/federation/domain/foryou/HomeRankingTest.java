package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
