package com.example.short_link.link.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Delivery-gate inputs grouped into one embeddable so the entity stops growing a flat column wall.
 * Same table as {@code link_webhook} — no schema change, just JPA mapping reshape via {@link
 * jakarta.persistence.Embedded}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WebhookFilter {

  @Column(name = "include_bots", nullable = false)
  private boolean includeBots = false;

  @Column(name = "sample_rate", nullable = false)
  private int sampleRate = 100;

  @Column(name = "batch_enabled", nullable = false)
  private boolean batchEnabled = false;

  @Column(name = "daily_quota")
  private Integer dailyQuota;

  @Column(name = "referrer_host_filter", length = 255)
  private String referrerHostFilter;

  @Column(name = "utm_source_filter", length = 100)
  private String utmSourceFilter;

  /**
   * Mirror of the legacy {@code LinkWebhookEntity.updateConfig} contract: nulls leave fields
   * untouched.
   */
  public void update(
      Boolean includeBots,
      Integer sampleRate,
      Boolean batchEnabled,
      Integer dailyQuota,
      String referrerHostFilter,
      String utmSourceFilter) {
    if (includeBots != null) this.includeBots = includeBots;
    if (sampleRate != null) this.sampleRate = clamp(sampleRate, 1, 100);
    if (batchEnabled != null) this.batchEnabled = batchEnabled;
    if (dailyQuota != null) this.dailyQuota = dailyQuota <= 0 ? null : dailyQuota;
    if (referrerHostFilter != null)
      this.referrerHostFilter = referrerHostFilter.isBlank() ? null : referrerHostFilter.trim();
    if (utmSourceFilter != null)
      this.utmSourceFilter = utmSourceFilter.isBlank() ? null : utmSourceFilter.trim();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
