package com.example.short_link.link.webhook.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_webhook")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkWebhookEntity extends BaseCreatedEntity {

  public static final int AUTO_DISABLE_FAILURE_THRESHOLD = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false, length = 64)
  private String secret;

  @Column(length = 100)
  private String name;

  @Column(nullable = false)
  private boolean enabled = true;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_mode", nullable = false, length = 32)
  private WebhookDeliveryMode deliveryMode = WebhookDeliveryMode.PER_EVENT;

  @Column(name = "summary_hour_of_day")
  private Integer summaryHourOfDay;

  @Column(name = "summary_last_sent_date")
  private LocalDate summaryLastSentDate;

  @Column(name = "spike_threshold")
  private Integer spikeThreshold;

  @Column(name = "spike_window_minutes")
  private Integer spikeWindowMinutes;

  @Column(name = "spike_last_fired_at")
  private Instant spikeLastFiredAt;

  public LinkWebhookEntity(LinkId linkId, String url, String secret, String name) {
    this(linkId, url, secret, name, WebhookFormat.GENERIC);
  }

  public LinkWebhookEntity(
      LinkId linkId, String url, String secret, String name, WebhookFormat format) {
    this.linkId = linkId == null ? null : linkId.value();
    this.url = url;
    this.secret = secret;
    this.name = name;
    this.enabled = true;
    this.format = format == null ? WebhookFormat.GENERIC : format;
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

  /**
   * Switch the hook's delivery mode + the mode-specific knobs. Caller passes only the values that
   * apply to the new mode; the entity nulls out the others to keep the row coherent (e.g. switching
   * from DAILY_SUMMARY to PER_EVENT clears the summary hour so a future re-enable doesn't fire
   * yesterday's stats at a stale hour).
   */
  public void changeDeliveryMode(
      WebhookDeliveryMode mode,
      Integer summaryHourOfDay,
      Integer spikeThreshold,
      Integer spikeWindowMinutes) {
    if (mode == null) return;
    this.deliveryMode = mode;
    if (mode.sendsDailySummary()) {
      if (summaryHourOfDay == null) {
        throw new IllegalArgumentException("summaryHourOfDay required for " + mode);
      }
      if (summaryHourOfDay < 0 || summaryHourOfDay > 23) {
        throw new IllegalArgumentException("summaryHourOfDay must be 0..23");
      }
      this.summaryHourOfDay = summaryHourOfDay;
    } else {
      this.summaryHourOfDay = null;
      this.summaryLastSentDate = null;
    }
    if (mode.sendsSpikeAlert()) {
      if (spikeThreshold == null || spikeThreshold < 1) {
        throw new IllegalArgumentException("spikeThreshold must be >= 1 for " + mode);
      }
      if (spikeWindowMinutes == null || spikeWindowMinutes < 1) {
        throw new IllegalArgumentException("spikeWindowMinutes must be >= 1 for " + mode);
      }
      this.spikeThreshold = spikeThreshold;
      this.spikeWindowMinutes = spikeWindowMinutes;
    } else {
      this.spikeThreshold = null;
      this.spikeWindowMinutes = null;
      this.spikeLastFiredAt = null;
    }
  }

  public void markSummarySent(LocalDate date) {
    this.summaryLastSentDate = date;
  }

  public void markSpikeFired(Instant at) {
    this.spikeLastFiredAt = at;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
