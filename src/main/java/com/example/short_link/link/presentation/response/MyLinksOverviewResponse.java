package com.example.short_link.link.presentation.response;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLinksOverview;
import java.time.Instant;
import java.util.List;

public record MyLinksOverviewResponse(
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
    List<MyLinksOverview.DayClick> dailyClicks,
    MyLinksOverview.Peak peak,
    List<MyLinkResponse> topLinks,
    List<MyLinkResponse> weekTopLinks) {
  public static MyLinksOverviewResponse from(MyLinksOverview value, ShortLinkUrlBuilder urls) {
    return new MyLinksOverviewResponse(
        value.totalLinks(),
        value.totalClicks(),
        value.humanClicks(),
        value.clicks7d(),
        value.previousClicks7d(),
        value.clicksToday(),
        value.zeroClickLinks(),
        value.expiringLinks(),
        value.timezone(),
        value.updatedAt(),
        value.dailyClicks(),
        value.peak(),
        value.topLinks().stream().map(link -> MyLinkResponse.from(link, urls)).toList(),
        value.weekTopLinks().stream().map(link -> MyLinkResponse.from(link, urls)).toList());
  }
}
