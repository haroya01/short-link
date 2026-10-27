package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.common.analytics.VisitProjections;
import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadStatsReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostReadStatsServiceTest {

  private static final long USER = 7L;
  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

  @Mock private PostReadStatsReader reader;
  @Mock private PostRepository postRepository;
  @Mock private SeriesOwnership seriesOwnership;
  @Mock private UserAccessLookup users;

  private PostReadStatsService service;

  @BeforeEach
  void setUp() {
    service =
        new PostReadStatsService(
            reader, postRepository, seriesOwnership, users, Clock.fixed(NOW, ZoneOffset.UTC));
    when(users.timezone(USER)).thenReturn(Optional.of(""));
  }

  private PostEntity ownedPost() {
    return new PostEntity(USER, "p", "P", "ko");
  }

  @Test
  void forPostAggregatesWhenOwned() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(reader.countViews(any())).thenReturn(120L);
    when(reader.countHuman(any())).thenReturn(100L);
    when(reader.countBot(any())).thenReturn(20L);
    when(reader.countUnique(any())).thenReturn(80L);

    VisitStats stats = service.forPost(USER, 1L);

    assertThat(stats.totalVisits()).isEqualTo(120L);
    assertThat(stats.humanVisits()).isEqualTo(100L);
    assertThat(stats.botVisits()).isEqualTo(20L);
    assertThat(stats.uniqueVisits()).isEqualTo(80L);
    assertThat(stats.timezone()).isEqualTo("Asia/Seoul");
    // Unstubbed list queries default to empty (Mockito) → stable empty breakdowns, not null.
    assertThat(stats.countryVisits()).isEmpty();
    assertThat(stats.dailyVisits()).isEmpty();
  }

  private static VisitProjections.HeatmapVisitRow heat(int dow) {
    return new VisitProjections.HeatmapVisitRow() {
      @Override
      public Integer getDow() {
        return dow;
      }

      @Override
      public Integer getHour() {
        return 9;
      }

      @Override
      public Long getCount() {
        return 1L;
      }
    };
  }

  @Test
  void forPostMapsDimensions() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    VisitProjections.HourVisitRow h = mock(VisitProjections.HourVisitRow.class);
    when(h.getHour()).thenReturn(21);
    when(h.getCount()).thenReturn(50L);
    when(reader.hourly(any(), any(), any(), any())).thenReturn(List.of(h));
    // dow 0 (default→UNKNOWN) + 1..7 covers every dayOfWeekName branch.
    when(reader.heatmap(any(), any(), any(), any()))
        .thenReturn(
            List.of(heat(0), heat(1), heat(2), heat(3), heat(4), heat(5), heat(6), heat(7)));
    VisitProjections.CountryVisitRow c = mock(VisitProjections.CountryVisitRow.class);
    when(c.getCountry()).thenReturn(null);
    when(c.getCount()).thenReturn(7L);
    when(reader.topCountries(any(), anyInt())).thenReturn(List.of(c));

    VisitStats stats = service.forPost(USER, 1L);

    assertThat(stats.peakHour()).isEqualTo(21);
    assertThat(stats.heatmap())
        .extracting(HeatmapCell::dayOfWeek)
        .containsExactly(
            "UNKNOWN",
            "SUNDAY",
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY");
    assertThat(stats.countryVisits().get(0).country()).isEqualTo("unknown");
  }

  @Test
  void forPostReadsTheOwnersOffsetAndWindowAtTheClocksInstant() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(users.timezone(USER)).thenReturn(Optional.of("America/New_York"));
    VisitProjections.DailyVisitRow day = mock(VisitProjections.DailyVisitRow.class);
    when(day.getDay()).thenReturn(LocalDate.of(2026, 6, 30));
    when(day.getCount()).thenReturn(4L);
    when(reader.daily(
            List.of(1L),
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:01Z"),
            "-04:00"))
        .thenReturn(List.of(day));

    VisitStats stats = service.forPost(USER, 1L);

    assertThat(stats.timezone()).isEqualTo("America/New_York");
    assertThat(stats.dailyVisits())
        .containsExactly(new VisitStats.DailyVisit(LocalDate.of(2026, 6, 30), 4L));
  }

  @Test
  void aNewYorkAuthorsStatsAreReadPerOffsetAndSummedAcrossTheFallBack() {
    Instant fallBack = Instant.parse("2026-11-01T06:00:00Z");
    Instant now = Instant.parse("2026-11-10T15:00:00Z");
    Instant until = Instant.parse("2026-11-10T15:00:01Z");
    Instant firstView = Instant.parse("2026-10-20T00:00:00Z");
    PostReadStatsService inNovember =
        new PostReadStatsService(
            reader, postRepository, seriesOwnership, users, Clock.fixed(now, ZoneOffset.UTC));
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(users.timezone(USER)).thenReturn(Optional.of("America/New_York"));
    when(reader.firstViewedAt(List.of(1L))).thenReturn(firstView);
    when(reader.daily(List.of(1L), Instant.parse("2026-10-11T15:00:00Z"), fallBack, "-04:00"))
        .thenReturn(List.of(new Day(LocalDate.of(2026, 11, 1), 1L)));
    when(reader.daily(List.of(1L), fallBack, until, "-05:00"))
        .thenReturn(
            List.of(
                new Day(LocalDate.of(2026, 11, 1), 2L), new Day(LocalDate.of(2026, 11, 5), 1L)));
    when(reader.hourly(List.of(1L), firstView, fallBack, "-04:00"))
        .thenReturn(List.of(new Hour(9, 2L)));
    when(reader.hourly(List.of(1L), fallBack, until, "-05:00"))
        .thenReturn(List.of(new Hour(9, 1L), new Hour(10, 1L)));

    VisitStats stats = inNovember.forPost(USER, 1L);

    assertThat(stats.dailyVisits())
        .containsExactly(
            new VisitStats.DailyVisit(LocalDate.of(2026, 11, 1), 3L),
            new VisitStats.DailyVisit(LocalDate.of(2026, 11, 5), 1L));
    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(9, 3L), new VisitStats.HourVisit(10, 1L));
    assertThat(stats.peakHour()).isEqualTo(9);
  }

  record Day(LocalDate getDay, Long getCount) implements VisitProjections.DailyVisitRow {}

  record Hour(Integer getHour, Long getCount) implements VisitProjections.HourVisitRow {}

  @Test
  void forPostFallsBackToDefaultTimezoneWhenUserMissing() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(ownedPost()));
    when(users.timezone(USER)).thenReturn(Optional.empty());

    VisitStats stats = service.forPost(USER, 1L);

    assertThat(stats.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void forPostDeniesWhenNotOwned() {
    PostEntity other = new PostEntity(999L, "p", "P", "ko");
    when(postRepository.findById(1L)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.forPost(USER, 1L)).isInstanceOf(PostException.class);
    verify(reader, never()).countViews(any());
  }

  @Test
  void forPostThrowsWhenPostMissing() {
    when(postRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.forPost(USER, 1L)).isInstanceOf(PostException.class);
  }

  @Test
  void forSeriesAggregatesAcrossMembers() {
    PostEntity m1 = mock(PostEntity.class);
    PostEntity m2 = mock(PostEntity.class);
    when(m1.getId()).thenReturn(11L);
    when(m2.getId()).thenReturn(12L);
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of(m1, m2));
    when(reader.countViews(List.of(11L, 12L))).thenReturn(300L);

    VisitStats stats = service.forSeries(USER, 5L);

    verify(seriesOwnership).requireOwned(USER, 5L);
    assertThat(stats.totalVisits()).isEqualTo(300L);
  }

  @Test
  void forSeriesEmptyWhenNoMembers() {
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(5L)).thenReturn(List.of());

    VisitStats stats = service.forSeries(USER, 5L);

    verify(seriesOwnership).requireOwned(USER, 5L);
    assertThat(stats.totalVisits()).isZero();
    assertThat(stats.countryVisits()).isEmpty();
    // Empty member set must not hit the reader (SQL IN () would be invalid).
    verify(reader, never()).countViews(any());
    verify(reader, never()).daily(any(), any(), any(), any());
  }
}
