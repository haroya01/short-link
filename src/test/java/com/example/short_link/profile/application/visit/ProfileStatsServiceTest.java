package com.example.short_link.profile.application.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileStatsServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ProfileVisitRepository visits;

  private ProfileStatsService service;

  @BeforeEach
  void setUp() {
    service = new ProfileStatsService(userRepository, visits);
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
    HourClickRow hour1 = hourRow(0, 10L);
    HourClickRow hour2 = hourRow(21, 99L);
    when(visits.findHourlyVisits(anyLong(), any())).thenReturn(List.of(hour1, hour2));
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    ProfileStats out = service.statsForOwner(1L);
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
    when(visits.findHourlyVisits(anyLong(), any())).thenReturn(List.of());
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    ProfileStats out = service.publicStats("alice");
    assertThat(out.peakHour()).isNull();
    assertThat(out.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void summaryReturnsAllBuckets() {
    when(visits.countByProfileUserIdAndBotFalse(7L)).thenReturn(100L);
    when(visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(anyLong(), any(Instant.class)))
        .thenReturn(10L)
        .thenReturn(30L)
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
    when(visits.findHourlyVisits(anyLong(), any())).thenReturn(List.of());
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    ProfileStats out = service.statsForOwner(1L);
    assertThat(out.timezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void heatmapDayOfWeekMapsAllValues() {
    UserEntity u = user(1L, "Asia/Seoul", false);
    stubVisits(1L);
    when(visits.findHourlyVisits(anyLong(), any())).thenReturn(List.of());
    List<HeatmapRow> rows =
        List.of(
            heat(1, 0, 1L),
            heat(2, 1, 2L),
            heat(3, 2, 3L),
            heat(4, 3, 4L),
            heat(5, 4, 5L),
            heat(6, 5, 6L),
            heat(7, 6, 7L),
            heat(99, 7, 8L));
    when(visits.findHeatmap(anyLong(), any())).thenReturn(rows);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    ProfileStats out = service.statsForOwner(1L);
    assertThat(out.heatmap())
        .extracting(ProfileStats.HeatmapCell::dayOfWeek)
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
    when(visits.findDailyVisits(anyLong(), any(Instant.class), any())).thenReturn(List.of());
    when(visits.findHourlyVisits(anyLong(), any())).thenReturn(List.of());
    when(visits.findHeatmap(anyLong(), any())).thenReturn(List.of());
    CountryClickRow nullCountry = country(null, 3L);
    DeviceClickRow nullDevice = device(null, 3L);
    BrowserClickRow nullBrowser = browser(null, 3L);
    when(visits.findCountryVisits(anyLong(), any(Pageable.class))).thenReturn(List.of(nullCountry));
    when(visits.findDeviceVisits(anyLong())).thenReturn(List.of(nullDevice));
    when(visits.findBrowserVisits(anyLong(), any(Pageable.class))).thenReturn(List.of(nullBrowser));
    when(visits.findReferrerHostVisits(anyLong(), any(Pageable.class))).thenReturn(List.of());
    when(visits.findSourceChannelVisits(anyLong(), any(Pageable.class))).thenReturn(List.of());
    when(visits.findUtmCampaignVisits(anyLong(), any(Pageable.class))).thenReturn(List.of());
    when(visits.findUtmSourceVisits(anyLong(), any(Pageable.class))).thenReturn(List.of());
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    ProfileStats out = service.statsForOwner(1L);
    assertThat(out.countryVisits())
        .extracting(ProfileStats.CountryVisit::country)
        .containsExactly("unknown");
    assertThat(out.deviceVisits())
        .extracting(ProfileStats.DeviceVisit::device)
        .containsExactly("unknown");
    assertThat(out.browserVisits())
        .extracting(ProfileStats.BrowserVisit::browser)
        .containsExactly("unknown");
  }

  private void stubVisits(long uid) {
    DailyClickRow daily = mockDaily(LocalDate.of(2024, 1, 1), 5L);
    HeatmapRow heat = heat(2, 9, 5L);
    CountryClickRow country = country("KR", 50L);
    DeviceClickRow device = device("Desktop", 30L);
    BrowserClickRow browser = browser("Chrome", 25L);
    ReferrerHostClickRow refHost = refHost("twitter.com", 7L);
    SourceChannelClickRow srcChannel = srcChannel("x", 7L);
    UtmCampaignClickRow utmCampaign = utmCampaign("launch", 5L);
    UtmSourceClickRow utmSource = utmSource("twitter", 5L);
    when(visits.countByProfileUserId(uid)).thenReturn(100L);
    when(visits.countByProfileUserIdAndBotFalse(uid)).thenReturn(80L);
    when(visits.countBotByProfileUserId(uid)).thenReturn(20L);
    when(visits.countUniqueVisitorsByProfileUserId(uid)).thenReturn(45L);
    when(visits.findFirstVisitAt(uid)).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
    when(visits.findLastVisitAt(uid)).thenReturn(Instant.parse("2024-01-02T00:00:00Z"));
    when(visits.findDailyVisits(anyLong(), any(Instant.class), any())).thenReturn(List.of(daily));
    when(visits.findHeatmap(anyLong(), any())).thenReturn(List.of(heat));
    when(visits.findCountryVisits(anyLong(), any(Pageable.class))).thenReturn(List.of(country));
    when(visits.findDeviceVisits(anyLong())).thenReturn(List.of(device));
    when(visits.findBrowserVisits(anyLong(), any(Pageable.class))).thenReturn(List.of(browser));
    when(visits.findReferrerHostVisits(anyLong(), any(Pageable.class)))
        .thenReturn(List.of(refHost));
    when(visits.findSourceChannelVisits(anyLong(), any(Pageable.class)))
        .thenReturn(List.of(srcChannel));
    when(visits.findUtmCampaignVisits(anyLong(), any(Pageable.class)))
        .thenReturn(List.of(utmCampaign));
    when(visits.findUtmSourceVisits(anyLong(), any(Pageable.class))).thenReturn(List.of(utmSource));
  }

  private static HourClickRow hourRow(int hour, long count) {
    HourClickRow row = org.mockito.Mockito.mock(HourClickRow.class);
    when(row.getHour()).thenReturn(hour);
    when(row.getCount()).thenReturn(count);
    return row;
  }

  private static DailyClickRow mockDaily(LocalDate d, long count) {
    DailyClickRow row = org.mockito.Mockito.mock(DailyClickRow.class);
    when(row.getDay()).thenReturn(d);
    when(row.getCount()).thenReturn(count);
    return row;
  }

  private static HeatmapRow heat(int dow, int hour, long count) {
    HeatmapRow r = org.mockito.Mockito.mock(HeatmapRow.class);
    when(r.getDow()).thenReturn(dow);
    when(r.getHour()).thenReturn(hour);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static CountryClickRow country(String name, long count) {
    CountryClickRow r = org.mockito.Mockito.mock(CountryClickRow.class);
    when(r.getCountry()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static DeviceClickRow device(String name, long count) {
    DeviceClickRow r = org.mockito.Mockito.mock(DeviceClickRow.class);
    when(r.getDevice()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static BrowserClickRow browser(String name, long count) {
    BrowserClickRow r = org.mockito.Mockito.mock(BrowserClickRow.class);
    when(r.getBrowser()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static ReferrerHostClickRow refHost(String name, long count) {
    ReferrerHostClickRow r = org.mockito.Mockito.mock(ReferrerHostClickRow.class);
    when(r.getHost()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static SourceChannelClickRow srcChannel(String name, long count) {
    SourceChannelClickRow r = org.mockito.Mockito.mock(SourceChannelClickRow.class);
    when(r.getSource()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static UtmCampaignClickRow utmCampaign(String name, long count) {
    UtmCampaignClickRow r = org.mockito.Mockito.mock(UtmCampaignClickRow.class);
    when(r.getCampaign()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static UtmSourceClickRow utmSource(String name, long count) {
    UtmSourceClickRow r = org.mockito.Mockito.mock(UtmSourceClickRow.class);
    when(r.getSource()).thenReturn(name);
    when(r.getCount()).thenReturn(count);
    return r;
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
