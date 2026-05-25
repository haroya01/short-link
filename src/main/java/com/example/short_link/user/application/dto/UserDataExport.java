package com.example.short_link.user.application.dto;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.user.domain.UserEntity;
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
      Instant createdAt) {

    public static ExportedUser from(UserEntity u) {
      return new ExportedUser(
          u.getId(),
          u.getEmail(),
          u.getOauthProvider(),
          u.getRole().name(),
          u.getTimezone(),
          u.getCreatedAt());
    }
  }

  public record ExportedLink(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage) {

    public static ExportedLink from(LinkEntity l) {
      return new ExportedLink(
          l.getShortCode(),
          l.getOriginalUrl(),
          l.getCreatedAt(),
          l.getExpiresAt(),
          l.getOgTitle(),
          l.getOgDescription(),
          l.getOgImage());
    }
  }

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
      String botName) {

    public static ExportedClick from(ClickEventEntity c, String shortCode) {
      return new ExportedClick(
          shortCode,
          c.getClickedAt(),
          c.getReferrerHost(),
          c.getDeviceClass(),
          c.getOsName(),
          c.getBrowserName(),
          c.getCountryCode(),
          c.getRegionName(),
          c.getCityName(),
          c.getLanguage(),
          c.isBot(),
          c.getBotName());
    }
  }
}
