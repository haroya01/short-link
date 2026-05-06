package com.example.short_link.link.application;

import java.time.LocalDate;
import java.util.List;

public record LinkStats(
    String shortCode,
    long totalClicks,
    List<DailyClick> dailyClicks,
    List<ReferrerClick> referrerClicks,
    List<DeviceClick> deviceClicks) {

  public record DailyClick(LocalDate date, long count) {}

  public record ReferrerClick(String referrer, long count) {}

  public record DeviceClick(String device, long count) {}
}
