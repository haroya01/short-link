package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record LinkStats(
    ShortCode shortCode,
    String timezone,
    long totalClicks,
    long humanClicks,
    long botClicks,
    long uniqueClicks,
    long previewClicks,
    long profileClicks,
    Instant firstClickAt,
    Instant lastClickAt,
    Long timeToFirstClickMinutes,
    Integer peakHour,
    Velocity velocity,
    ReturnRate returnRate,
    Lifecycle lifecycle,
    List<DailyClick> dailyClicks,
    List<HourClick> hourClicks,
    List<DayOfWeekClick> dayOfWeekClicks,
    List<HeatmapCell> heatmap,
    List<ReferrerClick> referrerClicks,
    List<ReferrerHostClick> referrerHostClicks,
    List<ChannelClick> channelClicks,
    List<DeviceClick> deviceClicks,
    List<OsClick> osClicks,
    List<BrowserClick> browserClicks,
    List<BotClick> botClicks2,
    List<UtmCampaignClick> utmCampaignClicks,
    List<UtmSourceClick> utmSourceClicks,
    List<UtmMediumClick> utmMediumClicks,
    List<UtmContentClick> utmContentClicks,
    List<SourceChannelClick> sourceChannelClicks,
    List<DestinationClick> destinationClicks,
    List<CountryClick> countryClicks,
    List<RegionClick> regionClicks,
    List<CityClick> cityClicks,
    List<LanguageClick> languageClicks,
    List<AsnClick> asnClicks,
    long datacenterClicks,
    List<Insight> insights) {

  public record DailyClick(LocalDate date, long count) {}

  public record HourClick(int hour, long count) {}

  public record DayOfWeekClick(String dayOfWeek, long count) {}

  public record HeatmapCell(String dayOfWeek, int hour, long count) {}

  public record ReferrerClick(String referrer, long count) {}

  public record ReferrerHostClick(String host, long count) {}

  public record ChannelClick(String channel, long count) {}

  public record DeviceClick(String device, long count) {}

  public record OsClick(String os, long count) {}

  public record BrowserClick(String browser, long count) {}

  public record BotClick(String bot, long count) {}

  public record UtmCampaignClick(String campaign, long count) {}

  public record UtmSourceClick(String source, long count) {}

  public record UtmMediumClick(String medium, long count) {}

  public record UtmContentClick(String content, long count) {}

  public record SourceChannelClick(String source, long count) {}

  public record DestinationClick(
      Long destinationId, String url, String label, int weight, boolean enabled, long count) {}

  public record CountryClick(String country, long count) {}

  public record RegionClick(String region, long count) {}

  public record CityClick(String city, long count) {}

  public record LanguageClick(String language, long count) {}

  public record AsnClick(Integer asn, String organization, long count) {}

  public record Velocity(long currentHour, double baselinePerHour, double ratio) {}

  public record ReturnRate(long newVisitors, long returningVisitors, double ratio) {}

  public record Lifecycle(List<DayClick> dayClicks, Integer halfLifeDays) {}

  public record DayClick(int day, long count) {}

  public record Insight(String type, String severity, String message, Map<String, Object> data) {}
}
