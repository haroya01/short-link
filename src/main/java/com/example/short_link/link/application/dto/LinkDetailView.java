package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;

public record LinkDetailView(
    ShortCode shortCode,
    String originalUrl,
    Instant expiresAt,
    String ogTitle,
    String ogDescription,
    String ogImage,
    String ogTitleOverride,
    String ogDescriptionOverride,
    String ogImageOverride,
    boolean passwordProtected,
    Integer maxViews,
    int viewCount,
    boolean statsPublic,
    List<String> tags,
    String note,
    String expiredMessage,
    boolean openInBrowser,
    Splash splash,
    Instant opensAt,
    DestinationHealth destinationHealth,
    Moderation moderation) {

  public record Moderation(String reason, Instant disabledAt) {}

  public record DestinationHealth(
      boolean broken, String failure, Integer httpStatus, Instant brokenSince, Instant checkedAt) {}

  public record Splash(boolean enabled, String message, int seconds, Long ctaId) {
    public static final Splash OFF = new Splash(false, null, 3, null);
  }
}
