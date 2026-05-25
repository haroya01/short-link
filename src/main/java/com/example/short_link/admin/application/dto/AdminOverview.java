package com.example.short_link.admin.application.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminOverview(
    Totals totals,
    long newUsers7d,
    long newLinks7d,
    long clicks7d,
    double anonymousLinkRatio,
    double expiredLinkRatio,
    double clicklessLinkRatio,
    List<DailyPoint> dailySignups,
    List<DailyPoint> dailyLinks,
    List<DailyPoint> dailyClicks,
    List<UserStat> topUsersByLinks,
    long topUsersByLinksTotal,
    List<UserStat> topUsersByClicks,
    long topUsersByClicksTotal,
    List<LinkStat> topLinksByClicks,
    long topLinksByClicksTotal) {

  public record Totals(long users, long links, long clicks) {}

  public record DailyPoint(LocalDate date, long count) {}

  public record UserStat(Long userId, String email, long count) {}

  public record LinkStat(String shortCode, long clickCount, String ownerEmail) {}
}
