package com.example.short_link.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One A/B variant for a link. When a link has any rows here, the redirect handler picks among
 * enabled variants by weight; otherwise it falls back to {@code link.original_url}. The original
 * URL is implicitly the "control" — owners can promote any variant by either bumping its weight or
 * replacing the original.
 *
 * <p>{@code countryCode} (ISO-3166 alpha-2, uppercase) gates the variant on the resolved client
 * country: a non-null value means "only pick this variant for visitors from that country". When at
 * least one country-matched variant exists for a request, the picker prefers them; otherwise it
 * falls back to country-agnostic (null) variants. If all variants are country-tagged and none
 * match, the redirect falls back to the original URL.
 */
@Entity
@Table(name = "link_destination")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkDestinationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false)
  private int weight = 1;

  @Column(length = 40)
  private String label;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  /**
   * Optional broad device bucket — {@code mobile}, {@code tablet}, {@code desktop}. Layered on top
   * of {@link #countryCode}: a variant tagged both {@code KR} and {@code mobile} only matches a
   * Korean visitor on a phone. Null = "no device constraint".
   */
  @Column(name = "device_class", length = 16)
  private String deviceClass;

  /**
   * Optional fine-grained OS — {@code ios}, {@code android}, {@code windows}, {@code macos}, {@code
   * linux}. Same layering rule as {@link #deviceClass}; null = "any OS".
   */
  @Column(length = 16)
  private String os;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public LinkDestinationEntity(
      Long linkId, String url, int weight, String label, String countryCode) {
    this(linkId, url, weight, label, countryCode, null, null);
  }

  public LinkDestinationEntity(
      Long linkId,
      String url,
      int weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    this.linkId = linkId;
    this.url = url;
    this.weight = Math.max(1, weight);
    this.label = label;
    this.enabled = true;
    this.countryCode = normalizeCountry(countryCode);
    this.deviceClass = normalizeDeviceClass(deviceClass);
    this.os = normalizeOs(os);
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public void update(
      String url, Integer weight, String label, Boolean enabled, String countryCode) {
    update(url, weight, label, enabled, countryCode, null, null);
  }

  public void update(
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    if (url != null) this.url = url;
    if (weight != null) this.weight = Math.max(1, weight);
    if (label != null) this.label = label;
    if (enabled != null) this.enabled = enabled;
    if (countryCode != null) this.countryCode = normalizeCountry(countryCode);
    if (deviceClass != null) this.deviceClass = normalizeDeviceClass(deviceClass);
    if (os != null) this.os = normalizeOs(os);
  }

  private static String normalizeCountry(String input) {
    if (input == null) return null;
    String trimmed = input.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.length() != 2) {
      throw new IllegalArgumentException("countryCode must be ISO-3166 alpha-2 (2 chars)");
    }
    return trimmed.toUpperCase();
  }

  private static String normalizeDeviceClass(String input) {
    if (input == null) return null;
    String v = input.trim().toLowerCase();
    if (v.isEmpty()) return null;
    return switch (v) {
      case "mobile", "tablet", "desktop" -> v;
      default ->
          throw new IllegalArgumentException("deviceClass must be mobile, tablet, or desktop");
    };
  }

  private static String normalizeOs(String input) {
    if (input == null) return null;
    String v = input.trim().toLowerCase();
    if (v.isEmpty()) return null;
    return switch (v) {
      case "ios", "android", "windows", "macos", "linux" -> v;
      default ->
          throw new IllegalArgumentException("os must be ios, android, windows, macos, or linux");
    };
  }
}
