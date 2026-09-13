package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    String upper = clientCountry.toUpperCase(Locale.ROOT);
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

  /** 모든 지정 조건이 일치하는 목적지 중 조건 수가 가장 많은 것을 선택한다. 동률이면 가중 무작위 선택, 일치 항목이 없으면 원본 URL을 사용한다. */
  public Picked pick(String clientCountry, String os, String deviceClass) {
    List<Variant> enabled = variants.stream().filter(Variant::enabled).toList();
    if (enabled.isEmpty()) return new Picked(originalUrl, null);

    VisitorSignals visitor = VisitorSignals.normalized(clientCountry, os, deviceClass);

    int bestSpecificity = -1;
    List<Variant> winners = new ArrayList<>();
    for (Variant v : enabled) {
      Integer specificity = v.matchSpecificity(visitor);
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

  private record VisitorSignals(String country, String os, String deviceClass) {
    static VisitorSignals normalized(String country, String os, String deviceClass) {
      return new VisitorSignals(
          country == null ? null : country.trim().toUpperCase(Locale.ROOT),
          os == null ? null : os.trim().toLowerCase(Locale.ROOT),
          deviceClass == null ? null : deviceClass.trim().toLowerCase(Locale.ROOT));
    }
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

    /** 불일치 조건이 있으면 null, 아니면 지정된 조건 수를 반환한다. 조건이 없으면 0이다. */
    private Integer matchSpecificity(VisitorSignals visitor) {
      int score = 0;
      if (countryCode != null) {
        if (!countryCode.equals(visitor.country())) return null;
        score++;
      }
      if (os != null) {
        if (!os.equals(visitor.os())) return null;
        score++;
      }
      if (deviceClass != null) {
        if (!deviceClass.equals(visitor.deviceClass())) return null;
        score++;
      }
      return score;
    }

    public Variant(Long id, String url, int weight, boolean enabled, String countryCode) {
      this(id, url, weight, enabled, countryCode, null, null);
    }
  }

  public record Picked(String url, Long destinationId) {}
}
