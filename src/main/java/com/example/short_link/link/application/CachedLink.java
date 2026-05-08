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
   * Picks the destination URL for a country-agnostic redirect (treats every visitor the same).
   * Prefer {@link #pick(String)} when the caller has resolved the visitor's country — it lets
   * geo-tagged variants take effect.
   */
  public Picked pick() {
    return pick(null);
  }

  /**
   * Country-aware weighted pick. The order of preference:
   *
   * <ol>
   *   <li>If any enabled variant is tagged with {@code clientCountry}, pick by weight from those.
   *   <li>Otherwise, pick by weight from country-agnostic variants (countryCode == null).
   *   <li>If every enabled variant is country-tagged and none match, fall through to the original
   *       URL — owners get a deterministic "untargeted visitors hit the control" guarantee.
   * </ol>
   */
  public Picked pick(String clientCountry) {
    List<Variant> enabled = variants.stream().filter(Variant::enabled).toList();
    if (enabled.isEmpty()) return new Picked(originalUrl, null);

    if (clientCountry != null && !clientCountry.isBlank()) {
      String normalized = clientCountry.trim().toUpperCase();
      List<Variant> matched =
          enabled.stream().filter(v -> normalized.equals(v.countryCode())).toList();
      if (!matched.isEmpty()) return weightedPick(matched);
    }

    List<Variant> agnostic = enabled.stream().filter(v -> v.countryCode() == null).toList();
    if (!agnostic.isEmpty()) return weightedPick(agnostic);

    // Every variant is country-tagged but none matches → fall back to control.
    return new Picked(originalUrl, null);
  }

  private Picked weightedPick(List<Variant> pool) {
    int total = pool.stream().mapToInt(Variant::weight).sum();
    if (total <= 0) return new Picked(originalUrl, null);
    int draw = ThreadLocalRandom.current().nextInt(total);
    int cumulative = 0;
    for (Variant v : pool) {
      cumulative += v.weight();
      if (draw < cumulative) return new Picked(v.url(), v.id());
    }
    return new Picked(originalUrl, null);
  }

  public record Variant(Long id, String url, int weight, boolean enabled, String countryCode) {}

  public record Picked(String url, Long destinationId) {}
}
