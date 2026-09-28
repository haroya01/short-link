package com.example.short_link.link.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Account-wide summary: totalClicks includes bots; humanClicks and all daily buckets exclude bots.
// dailyClicks contains seven owner-local dates, including the partial current day. Expiring links
// have an expiry in the inclusive interval [updatedAt, updatedAt + 3 days]. previousClicks7d covers
// the same owner-local span one week earlier, cut at the same time of day. peak and weekTopLinks
// cover the seven dates; topLinks ranks human clicks since creation.
public record MyLinksOverview(
    long totalLinks,
    long totalClicks,
    long humanClicks,
    long clicks7d,
    long previousClicks7d,
    long clicksToday,
    long zeroClickLinks,
    long expiringLinks,
    String timezone,
    Instant updatedAt,
    List<DayClick> dailyClicks,
    Peak peak,
    List<MyLink> topLinks,
    List<MyLink> weekTopLinks) {
  public record DayClick(LocalDate date, long count) {}

  public record Peak(int dayOfWeek, int hour, long clicks) {}
}
