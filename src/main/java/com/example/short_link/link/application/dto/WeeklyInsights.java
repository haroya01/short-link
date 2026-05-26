package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;

public record WeeklyInsights(
    Instant from,
    Instant to,
    long totalClicks,
    long humanClicks,
    long previousHumanClicks,
    Double deltaPercent,
    Double humanRatio,
    TopLink topLink,
    Peak peak) {

  public record TopLink(
      ShortCode shortCode, String originalUrl, long clicks, String topUtmSource) {}

  public record Peak(Integer dayOfWeek, Integer hour, long clicks) {}
}
