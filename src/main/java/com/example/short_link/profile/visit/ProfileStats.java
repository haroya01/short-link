package com.example.short_link.profile.visit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated visit stats for one profile owner — shape mirrors {@code LinkStats} so the same
 * frontend chart components (daily / heatmap / country table / device breakdown) can render it.
 * Some link-only fields (preview clicks, destinations, A/B variants, return rate, lifecycle,
 * insights) are omitted because they don't apply to profile visits.
 */
public record ProfileStats(
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
}
