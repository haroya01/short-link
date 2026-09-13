package com.example.short_link.post.application.read;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Uses the ProfileStats response shape for the shared dashboard. "Visits" are post reads from
 * {@code post_view_event}.
 */
public record PostReadStats(
    String timezone,
    long totalVisits,
    long humanVisits,
    long botVisits,
    long uniqueVisits,
    Instant firstVisitAt,
    Instant lastVisitAt,
    Integer peakHour,
    List<DailyVisit> dailyVisits,
    List<HourVisit> hourVisits,
    List<HeatmapCell> heatmap,
    List<CountryVisit> countryVisits,
    List<DeviceVisit> deviceVisits,
    List<BrowserVisit> browserVisits,
    List<ReferrerHostVisit> referrerHostVisits,
    List<SourceChannelVisit> sourceChannelVisits,
    List<UtmCampaignVisit> utmCampaignVisits,
    List<UtmSourceVisit> utmSourceVisits) {

  public record DailyVisit(LocalDate date, long count) {}

  public record HourVisit(int hour, long count) {}

  public record HeatmapCell(String dayOfWeek, int hour, long count) {}

  public record CountryVisit(String country, long count) {}

  public record DeviceVisit(String device, long count) {}

  public record BrowserVisit(String browser, long count) {}

  public record ReferrerHostVisit(String host, long count) {}

  public record SourceChannelVisit(String source, long count) {}

  public record UtmCampaignVisit(String campaign, long count) {}

  public record UtmSourceVisit(String source, long count) {}

  public static PostReadStats empty(String timezone) {
    return new PostReadStats(
        timezone, 0, 0, 0, 0, null, null, null, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
