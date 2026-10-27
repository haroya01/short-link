package com.example.short_link.common.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.analytics.VisitProjections.BrowserVisitRow;
import com.example.short_link.common.analytics.VisitProjections.CountryVisitRow;
import com.example.short_link.common.analytics.VisitProjections.DailyVisitRow;
import com.example.short_link.common.analytics.VisitProjections.DeviceVisitRow;
import com.example.short_link.common.analytics.VisitProjections.HeatmapVisitRow;
import com.example.short_link.common.analytics.VisitProjections.HourVisitRow;
import com.example.short_link.common.analytics.VisitProjections.ReferrerHostVisitRow;
import com.example.short_link.common.analytics.VisitProjections.SourceChannelVisitRow;
import com.example.short_link.common.analytics.VisitProjections.UtmCampaignVisitRow;
import com.example.short_link.common.analytics.VisitProjections.UtmSourceVisitRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisitStatsAssemblerTest {

  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private static final Instant FIRST = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant LAST = Instant.parse("2026-06-30T12:00:00Z");
  private static final VisitStatsAssembler.Totals TOTALS =
      new VisitStatsAssembler.Totals(12, 9, 3, 5, FIRST, LAST);

  record Daily(LocalDate getDay, Long getCount) implements DailyVisitRow {}

  record Hour(Integer getHour, Long getCount) implements HourVisitRow {}

  record Heat(Integer getDow, Integer getHour, Long getCount) implements HeatmapVisitRow {}

  record Country(String getCountry, Long getCount) implements CountryVisitRow {}

  record Device(String getDevice, Long getCount) implements DeviceVisitRow {}

  record Browser(String getBrowser, Long getCount) implements BrowserVisitRow {}

  record ReferrerHost(String getHost, Long getCount) implements ReferrerHostVisitRow {}

  record SourceChannel(String getSource, Long getCount) implements SourceChannelVisitRow {}

  record UtmCampaign(String getCampaign, Long getCount) implements UtmCampaignVisitRow {}

  record UtmSource(String getSource, Long getCount) implements UtmSourceVisitRow {}

  private static VisitStatsAssembler.Rows noRows() {
    return new VisitStatsAssembler.Rows(
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of());
  }

  @Test
  void carriesTheZoneAndTotalsAcross() {
    VisitStats stats = VisitStatsAssembler.assemble(TOKYO, TOTALS, noRows());

    assertThat(stats.timezone()).isEqualTo("Asia/Tokyo");
    assertThat(stats.totalVisits()).isEqualTo(12);
    assertThat(stats.humanVisits()).isEqualTo(9);
    assertThat(stats.botVisits()).isEqualTo(3);
    assertThat(stats.uniqueVisits()).isEqualTo(5);
    assertThat(stats.firstVisitAt()).isEqualTo(FIRST);
    assertThat(stats.lastVisitAt()).isEqualTo(LAST);
    assertThat(stats.peakHour()).isNull();
    assertThat(stats.dailyVisits()).isEmpty();
    assertThat(stats.utmSourceVisits()).isEmpty();
  }

  @Test
  void mapsEveryRowInOrder() {
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            List.of(
                new Daily(LocalDate.of(2026, 6, 29), 2L), new Daily(LocalDate.of(2026, 6, 30), 1L)),
            List.of(new Hour(8, 1L), new Hour(21, 2L)),
            List.of(new Heat(2, 21, 2L)),
            List.of(new Country("JP", 3L)),
            List.of(new Device("mobile", 3L)),
            List.of(new Browser("Safari", 3L)),
            List.of(new ReferrerHost("x.com", 2L)),
            List.of(new SourceChannel("social", 2L)),
            List.of(new UtmCampaign("launch", 1L)),
            List.of(new UtmSource("newsletter", 1L)));

    VisitStats stats = VisitStatsAssembler.assemble(TOKYO, TOTALS, rows);

    assertThat(stats.dailyVisits())
        .containsExactly(
            new VisitStats.DailyVisit(LocalDate.of(2026, 6, 29), 2L),
            new VisitStats.DailyVisit(LocalDate.of(2026, 6, 30), 1L));
    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(8, 1L), new VisitStats.HourVisit(21, 2L));
    assertThat(stats.heatmap()).containsExactly(new HeatmapCell("MONDAY", 21, 2L));
    assertThat(stats.countryVisits()).containsExactly(new VisitStats.CountryVisit("JP", 3L));
    assertThat(stats.deviceVisits()).containsExactly(new VisitStats.DeviceVisit("mobile", 3L));
    assertThat(stats.browserVisits()).containsExactly(new VisitStats.BrowserVisit("Safari", 3L));
    assertThat(stats.referrerHostVisits())
        .containsExactly(new VisitStats.ReferrerHostVisit("x.com", 2L));
    assertThat(stats.sourceChannelVisits())
        .containsExactly(new VisitStats.SourceChannelVisit("social", 2L));
    assertThat(stats.utmCampaignVisits())
        .containsExactly(new VisitStats.UtmCampaignVisit("launch", 1L));
    assertThat(stats.utmSourceVisits())
        .containsExactly(new VisitStats.UtmSourceVisit("newsletter", 1L));
  }

  @Test
  void thePeakHourIsTheBusiestAndTheEarliestOnATie() {
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            List.of(),
            List.of(new Hour(3, 5L), new Hour(9, 7L), new Hour(15, 7L)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertThat(VisitStatsAssembler.assemble(TOKYO, TOTALS, rows).peakHour()).isEqualTo(9);
  }

  @Test
  void namesSqlDaysOfWeekFromSunday() {
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            List.of(),
            List.of(),
            List.of(
                new Heat(1, 0, 1L),
                new Heat(2, 0, 1L),
                new Heat(3, 0, 1L),
                new Heat(4, 0, 1L),
                new Heat(5, 0, 1L),
                new Heat(6, 0, 1L),
                new Heat(7, 0, 1L),
                new Heat(0, 0, 1L)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertThat(VisitStatsAssembler.assemble(TOKYO, TOTALS, rows).heatmap())
        .extracting(HeatmapCell::dayOfWeek)
        .containsExactly(
            "SUNDAY",
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "UNKNOWN");
  }

  @Test
  void labelsMissingCountryDeviceAndBrowserUnknownButLeavesTheOthersNull() {
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            List.of(),
            List.of(),
            List.of(),
            List.of(new Country(null, 1L)),
            List.of(new Device(null, 1L)),
            List.of(new Browser(null, 1L)),
            List.of(new ReferrerHost(null, 1L)),
            List.of(new SourceChannel(null, 1L)),
            List.of(new UtmCampaign(null, 1L)),
            List.of(new UtmSource(null, 1L)));

    VisitStats stats = VisitStatsAssembler.assemble(TOKYO, TOTALS, rows);

    assertThat(stats.countryVisits()).containsExactly(new VisitStats.CountryVisit("unknown", 1L));
    assertThat(stats.deviceVisits()).containsExactly(new VisitStats.DeviceVisit("unknown", 1L));
    assertThat(stats.browserVisits()).containsExactly(new VisitStats.BrowserVisit("unknown", 1L));
    assertThat(stats.referrerHostVisits())
        .containsExactly(new VisitStats.ReferrerHostVisit(null, 1L));
    assertThat(stats.sourceChannelVisits())
        .containsExactly(new VisitStats.SourceChannelVisit(null, 1L));
    assertThat(stats.utmCampaignVisits())
        .containsExactly(new VisitStats.UtmCampaignVisit(null, 1L));
    assertThat(stats.utmSourceVisits()).containsExactly(new VisitStats.UtmSourceVisit(null, 1L));
  }
}
