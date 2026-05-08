package com.example.short_link.user.application;

import java.time.Instant;
import java.util.List;

public record UserDataExport(
    ExportedUser user, List<ExportedLink> links, List<ExportedClick> clickEvents) {

  public record ExportedUser(
      Long id,
      String email,
      String oauthProvider,
      String role,
      String timezone,
      Instant createdAt) {}

  public record ExportedLink(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage) {}

  public record ExportedClick(
      String shortCode,
      Instant clickedAt,
      String referrerHost,
      String deviceClass,
      String osName,
      String browserName,
      String countryCode,
      String regionName,
      String cityName,
      String language,
      boolean bot,
      String botName) {}
}
