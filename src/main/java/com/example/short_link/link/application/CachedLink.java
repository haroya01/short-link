package com.example.short_link.link.application;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record CachedLink(
    Long linkId,
    String originalUrl,
    Instant expiresAt,
    String ogTitle,
    String ogDescription,
    String ogImage,
    List<Variant> variants) {

  public CachedLink(
      Long linkId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage) {
    this(linkId, originalUrl, expiresAt, ogTitle, ogDescription, ogImage, List.of());
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  /**
   * Picks the destination URL for this redirect. With no enabled variants, returns the original
   * (control). With variants, performs a weighted random pick over the enabled subset and returns
   * the chosen URL plus the destination_id so the click row can be attributed.
   */
  public Picked pick() {
    List<Variant> enabled = variants.stream().filter(Variant::enabled).toList();
    if (enabled.isEmpty()) return new Picked(originalUrl, null);
    int total = enabled.stream().mapToInt(Variant::weight).sum();
    if (total <= 0) return new Picked(originalUrl, null);
    int draw = ThreadLocalRandom.current().nextInt(total);
    int cumulative = 0;
    for (Variant v : enabled) {
      cumulative += v.weight();
      if (draw < cumulative) return new Picked(v.url(), v.id());
    }
    return new Picked(originalUrl, null);
  }

  public record Variant(Long id, String url, int weight, boolean enabled) {}

  public record Picked(String url, Long destinationId) {}
}
