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
    List<UtmTermClick> utmTermClicks,
    List<SourceChannelClick> sourceChannelClicks,
    List<ClientAppClick> clientAppClicks,
    List<FetchSiteClick> fetchSiteClicks,
    List<PostClick> postClicks,
    List<ChannelDepth> channelDepth,
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

  public record UtmTermClick(String term, long count) {}

  public record SourceChannelClick(String source, long count) {}

  /**
   * Human clicks opened inside an in-app browser (KakaoTalk, Instagram, LINE …), keyed by the app
   * name. Ordinary browsers are not in this list at all — it answers "어디 앱 안에서 열렸나", not "무슨
   * 브라우저인가", and it is not a bot signal.
   */
  public record ClientAppClick(String app, long count) {}

  /**
   * Human clicks by the browser's {@code Sec-Fetch-Site} value ({@code none} = the visitor opened
   * it themselves by typing / bookmark / QR, {@code cross-site} = they followed a link from
   * somewhere else). Splits apart traffic that carries no referrer at all. Older browsers send
   * nothing, so those clicks are absent rather than bucketed.
   */
  public record FetchSiteClick(String fetchSite, long count) {}

  /**
   * Human clicks attributed to the blog post that embedded the link. {@code title} is null when the
   * post was deleted after the click was recorded — the count still counts.
   */
  public record PostClick(Long postId, String title, long count) {}

  /**
   * A referring channel read over time rather than as a single bar: how many people it sent, when
   * it first appeared, and how many of its visitors came back. Lets "instagram burned down in 4
   * hours" and "notion kept trickling for three days" look different instead of both being a
   * number.
   */
  public record ChannelDepth(
      String host,
      long count,
      java.time.Instant firstSeenAt,
      long returningVisitors,
      double returnRatio) {}

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
