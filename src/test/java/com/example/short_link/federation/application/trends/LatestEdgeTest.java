package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.trends.LatestPosts.Edge;
import org.junit.jupiter.api.Test;

class LatestEdgeTest {

  @Test
  void aServersFirstAnswerSetsBothEndsAndAnEmptyOneMarksItsBottom() {
    assertThat(Edge.firstAnswer("9", "5", false)).isEqualTo(new Edge("9", "5", false));
    assertThat(Edge.firstAnswer("9", "9", true)).isEqualTo(new Edge("9", "9", true));
    assertThat(Edge.firstAnswer(null, null, false)).isEqualTo(new Edge("0", "0", true));
  }

  @Test
  void aNewerPageMovesOnlyTheNewestEndAndAnEmptyOneMovesNothing() {
    Edge edge = new Edge("9", "5", false);

    assertThat(edge.afterNewer("12")).isEqualTo(new Edge("12", "5", false));
    assertThat(edge.afterNewer(null)).isSameAs(edge);
  }

  @Test
  void anOlderPageMovesOnlyTheOldestEndAndAShortOneIsTheBottom() {
    Edge edge = new Edge("9", "5", false);

    assertThat(edge.afterOlder("3", false)).isEqualTo(new Edge("9", "3", false));
    assertThat(edge.afterOlder("3", true)).isEqualTo(new Edge("9", "3", true));
    assertThat(edge.afterOlder(null, true)).isEqualTo(new Edge("9", "5", true));
  }
}
