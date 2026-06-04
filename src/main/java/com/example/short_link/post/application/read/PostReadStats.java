package com.example.short_link.post.application.read;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reader breakdown for a post (or a series' member posts) — who read it and from where, at the same
 * depth as the profile-visit dashboard. Field names mirror {@code ProfileStats} exactly so the same
 * frontend dashboard (ProfileStatsDashboard) renders it without a separate mapping. "visits" here =
 * post reads (post_view_event), enriched on the write path (V80).
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

  /** Empty stats for a post/series with no (human) reads yet — keeps the endpoint shape stable. */
  public static PostReadStats empty(String timezone) {
    return new PostReadStats(
        timezone, 0, 0, 0, 0, null, null, null, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
