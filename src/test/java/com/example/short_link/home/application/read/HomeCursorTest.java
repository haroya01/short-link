package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeCursorTest {

  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00.123Z");
  private static final Instant AS_OF = Instant.ofEpochMilli(1_792_404_000_123L);

  @Test
  void positionsReadAsTheNextPlaceInEachSourceAndAnythingElseStartsOver() {
    assertThat(HomeFeedQueryService.positions("7.2")).containsExactly(7, 2);
    assertThat(HomeFeedQueryService.positions("0.0")).containsExactly(0, 0);
    assertThat(HomeFeedQueryService.positions("-3.5")).containsExactly(0, 5);
    for (String bad : List.of("x.1", "1", "1.2.3", "", "h1.2.3", "r1.2", "b1.2", "1.")) {
      assertThat(HomeFeedQueryService.positions(bad)).as(bad).containsExactly(0, 0);
    }
    assertThat(HomeFeedQueryService.positions(null)).containsExactly(0, 0);
  }

  @Test
  void aLatestCursorKeepsHidingTheSeenOnlyWhenItsScrollDid() {
    assertThat(HomeFeedQueryService.at("h" + AS_OF.toEpochMilli() + ".3.4", false, NOW))
        .isEqualTo(new HomeFeedQueryService.At(AS_OF, 3, 4));
    assertThat(HomeFeedQueryService.at("3.4", true, NOW))
        .isEqualTo(new HomeFeedQueryService.At(null, 3, 4));
    assertThat(HomeFeedQueryService.at(null, true, NOW))
        .isEqualTo(new HomeFeedQueryService.At(NOW, 0, 0));
    assertThat(HomeFeedQueryService.at(null, false, NOW))
        .isEqualTo(new HomeFeedQueryService.At(null, 0, 0));
    assertThat(HomeFeedQueryService.at("x.4", true, NOW))
        .isEqualTo(new HomeFeedQueryService.At(null, 0, 0));
    for (String bad :
        List.of("h", "hx.1.2", "h1.2", "h1.x.2", "h-1.0.0", "h1.-1.0", "h1.0.-1", "h1.2.3.4")) {
      assertThat(HomeFeedQueryService.at(bad, false, NOW))
          .as(bad)
          .isEqualTo(new HomeFeedQueryService.At(NOW, 0, 0));
    }
  }

  @Test
  void aBlogMarkerReadsBackToTheSameStringAndAnythingElseIsNoMarker() {
    for (String marker : List.of("b1792404000000001.4", "b0.1", "b1792490400123456.9")) {
      assertThat(HomeFeedQueryService.Marker.parse(marker)).as(marker).hasToString(marker);
    }
    assertThat(HomeFeedQueryService.Marker.parse("b1792404000000001.4"))
        .isEqualTo(
            new HomeFeedQueryService.Marker(
                Instant.EPOCH.plus(1_792_404_000_000_001L, ChronoUnit.MICROS), 4));
    HomeFeedQueryService.Marker marker =
        new HomeFeedQueryService.Marker(Instant.parse("2026-10-20T10:00:00.123456Z"), 9L);
    assertThat(HomeFeedQueryService.Marker.parse(marker.toString())).isEqualTo(marker);
    for (String bad :
        List.of("b1.0", "b-1.2", "b1", "1.2", "bx.2", "b1.2.3", "b99999999999999999999.1", "")) {
      assertThat(HomeFeedQueryService.Marker.parse(bad)).as(bad).isNull();
    }
    assertThat(HomeFeedQueryService.Marker.parse(null)).isNull();
  }

  @Test
  void aRankedCursorKeepsItsRankingTimeAndWhetherItHides() {
    assertThat(HomeForYou.position("rh" + AS_OF.toEpochMilli() + ".4", NOW))
        .isEqualTo(new HomeForYou.Position(AS_OF, 4, true));
    assertThat(HomeForYou.position("r" + AS_OF.toEpochMilli() + ".40", NOW))
        .isEqualTo(new HomeForYou.Position(AS_OF, 40, false));
    assertThat(HomeForYou.position("rh123.4", NOW))
        .isEqualTo(new HomeForYou.Position(Instant.ofEpochMilli(123), 4, true));
    assertThat(HomeForYou.position("r0.0", NOW))
        .isEqualTo(new HomeForYou.Position(Instant.EPOCH, 0, false));
    for (String bad :
        List.of("h123.1.1", "r.4", "rh.4", "rx.4", "r123.-1", "r-5.1", "r123", "4.2", "r1.2.3")) {
      assertThat(HomeForYou.position(bad, NOW))
          .as(bad)
          .isEqualTo(new HomeForYou.Position(NOW, 0, null));
    }
    assertThat(HomeForYou.position(null, NOW)).isEqualTo(new HomeForYou.Position(NOW, 0, null));
  }
}
