package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

/** 클릭 기록에는 IP와 방문자 해시를 노출하지 않는다. */
public record AdminActivity(
    List<RecentLink> recentLinks, List<RecentClick> recentClicks, List<TrendingLink> trending24h) {

  public record RecentLink(
      String shortCode, String originalUrl, String ownerEmail, Instant createdAt) {}

  public record RecentClick(
      String shortCode,
      Instant clickedAt,
      String country,
      String referrerHost,
      String deviceClass) {}

  public record TrendingLink(String shortCode, String ownerEmail, long clickCount) {}
}
