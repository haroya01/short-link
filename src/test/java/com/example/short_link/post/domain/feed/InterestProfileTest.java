package com.example.short_link.post.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class InterestProfileTest {

  @Test
  void aFollowedTagOutweighsTagsSeenOnlyInReads() {
    List<String> tags =
        InterestProfile.topTags(
            List.of("Rust"), List.of(List.of("java"), List.of("java", "spring")), List.of());

    assertThat(tags).containsExactly("rust", "java", "spring");
  }

  @Test
  void spellingsThatDifferOnlyInCaseAreOneTag() {
    List<String> tags =
        InterestProfile.topTags(
            List.of("Java"), List.of(List.of("JAVA"), List.of("kotlin")), List.of());

    assertThat(tags).containsExactly("java", "kotlin");
  }

  @Test
  void hiddenTagsLeaveTheProfileWhateverTheirCase() {
    List<String> tags =
        InterestProfile.topTags(
            List.of("crypto", "ai"), List.of(List.of("Crypto")), List.of("CRYPTO"));

    assertThat(tags).containsExactly("ai");
  }

  @Test
  void keepsTwelveTagsAndBreaksTiesAlphabetically() {
    List<List<String>> reads =
        IntStream.range(0, 14).mapToObj(i -> List.of(String.format("t%02d", 13 - i))).toList();

    List<String> tags = InterestProfile.topTags(List.of(), reads, List.of());

    assertThat(tags)
        .hasSize(InterestProfile.MAX_TAGS)
        .containsExactly(
            "t00", "t01", "t02", "t03", "t04", "t05", "t06", "t07", "t08", "t09", "t10", "t11");
  }

  @Test
  void signalsAreTheNewestFortyReadsAndFortyLikesWithoutRepeats() {
    List<Long> reads = LongStream.rangeClosed(1, 50).boxed().toList();
    List<Long> likes = LongStream.of(3, 100, 101).boxed().toList();

    List<Long> signals = InterestProfile.signalPostIds(reads, likes);

    assertThat(signals).hasSize(42).startsWith(1L, 2L, 3L).endsWith(40L, 100L, 101L);
  }

  @Test
  void noSignalMeansNoProfile() {
    assertThat(InterestProfile.topTags(List.of(), List.of(), List.of("ai"))).isEmpty();
  }
}
