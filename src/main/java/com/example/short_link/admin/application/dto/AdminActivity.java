package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Live activity feed for the admin console: the newest links and clicks across all users, plus the
 * links picking up the most human traffic in the last 24h. Deliberately PII-minimal — click rows
 * carry only coarse dimensions (country code, referrer host, device class), never IP or visitor
 * hash.
 */
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
