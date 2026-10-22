package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VisitorSketchTest {

  @Test
  void aVisitorSeenOnSeveralDaysAndAgainInTheRawClicksCountsOnce() {
    byte[] monday = VisitorSketch.of(List.of("a", "b", "c"));
    byte[] tuesday = VisitorSketch.of(List.of("b", "c", "d"));

    assertThat(VisitorSketch.count(List.of(monday, tuesday), List.of("d", "e", "a"))).isEqualTo(5);
  }

  @Test
  void manyVisitorsStayWithinTwoPercent() {
    List<String> first = IntStream.range(0, 30_000).mapToObj(i -> "v" + i).toList();
    List<String> second = IntStream.range(20_000, 50_000).mapToObj(i -> "v" + i).toList();

    long estimate =
        VisitorSketch.count(List.of(VisitorSketch.of(first), VisitorSketch.of(second)), List.of());

    assertThat(estimate).isBetween(49_000L, 51_000L);
  }
}
