package com.example.short_link.link.application;

import java.time.LocalDate;
import java.util.List;

public record LinkStats(
    String shortCode,
    long totalClicks,
    long humanClicks,
    long botClicks,
    List<DailyClick> dailyClicks,
    List<HourClick> hourClicks,
    List<DayOfWeekClick> dayOfWeekClicks,
    List<ReferrerClick> referrerClicks,
    List<ChannelClick> channelClicks,
    List<DeviceClick> deviceClicks,
    List<OsClick> osClicks,
    List<BrowserClick> browserClicks,
    List<UtmCampaignClick> utmCampaignClicks) {

  public record DailyClick(LocalDate date, long count) {}

  public record HourClick(int hour, long count) {}

  public record DayOfWeekClick(String dayOfWeek, long count) {}

  public record ReferrerClick(String referrer, long count) {}

  public record ChannelClick(String channel, long count) {}

  public record DeviceClick(String device, long count) {}

  public record OsClick(String os, long count) {}

  public record BrowserClick(String browser, long count) {}

  public record UtmCampaignClick(String campaign, long count) {}
}
