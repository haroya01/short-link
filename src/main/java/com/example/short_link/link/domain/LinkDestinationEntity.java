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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public LinkDestinationEntity(
      Long linkId, String url, int weight, String label, String countryCode) {
    this.linkId = linkId;
    this.url = url;
    this.weight = Math.max(1, weight);
    this.label = label;
    this.enabled = true;
    this.countryCode = normalizeCountry(countryCode);
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public void update(
      String url, Integer weight, String label, Boolean enabled, String countryCode) {
    if (url != null) this.url = url;
    if (weight != null) this.weight = Math.max(1, weight);
    if (label != null) this.label = label;
    if (enabled != null) this.enabled = enabled;
    if (countryCode != null) {
      this.countryCode = normalizeCountry(countryCode);
    }
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
}
