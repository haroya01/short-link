package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record CachedLink(
    LinkId linkId,
    ShortCode shortCode,
    Long userId,
    String originalUrl,
    Instant expiresAt,
    String ogTitle,
    String ogDescription,
    String ogImage,
    String blockedCountries,
    boolean passwordRequired,
    Integer maxViews,
    String expiredMessage,
    List<Variant> variants) {

  public CachedLink {
    variants = variants == null ? List.of() : List.copyOf(variants);
  }

  public CachedLink(
      LinkId linkId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage) {
    this(
        linkId,
        null,
        null,
        originalUrl,
        expiresAt,
        ogTitle,
        ogDescription,
        ogImage,
        null,
        false,
        null,
        null,
        List.of());
  }

  public CachedLink(
      LinkId linkId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage,
      List<Variant> variants) {
    this(
        linkId,
        null,
        null,
        originalUrl,
        expiresAt,
        ogTitle,
        ogDescription,
        ogImage,
        null,
        false,
        null,
        null,
        variants);
  }

  public CachedLink(
      LinkId linkId,
      Long userId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage,
      List<Variant> variants) {
    this(
        linkId,
        null,
        userId,
        originalUrl,
        expiresAt,
        ogTitle,
        ogDescription,
        ogImage,
        null,
        false,
        null,
        null,
        variants);
  }

  public CachedLink(
      LinkId linkId,
      Long userId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage,
      String blockedCountries,
      List<Variant> variants) {
    this(
        linkId,
        null,
        userId,
        originalUrl,
        expiresAt,
        ogTitle,
        ogDescription,
        ogImage,
        blockedCountries,
        false,
        null,
        null,
        variants);
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  public boolean isBlockedFor(String clientCountry) {
    if (blockedCountries == null || clientCountry == null) return false;
    String upper = clientCountry.toUpperCase();
    for (String code : blockedCountries.split(",")) {
      if (upper.equals(code.trim())) return true;
    }
    return false;
  }

  public Picked pick() {
    return pick(null, null, null);
  }

  public Picked pick(String clientCountry) {
    return pick(clientCountry, null, null);
  }

  /**
   * Picks one destination using a layered match. Each variant's non-null predicates (country, OS,
   * device class) must match the visitor's signals. Among matching variants, the most-specific set
   * (most non-null predicates satisfied) wins; ties resolve by weighted random pick. If nothing
   * matches at all, fall through to the link's control URL.
   */
  public Picked pick(String clientCountry, String os, String deviceClass) {
    List<Variant> enabled = variants.stream().filter(Variant::enabled).toList();
    if (enabled.isEmpty()) return new Picked(originalUrl, null);

    String country = clientCountry == null ? null : clientCountry.trim().toUpperCase();
    String osLower = os == null ? null : os.trim().toLowerCase();
    String dcLower = deviceClass == null ? null : deviceClass.trim().toLowerCase();

    int bestSpecificity = -1;
    List<Variant> winners = new ArrayList<>();
    for (Variant v : enabled) {
      Integer specificity = matchSpecificity(v, country, osLower, dcLower);
      if (specificity == null) continue;
      if (specificity > bestSpecificity) {
        bestSpecificity = specificity;
        winners.clear();
        winners.add(v);
      } else if (specificity == bestSpecificity) {
        winners.add(v);
      }
    }
    if (winners.isEmpty()) return new Picked(originalUrl, null);
    return weightedPick(winners);
  }

  /**
   * Returns the number of predicates a variant matches against the visitor signals, or {@code null}
   * if any non-null predicate fails. A variant with no predicates set always matches with
   * specificity 0 (fully generic).
   */
  private static Integer matchSpecificity(
      Variant v, String country, String os, String deviceClass) {
    int score = 0;
    if (v.countryCode() != null) {
      if (!v.countryCode().equals(country)) return null;
      score++;
    }
    if (v.os() != null) {
      if (!v.os().equals(os)) return null;
      score++;
    }
    if (v.deviceClass() != null) {
      if (!v.deviceClass().equals(deviceClass)) return null;
      score++;
    }
    return score;
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

  public record Variant(
      Long id,
      String url,
      int weight,
      boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {

    public Variant(Long id, String url, int weight, boolean enabled, String countryCode) {
      this(id, url, weight, enabled, countryCode, null, null);
    }
  }

  public record Picked(String url, Long destinationId) {}
}
