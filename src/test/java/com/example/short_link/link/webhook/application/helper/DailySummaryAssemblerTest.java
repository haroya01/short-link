package com.example.short_link.link.webhook.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailySummaryAssemblerTest {

  private final ClickRangeReadRepository ranges = mock(ClickRangeReadRepository.class);
  private final ClickAlertReadRepository alerts = mock(ClickAlertReadRepository.class);
  private final DailySummaryAssembler assembler = new DailySummaryAssembler(ranges, alerts);
  private final ZoneId tz = ZoneId.of("Asia/Seoul");
  private final LocalDate day = LocalDate.of(2026, 5, 25);

  @BeforeEach
  void defaults() {
    lenient()
        .when(ranges.countHumanByLinkIdAndRange(anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(0L);
    lenient()
        .when(ranges.countBotByLinkIdAndRange(anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(0L);
    lenient()
        .when(
            ranges.countUniqueVisitorsByLinkIdAndRange(
                anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(0L);
    lenient()
        .when(
            alerts.findTopChannelByLinkIdAndRange(
                anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            alerts.findTopCountryByLinkIdAndRange(
                anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            alerts.findTopDeviceByLinkIdAndRange(anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            alerts.findPeakHourByLinkIdAndRange(
                anyLong(), any(Instant.class), any(Instant.class), anyString()))
        .thenReturn(Optional.empty());
  }

  private Instant dayStart(LocalDate d) {
    return d.atStartOfDay(tz).toInstant();
  }

  @Test
  void totalIsHumanPlusBot() {
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(130L);
    when(ranges.countBotByLinkIdAndRange(eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(12L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.totalClicks()).isEqualTo(142);
  }

  @Test
  void humanCountFlowsThrough() {
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(130L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.humanClicks()).isEqualTo(130);
  }

  @Test
  void uniqueVisitorsFlowsThrough() {
    when(ranges.countUniqueVisitorsByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(88L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.uniqueVisitors()).isEqualTo(88);
  }

  @Test
  void topChannelComesFromFirstRow() {
    SourceChannelClickRow row = mock(SourceChannelClickRow.class);
    when(row.getSource()).thenReturn("twitter");
    when(alerts.findTopChannelByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(Optional.of(row));

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.topChannel()).isEqualTo("twitter");
  }

  @Test
  void topChannelNullWhenNoRows() {
    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.topChannel()).isNull();
  }

  @Test
  void topCountryComesFromFirstRow() {
    CountryClickRow row = mock(CountryClickRow.class);
    when(row.getCountry()).thenReturn("KR");
    when(alerts.findTopCountryByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(Optional.of(row));

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.topCountry()).isEqualTo("KR");
  }

  @Test
  void topDeviceComesFromFirstRow() {
    DeviceClickRow row = mock(DeviceClickRow.class);
    when(row.getDevice()).thenReturn("Mobile");
    when(alerts.findTopDeviceByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(Optional.of(row));

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.topDevice()).isEqualTo("Mobile");
  }

  @Test
  void peakHourComesFromFirstRow() {
    HourClickRow row = mock(HourClickRow.class);
    when(row.getHour()).thenReturn(21);
    when(row.getCount()).thenReturn(24L);
    when(alerts.findPeakHourByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1))), eq("Asia/Seoul")))
        .thenReturn(Optional.of(row));

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.peakHour()).isEqualTo(21);
  }

  @Test
  void peakHourZeroWhenNoRows() {
    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.peakHour()).isZero();
  }

  @Test
  void vsYesterdayPositiveWhenGrew() {
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(120L);
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day.minusDays(1))), eq(dayStart(day))))
        .thenReturn(100L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.vsYesterday()).isCloseTo(0.20, within(1e-9));
  }

  @Test
  void vsYesterdayNullWhenYesterdayZero() {
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(50L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.vsYesterday()).isNull();
  }

  @Test
  void vs7DayAvgComparesAgainstAverage() {
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day)), eq(dayStart(day.plusDays(1)))))
        .thenReturn(70L);
    when(ranges.countHumanByLinkIdAndRange(
            eq(1L), eq(dayStart(day.minusDays(7))), eq(dayStart(day))))
        .thenReturn(700L);

    DailySummaryPayload p = assembler.assemble(1L, "abc", day, tz);

    assertThat(p.vs7DayAvg()).isCloseTo(-0.30, within(1e-9));
  }

  @Test
  void ratioReturnsNullOnZeroBaseline() {
    assertThat(DailySummaryAssembler.ratio(10L, 0L)).isNull();
  }
}
