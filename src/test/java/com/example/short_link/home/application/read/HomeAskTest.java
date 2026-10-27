package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.home.application.read.HomeFeedQueryService.Tab;
import org.junit.jupiter.api.Test;

class HomeAskTest {

  private static final SeenViewer READER = SeenViewer.member(7L);

  private static HomeAsk asking(int limit) {
    return new HomeAsk(7L, READER, "ja", Tab.LATEST, "h1.2.3", "20.7", limit, false);
  }

  @Test
  void aPageIsOneToFortyLong() {
    assertThat(asking(0).size()).isEqualTo(1);
    assertThat(asking(-5).size()).isEqualTo(1);
    assertThat(asking(20).size()).isEqualTo(20);
    assertThat(asking(40).size()).isEqualTo(40);
    assertThat(asking(500).size()).isEqualTo(40);
  }

  @Test
  void aFreshAskIsTheSameTabFromTheTopHidingOnlyForWhomItIsAsked() {
    assertThat(asking(500).fresh(READER))
        .isEqualTo(new HomeAsk(7L, READER, "ja", Tab.LATEST, null, null, 500, true));
    assertThat(asking(500).fresh(null))
        .isEqualTo(new HomeAsk(7L, null, "ja", Tab.LATEST, null, null, 500, false));
  }

  @Test
  void aTabIsReadWithoutCaseOrDashesAndAnythingElseIsLatest() {
    assertThat(Tab.of("for-you")).isEqualTo(Tab.FOR_YOU);
    assertThat(Tab.of(" Following ")).isEqualTo(Tab.FOLLOWING);
    assertThat(Tab.of("TRENDING")).isEqualTo(Tab.TRENDING);
    assertThat(Tab.of("blog")).isEqualTo(Tab.BLOG);
    assertThat(Tab.of("for_you")).isEqualTo(Tab.FOR_YOU);
    assertThat(Tab.of("nonsense")).isEqualTo(Tab.LATEST);
    assertThat(Tab.of("")).isEqualTo(Tab.LATEST);
    assertThat(Tab.of(null)).isEqualTo(Tab.LATEST);
  }
}
