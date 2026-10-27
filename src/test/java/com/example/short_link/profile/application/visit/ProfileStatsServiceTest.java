package com.example.short_link.profile.application.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.example.short_link.common.analytics.HeatmapCell;
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
import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.profile.domain.repository.ProfileVisitRepository;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileStatsServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private ProfileVisitRepository visits;

  private ProfileStatsService service;

  @BeforeEach
  void setUp() {
    service = new ProfileStatsService(userRepository, visits, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private UserEntity user(long id, String tz, boolean publicStats) {
    UserEntity u = new UserEntity("u@x", "google", "g-" + id);
    TestEntities.withId(u, id);
    if (tz != null) u.changeTimezone(tz);
    if (publicStats) u.updateStatsPublic(true);
    u.claimUsername("alice");
    return u;
  }

  @Test
  void statsForOwnerMissingThrows() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.statsForOwner(1L)).isInstanceOf(UserException.class);
  }

  @Test
  void publicStatsMissingUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.publicStats("ghost")).isInstanceOf(UserException.class);
  }

  @Test
  void publicStatsOptedOut404s() {
    UserEntity u = user(1L, "Asia/Seoul", false);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    assertThatThrownBy(() -> service.publicStats("alice")).isInstanceOf(UserException.class);
  }

  @Test
  void statsAggregatesAndPicksPeakHour() {
    UserEntity u = user(1L, "Asia/Seoul", false);
    stubVisits(1L);
    HourVisitRow hour1 = hourRow(0, 10L);
    HourVisitRow hour2 = hourRow(21, 99L);
    when(visits.findHourlyVisits(anyLong(), any(), any(), any())).thenReturn(List.of(hour1, hour2));
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    VisitStats out = service.statsForOwner(1L);
    assertThat(out.timezone()).isEqualTo("Asia/Seoul");
    assertThat(out.peakHour()).isEqualTo(21);
    assertThat(out.dailyVisits()).hasSize(1);
    assertThat(out.heatmap()).hasSize(1);
    assertThat(out.countryVisits()).hasSize(1);
    assertThat(out.deviceVisits()).hasSize(1);
    assertThat(out.browserVisits()).hasSize(1);
    assertThat(out.referrerHostVisits()).hasSize(1);
    assertThat(out.sourceChannelVisits()).hasSize(1);
    assertThat(out.utmCampaignVisits()).hasSize(1);
    assertThat(out.utmSourceVisits()).hasSize(1);
  }

  @Test
  void publicStatsOptedInComputes() {
    UserEntity u = user(1L, null, true);
    stubVisits(1L);
    when(visits.findHourlyVisits(anyLong(), any(), any(), any())).thenReturn(List.of());
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    VisitStats out = service.publicStats("alice");
    assertThat(out.peakHour()).isNull();
    assertThat(out.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void summaryReturnsAllBuckets() {
    when(visits.countByProfileUserIdAndBotFalse(7L)).thenReturn(100L);
    when(visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            7L, Instant.parse("2026-06-30T00:00:00Z")))
        .thenReturn(10L);
    when(visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            7L, Instant.parse("2026-06-24T00:00:00Z")))
        .thenReturn(30L);
    when(visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            7L, Instant.parse("2026-06-01T00:00:00Z")))
        .thenReturn(70L);
    ProfileVisitSummary s = service.summaryForOwner(7L);
    assertThat(s.allTime()).isEqualTo(100L);
    assertThat(s.today()).isEqualTo(10L);
    assertThat(s.week()).isEqualTo(30L);
    assertThat(s.month()).isEqualTo(70L);
  }

  @Test
  void invalidTimezoneFallsBackToSeoul() {
    UserEntity u = user(1L, "Not/A/Zone", false);
    stubVisits(1L);
    when(visits.findHourlyVisits(anyLong(), any(), any(), any())).thenReturn(List.of());
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    VisitStats out = service.statsForOwner(1L);
    assertThat(out.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void heatmapDayOfWeekMapsAllValues() {
    UserEntity u = user(1L, "Asia/Seoul", false);
    stubVisits(1L);
    when(visits.findHourlyVisits(anyLong(), any(), any(), any())).thenReturn(List.of());
    List<HeatmapVisitRow> rows =
        List.of(
            heat(1, 0, 1L),
            heat(2, 1, 2L),
            heat(3, 2, 3L),
            heat(4, 3, 4L),
            heat(5, 4, 5L),
            heat(6, 5, 6L),
            heat(7, 6, 7L),
            heat(99, 7, 8L));
    when(visits.findHeatmap(anyLong(), any(), any(), any())).thenReturn(rows);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    VisitStats out = service.statsForOwner(1L);
    assertThat(out.heatmap())
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
  void nullCountryFallsBackToUnknownLabel() {
    UserEntity u = user(1L, "Asia/Seoul", false);
    when(visits.countByProfileUserId(1L)).thenReturn(1L);
    when(visits.countByProfileUserIdAndBotFalse(1L)).thenReturn(1L);
    when(visits.countBotByProfileUserId(1L)).thenReturn(0L);
    when(visits.countUniqueVisitorsByProfileUserId(1L)).thenReturn(1L);
    when(visits.findFirstVisitAt(1L)).thenReturn(null);
    when(visits.findLastVisitAt(1L)).thenReturn(null);
    when(visits.findDailyVisits(anyLong(), any(Instant.class), any(Instant.class), any()))
        .thenReturn(List.of());
    when(visits.findHourlyVisits(anyLong(), any(), any(), any())).thenReturn(List.of());
    when(visits.findHeatmap(anyLong(), any(), any(), any())).thenReturn(List.of());
    CountryVisitRow nullCountry = country(null, 3L);
    DeviceVisitRow nullDevice = device(null, 3L);
    BrowserVisitRow nullBrowser = browser(null, 3L);
    when(visits.topCountries(anyLong(), anyInt())).thenReturn(List.of(nullCountry));
    when(visits.findDeviceVisits(anyLong())).thenReturn(List.of(nullDevice));
    when(visits.topBrowsers(anyLong(), anyInt())).thenReturn(List.of(nullBrowser));
    when(visits.topReferrerHosts(anyLong(), anyInt())).thenReturn(List.of());
    when(visits.topSourceChannels(anyLong(), anyInt())).thenReturn(List.of());
    when(visits.topUtmCampaigns(anyLong(), anyInt())).thenReturn(List.of());
    when(visits.topUtmSources(anyLong(), anyInt())).thenReturn(List.of());
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    VisitStats out = service.statsForOwner(1L);
    assertThat(out.countryVisits())
        .extracting(VisitStats.CountryVisit::country)
        .containsExactly("unknown");
    assertThat(out.deviceVisits())
        .extracting(VisitStats.DeviceVisit::device)
        .containsExactly("unknown");
    assertThat(out.browserVisits())
        .extracting(VisitStats.BrowserVisit::browser)
        .containsExactly("unknown");
  }

  @Test
  void statsReadTheOwnersOffsetAndWindowAtTheClocksInstant() {
    UserEntity u = user(1L, "America/New_York", false);
    DailyVisitRow day = mockDaily(LocalDate.of(2026, 6, 30), 4L);
    when(visits.findDailyVisits(
            1L,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:01Z"),
            "-04:00"))
        .thenReturn(List.of(day));
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    VisitStats out = service.statsForOwner(1L);

    assertThat(out.timezone()).isEqualTo("America/New_York");
    assertThat(out.dailyVisits())
        .containsExactly(new VisitStats.DailyVisit(LocalDate.of(2026, 6, 30), 4L));
  }

  @Test
  void aNewYorkOwnersStatsAreReadPerOffsetAndSummedAcrossTheFallBack() {
    Instant fallBack = Instant.parse("2026-11-01T06:00:00Z");
    Instant now = Instant.parse("2026-11-10T15:00:00Z");
    Instant until = Instant.parse("2026-11-10T15:00:01Z");
    Instant firstVisit = Instant.parse("2026-10-20T00:00:00Z");
    ProfileStatsService inNovember =
        new ProfileStatsService(userRepository, visits, Clock.fixed(now, ZoneOffset.UTC));
    UserEntity u = user(1L, "America/New_York", false);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    List<HourVisitRow> summerHours = List.of(hourRow(9, 2L));
    List<HourVisitRow> winterHours = List.of(hourRow(9, 1L), hourRow(10, 1L));
    List<HeatmapVisitRow> summerCells = List.of(heat(7, 0, 1L));
    List<HeatmapVisitRow> winterCells = List.of(heat(5, 9, 1L), heat(7, 0, 1L));
    when(visits.findFirstVisitAt(1L)).thenReturn(firstVisit);
    when(visits.findHourlyVisits(1L, firstVisit, fallBack, "-04:00")).thenReturn(summerHours);
    when(visits.findHourlyVisits(1L, fallBack, until, "-05:00")).thenReturn(winterHours);
    when(visits.findHeatmap(1L, firstVisit, fallBack, "-04:00")).thenReturn(summerCells);
    when(visits.findHeatmap(1L, fallBack, until, "-05:00")).thenReturn(winterCells);

    VisitStats out = inNovember.statsForOwner(1L);

    assertThat(out.hourVisits())
        .containsExactly(new VisitStats.HourVisit(9, 3L), new VisitStats.HourVisit(10, 1L));
    assertThat(out.heatmap())
        .containsExactly(new HeatmapCell("THURSDAY", 9, 1L), new HeatmapCell("SATURDAY", 0, 2L));
  }

  private void stubVisits(long uid) {
    DailyVisitRow daily = mockDaily(LocalDate.of(2024, 1, 1), 5L);
    HeatmapVisitRow heat = heat(2, 9, 5L);
    CountryVisitRow country = country("KR", 50L);
    DeviceVisitRow device = device("Desktop", 30L);
    BrowserVisitRow browser = browser("Chrome", 25L);
    ReferrerHostVisitRow refHost = refHost("twitter.com", 7L);
    SourceChannelVisitRow srcChannel = srcChannel("x", 7L);
    UtmCampaignVisitRow utmCampaign = utmCampaign("launch", 5L);
    UtmSourceVisitRow utmSource = utmSource("twitter", 5L);
    when(visits.countByProfileUserId(uid)).thenReturn(100L);
    when(visits.countByProfileUserIdAndBotFalse(uid)).thenReturn(80L);
    when(visits.countBotByProfileUserId(uid)).thenReturn(20L);
    when(visits.countUniqueVisitorsByProfileUserId(uid)).thenReturn(45L);
    when(visits.findFirstVisitAt(uid)).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
    when(visits.findLastVisitAt(uid)).thenReturn(Instant.parse("2024-01-02T00:00:00Z"));
    when(visits.findDailyVisits(anyLong(), any(Instant.class), any(Instant.class), any()))
        .thenReturn(List.of(daily));
    when(visits.findHeatmap(anyLong(), any(), any(), any())).thenReturn(List.of(heat));
    when(visits.topCountries(anyLong(), anyInt())).thenReturn(List.of(country));
    when(visits.findDeviceVisits(anyLong())).thenReturn(List.of(device));
    when(visits.topBrowsers(anyLong(), anyInt())).thenReturn(List.of(browser));
    when(visits.topReferrerHosts(anyLong(), anyInt())).thenReturn(List.of(refHost));
    when(visits.topSourceChannels(anyLong(), anyInt())).thenReturn(List.of(srcChannel));
    when(visits.topUtmCampaigns(anyLong(), anyInt())).thenReturn(List.of(utmCampaign));
    when(visits.topUtmSources(anyLong(), anyInt())).thenReturn(List.of(utmSource));
  }

  private static HourVisitRow hourRow(int hour, long count) {
    HourVisitRow row = Mockito.mock(HourVisitRow.class);
    when(row.getHour()).thenReturn(hour);
    when(row.getCount()).thenReturn(count);
    return row;
  }

  private static DailyVisitRow mockDaily(LocalDate d, long count) {
    DailyVisitRow row = Mockito.mock(DailyVisitRow.class);
    when(row.getDay()).thenReturn(d);
    when(row.getCount()).thenReturn(count);
    return row;
  }

  private static HeatmapVisitRow heat(int dow, int hour, long count) {
    HeatmapVisitRow r = Mockito.mock(HeatmapVisitRow.class);
    when(r.getDow()).thenReturn(dow);
    when(r.getHour()).thenReturn(hour);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static CountryVisitRow country(String name, long count) {
    CountryVisitRow r = Mockito.mock(CountryVisitRow.class);
    when(r.getCountry()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static DeviceVisitRow device(String name, long count) {
    DeviceVisitRow r = Mockito.mock(DeviceVisitRow.class);
    when(r.getDevice()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static BrowserVisitRow browser(String name, long count) {
    BrowserVisitRow r = Mockito.mock(BrowserVisitRow.class);
    when(r.getBrowser()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static ReferrerHostVisitRow refHost(String name, long count) {
    ReferrerHostVisitRow r = Mockito.mock(ReferrerHostVisitRow.class);
    when(r.getHost()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static SourceChannelVisitRow srcChannel(String name, long count) {
    SourceChannelVisitRow r = Mockito.mock(SourceChannelVisitRow.class);
    when(r.getSource()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static UtmCampaignVisitRow utmCampaign(String name, long count) {
    UtmCampaignVisitRow r = Mockito.mock(UtmCampaignVisitRow.class);
    when(r.getCampaign()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static UtmSourceVisitRow utmSource(String name, long count) {
    UtmSourceVisitRow r = Mockito.mock(UtmSourceVisitRow.class);
    when(r.getSource()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }
}
