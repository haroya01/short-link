package com.example.short_link.post.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class InterestProfileTest {

  @Test
  void aFollowedTagOutweighsTagsSeenOnlyInReads() {
    Map<String, Integer> weights =
        InterestProfile.weights(
            List.of("Rust"), List.of(List.of("java"), List.of("java", "spring")), List.of());

    assertThat(weights)
        .containsExactlyInAnyOrderEntriesOf(Map.of("rust", 3, "java", 2, "spring", 1));
  }

  @Test
  void spellingsThatDifferOnlyInCaseAreOneTag() {
    Map<String, Integer> weights =
        InterestProfile.weights(
            List.of("Java"), List.of(List.of("JAVA"), List.of("kotlin")), List.of());

    assertThat(weights).containsExactlyInAnyOrderEntriesOf(Map.of("java", 4, "kotlin", 1));
  }

  @Test
  void hiddenTagsLeaveTheProfileWhateverTheirCase() {
    Map<String, Integer> weights =
        InterestProfile.weights(
            List.of("crypto", "ai"), List.of(List.of("Crypto")), List.of("CRYPTO"));

    assertThat(weights).containsOnlyKeys("ai");
  }

  @Test
  void signalsAreTheNewestFortyReadsAndFortyLikesWithoutRepeats() {
    List<Long> reads = LongStream.rangeClosed(1, 50).boxed().toList();
    List<Long> likes = LongStream.of(3, 100, 101).boxed().toList();

    List<Long> signals = InterestProfile.signalPostIds(reads, likes);

    assertThat(signals).hasSize(42).startsWith(1L, 2L, 3L).endsWith(40L, 100L, 101L);
  }

  @Test
  void readerLanguagesAreTheLocalePlusLanguagesTheyKeepReading() {
    assertThat(InterestProfile.languages("ko", List.of("ja", "ja", "ko", "en", "ko", "ko", "ko")))
        .containsExactlyInAnyOrder("ko", "ja");
  }

  @Test
  void oneStrayReadDoesNotAddALanguage() {
    assertThat(InterestProfile.languages("ko", List.of("ja"))).containsExactly("ko");
    assertThat(InterestProfile.languages(null, List.of())).isEmpty();
  }
}
