package com.example.short_link.link.destination.application.dto;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import java.time.Instant;

public record DestinationSummary(
    Long id,
    String url,
    int weight,
    String label,
    boolean enabled,
    String countryCode,
    String deviceClass,
    String os,
    Instant createdAt) {
  public static DestinationSummary from(LinkDestinationEntity destination) {
    return new DestinationSummary(
        destination.getId(),
        destination.getUrl(),
        destination.getWeight(),
        destination.getLabel(),
        destination.isEnabled(),
        destination.getCountryCode(),
        destination.getDeviceClass(),
        destination.getOs(),
        destination.getCreatedAt());
  }
}
