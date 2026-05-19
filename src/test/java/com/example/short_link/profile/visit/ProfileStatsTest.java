package com.example.short_link.profile.visit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.profile.visit.ProfileStats.BrowserVisit;
import com.example.short_link.profile.visit.ProfileStats.CountryVisit;
import com.example.short_link.profile.visit.ProfileStats.DailyVisit;
import com.example.short_link.profile.visit.ProfileStats.DeviceVisit;
import com.example.short_link.profile.visit.ProfileStats.HeatmapCell;
import com.example.short_link.profile.visit.ProfileStats.HourVisit;
import com.example.short_link.profile.visit.ProfileStats.ReferrerHostVisit;
import com.example.short_link.profile.visit.ProfileStats.SourceChannelVisit;
import com.example.short_link.profile.visit.ProfileStats.UtmCampaignVisit;
import com.example.short_link.profile.visit.ProfileStats.UtmSourceVisit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileStatsTest {

  @Test
  void recordExposesAllAggregates() {
    Instant first = Instant.parse("2024-01-01T00:00:00Z");
    Instant last = Instant.parse("2024-01-02T00:00:00Z");
    ProfileStats s =
        new ProfileStats(
            "Asia/Seoul",
            100L,
            80L,
            20L,
            45L,
            first,
            last,
            14,
            List.of(new DailyVisit(LocalDate.of(2024, 1, 1), 10L)),
            List.of(new HourVisit(9, 5L)),
            List.of(new HeatmapCell("MON", 9, 1L)),
            List.of(new CountryVisit("KR", 50L)),
            List.of(new DeviceVisit("Desktop", 30L)),
            List.of(new BrowserVisit("Chrome", 40L)),
            List.of(new ReferrerHostVisit("twitter.com", 7L)),
            List.of(new SourceChannelVisit("x", 7L)),
            List.of(new UtmCampaignVisit("launch", 5L)),
            List.of(new UtmSourceVisit("twitter", 5L)));
    assertThat(s.timezone()).isEqualTo("Asia/Seoul");
    assertThat(s.totalVisits()).isEqualTo(100L);
    assertThat(s.humanVisits()).isEqualTo(80L);
    assertThat(s.botVisits()).isEqualTo(20L);
    assertThat(s.uniqueVisits()).isEqualTo(45L);
    assertThat(s.firstVisitAt()).isEqualTo(first);
    assertThat(s.lastVisitAt()).isEqualTo(last);
    assertThat(s.peakHour()).isEqualTo(14);
    assertThat(s.dailyVisits()).extracting(DailyVisit::count).containsExactly(10L);
    assertThat(s.hourVisits()).extracting(HourVisit::hour).containsExactly(9);
    assertThat(s.heatmap()).extracting(HeatmapCell::dayOfWeek).containsExactly("MON");
    assertThat(s.countryVisits()).extracting(CountryVisit::country).containsExactly("KR");
    assertThat(s.deviceVisits()).extracting(DeviceVisit::device).containsExactly("Desktop");
    assertThat(s.browserVisits()).extracting(BrowserVisit::browser).containsExactly("Chrome");
    assertThat(s.referrerHostVisits())
        .extracting(ReferrerHostVisit::host)
        .containsExactly("twitter.com");
    assertThat(s.sourceChannelVisits()).extracting(SourceChannelVisit::source).containsExactly("x");
    assertThat(s.utmCampaignVisits())
        .extracting(UtmCampaignVisit::campaign)
        .containsExactly("launch");
    assertThat(s.utmSourceVisits()).extracting(UtmSourceVisit::source).containsExactly("twitter");
  }
}
