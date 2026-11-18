package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.home.application.read.HomeCursor.Blog;
import com.example.short_link.home.application.read.HomeCursor.Positions;
import com.example.short_link.home.application.read.HomeCursor.Ranked;
import com.example.short_link.post.application.read.BlogSlice;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeCursorTest {

  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00.123Z");
  private static final Instant AS_OF = Instant.ofEpochMilli(1_792_404_000_123L);

  @Test
  void positionsReadAsTheNextPlaceInEachSourceAndAnythingElseStartsOver() {
    assertThat(Positions.plain("7.2")).isEqualTo(new Positions(null, 7, 2));
    assertThat(Positions.plain("0.0")).isEqualTo(new Positions(null, 0, 0));
    assertThat(Positions.plain("-3.5")).isEqualTo(new Positions(null, 0, 5));
    for (String bad : List.of("x.1", "1", "1.2.3", "", "h1.2.3", "r1.2", "b1.2", "1.")) {
      assertThat(Positions.plain(bad)).as(bad).isEqualTo(new Positions(null, 0, 0));
    }
    assertThat(Positions.plain(null)).isEqualTo(new Positions(null, 0, 0));
  }

  @Test
  void aLatestCursorKeepsHidingTheSeenOnlyWhenItsScrollDid() {
    assertThat(Positions.read("h" + AS_OF.toEpochMilli() + ".3.4", false, NOW))
        .isEqualTo(new Positions(AS_OF, 3, 4));
    assertThat(Positions.read("3.4", true, NOW)).isEqualTo(new Positions(null, 3, 4));
    assertThat(Positions.read(null, true, NOW)).isEqualTo(new Positions(NOW, 0, 0));
    assertThat(Positions.read(null, false, NOW)).isEqualTo(new Positions(null, 0, 0));
    assertThat(Positions.read("x.4", true, NOW)).isEqualTo(new Positions(null, 0, 0));
    for (String bad :
        List.of("h", "hx.1.2", "h1.2", "h1.x.2", "h-1.0.0", "h1.-1.0", "h1.0.-1", "h1.2.3.4")) {
      assertThat(Positions.read(bad, false, NOW)).as(bad).isEqualTo(new Positions(NOW, 0, 0));
    }
  }

  @Test
  void positionsReadBackToTheSameString() {
    for (String cursor : List.of("7.2", "0.0", "40.13")) {
      assertThat(Positions.plain(cursor)).as(cursor).hasToString(cursor);
      assertThat(Positions.read(cursor, true, NOW)).as(cursor).hasToString(cursor);
    }
    for (String cursor : List.of("h" + AS_OF.toEpochMilli() + ".3.4", "h0.0.0", "h123.1.0")) {
      assertThat(Positions.read(cursor, false, NOW)).as(cursor).hasToString(cursor);
    }
    assertThat(new Positions(AS_OF, 0, 0).next(1, 1))
        .hasToString("h" + AS_OF.toEpochMilli() + ".1.1");
    assertThat(Positions.START.next(2, 0)).hasToString("2.0");
  }

  @Test
  void aBlogMarkerReadsBackToTheSameStringAndAnythingElseIsNoMarker() {
    for (String marker : List.of("b1792404000000001.4", "b0.1", "b1792490400123456.9")) {
      assertThat(Blog.parse(marker)).as(marker).hasToString(marker);
    }
    assertThat(Blog.parse("b1792404000000001.4"))
        .isEqualTo(
            new Blog(
                Instant.EPOCH.plus(1_792_404_000_000_001L, ChronoUnit.MICROS), 4, null, false));
    Blog marker = new Blog(Instant.parse("2026-10-20T10:00:00.123456Z"), 9L, null, false);
    assertThat(Blog.parse(marker.toString())).isEqualTo(marker);
    for (String bad :
        List.of("b1.0", "b-1.2", "b1", "1.2", "bx.2", "b1.2.3", "b99999999999999999999.1", "")) {
      assertThat(Blog.parse(bad)).as(bad).isNull();
    }
    assertThat(Blog.parse(null)).isNull();
  }

  @Test
  void aBlogPlaceForAReaderWithALanguageCarriesItAndWhetherItIsAmongTheRest() {
    Instant at = Instant.EPOCH.plus(1_792_404_000_000_001L, ChronoUnit.MICROS);
    assertThat(Blog.parse("b1-ko.1792404000000001.4")).isEqualTo(new Blog(at, 4, "ko", false));
    assertThat(Blog.parse("b2-ja.1792404000000001.4")).isEqualTo(new Blog(at, 4, "ja", true));
    for (String cursor :
        List.of("b1-ko.1792404000000001.4", "b2-ja.1792404000000001.4", "b2-en.0.1")) {
      assertThat(Blog.parse(cursor)).as(cursor).hasToString(cursor);
    }
    assertThat(Blog.parse("b1792404000000001.4").language()).isNull();
    assertThat(Blog.parse("b1792404000000001.4").rest()).isFalse();
    for (String bad :
        List.of(
            "b1-.1.2",
            "b1-ko-.1.2",
            "b1-ko-ja.1.2",
            "b3-ko.1.2",
            "b-ko.1.2",
            "b1-KO.1.2",
            "b1-k.1.2",
            "b1-toolonglang.1.2",
            "b1-ko.1.0",
            "b1-ko.-1.2",
            "b1-ko.x.2",
            "b1-ko.1.2.3",
            "1-ko.1.2")) {
      assertThat(Blog.parse(bad)).as(bad).isNull();
    }
  }

  @Test
  void aBlogTakesOnlyAPlainLanguageTagAndANewestAmongTheRestIsAPlainMarker() {
    Instant at = Instant.EPOCH.plus(5, ChronoUnit.MICROS);
    assertThat(Blog.language("ko")).isEqualTo("ko");
    for (String other : List.of("zh.tw", "KO", "x", "a-b", "")) {
      assertThat(Blog.language(other)).as(other).isNull();
    }
    assertThat(Blog.language(null)).isNull();
    assertThat(Blog.newest(new BlogSlice.Place(at, 3, false), "ko")).hasToString("b1-ko.5.3");
    assertThat(Blog.newest(new BlogSlice.Place(at, 3, true), "ko")).hasToString("b5.3");
    assertThat(Blog.of(new BlogSlice.Place(at, 3, true), "ko")).hasToString("b2-ko.5.3");
    assertThat(Blog.of(new BlogSlice.Place(at, 3, false), null)).hasToString("b5.3");
    assertThat(Blog.of(null, "ko")).isNull();
    assertThat(Blog.newest(null, "ko")).isNull();
  }

  @Test
  void aRankedCursorKeepsItsRankingTimeAndWhetherItHides() {
    assertThat(Ranked.parse("rh" + AS_OF.toEpochMilli() + ".4"))
        .isEqualTo(new Ranked(AS_OF, 4, true));
    assertThat(Ranked.parse("r" + AS_OF.toEpochMilli() + ".40"))
        .isEqualTo(new Ranked(AS_OF, 40, false));
    assertThat(Ranked.parse("rh123.4")).isEqualTo(new Ranked(Instant.ofEpochMilli(123), 4, true));
    assertThat(Ranked.parse("r0.0")).isEqualTo(new Ranked(Instant.EPOCH, 0, false));
    for (String bad :
        List.of("h123.1.1", "r.4", "rh.4", "rx.4", "r123.-1", "r-5.1", "r123", "4.2", "r1.2.3")) {
      assertThat(Ranked.parse(bad)).as(bad).isNull();
    }
    assertThat(Ranked.parse(null)).isNull();
  }

  @Test
  void aRankedCursorReadsBackToTheSameString() {
    for (String cursor :
        List.of("rh" + AS_OF.toEpochMilli() + ".4", "r" + AS_OF.toEpochMilli() + ".40", "r0.0")) {
      assertThat(Ranked.parse(cursor)).as(cursor).hasToString(cursor);
    }
  }

  @Test
  void aSinceMarkerIsTheNewestNoteAndPostIdAndAnythingElseIsNone() {
    assertThat(SinceMarker.parse("20.7")).isEqualTo(new SinceMarker(20, 7));
    assertThat(SinceMarker.parse("-1.5")).isEqualTo(new SinceMarker(-1, 5));
    for (String marker : List.of("20.7", "0.0", "100.500", "-1.5")) {
      assertThat(SinceMarker.parse(marker)).as(marker).hasToString(marker);
    }
    for (String bad : List.of("x.7", "20", "20.7.1", "", "b1.2", "h1.2.3", "1.")) {
      assertThat(SinceMarker.parse(bad)).as(bad).isNull();
    }
    assertThat(SinceMarker.parse(null)).isNull();
    assertThat(SinceMarker.NONE).hasToString("0.0");
  }
}
