package com.example.short_link.link.domain;

import com.example.short_link.link.application.WebhookFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_webhook")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkWebhookEntity {

  public static final int AUTO_DISABLE_FAILURE_THRESHOLD = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false, length = 64)
  private String secret;

  @Column(length = 100)
  private String name;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_called_at")
  private Instant lastCalledAt;

  @Column(name = "last_status_code")
  private Integer lastStatusCode;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "include_bots", nullable = false)
  private boolean includeBots = false;

  @Column(name = "sample_rate", nullable = false)
  private int sampleRate = 100;

  @Column(name = "batch_enabled", nullable = false)
  private boolean batchEnabled = false;

  @Column(name = "daily_quota")
  private Integer dailyQuota;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures = 0;

  @Column(name = "auto_disabled_reason", length = 200)
  private String autoDisabledReason;

  @Column(name = "referrer_host_filter", length = 255)
  private String referrerHostFilter;

  @Column(name = "utm_source_filter", length = 100)
  private String utmSourceFilter;

  @Enumerated(EnumType.STRING)
  @Column(name = "format", nullable = false, length = 16)
  private WebhookFormat format = WebhookFormat.GENERIC;

  public LinkWebhookEntity(Long linkId, String url, String secret, String name) {
    this(linkId, url, secret, name, WebhookFormat.GENERIC);
  }

  public LinkWebhookEntity(
      Long linkId, String url, String secret, String name, WebhookFormat format) {
    this.linkId = linkId;
    this.url = url;
    this.secret = secret;
    this.name = name;
    this.enabled = true;
    this.format = format == null ? WebhookFormat.GENERIC : format;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public void disable() {
    this.enabled = false;
  }

  public void changeFormat(WebhookFormat format) {
    this.format = format == null ? WebhookFormat.GENERIC : format;
  }

  /**
   * Clears the failure trail so a hook that auto-disabled on a stale reason (e.g. the payload-shape
   * mismatch fixed by re-detecting {@link WebhookFormat}) can fire again. Distinct from {@link
   * #enable()} because we also want {@code lastError} blanked — leaving the old error string
   * dangling on a freshly-revived hook is misleading in the dashboard.
   */
  public void resetFailureState() {
    this.enabled = true;
    this.consecutiveFailures = 0;
    this.autoDisabledReason = null;
    this.lastError = null;
  }

  public void enable() {
    this.enabled = true;
    this.consecutiveFailures = 0;
    this.autoDisabledReason = null;
  }

  public void recordSuccess(int status) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = null;
    this.consecutiveFailures = 0;
  }

  public void recordFailure(Integer status, String error) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    this.consecutiveFailures += 1;
    if (this.consecutiveFailures >= AUTO_DISABLE_FAILURE_THRESHOLD && this.enabled) {
      this.enabled = false;
      this.autoDisabledReason =
          "auto-disabled after "
              + AUTO_DISABLE_FAILURE_THRESHOLD
              + " consecutive failures: "
              + (this.lastError == null ? "(no detail)" : truncate(this.lastError, 150));
    }
  }

  public void updateConfig(
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

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
