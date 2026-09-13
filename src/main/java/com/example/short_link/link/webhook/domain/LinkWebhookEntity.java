package com.example.short_link.link.webhook.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

  @Column(nullable = false, length = 255)
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

  @Embedded private WebhookFilter filter = new WebhookFilter();

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures = 0;

  @Column(name = "auto_disabled_reason", length = 200)
  private String autoDisabledReason;

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

  public void resetFailureState() {
    this.enabled = true;
    this.consecutiveFailures = 0;
    this.autoDisabledReason = null;
    this.lastError = null;
  }

  public record FormatRedetection(boolean changed, boolean reactivated) {}

  /** 포맷이 바뀐 자동 비활성 훅만 복구한다. 사용자가 직접 끈 훅은 그대로 둔다. */
  public FormatRedetection redetectFormat() {
    WebhookFormat detected = WebhookFormat.detect(url);
    if (detected == format) return new FormatRedetection(false, false);
    boolean reactivate =
        !enabled && autoDisabledReason != null && detected != WebhookFormat.GENERIC;
    changeFormat(detected);
    if (reactivate) resetFailureState();
    return new FormatRedetection(true, reactivate);
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
    filter.update(
        includeBots, sampleRate, batchEnabled, dailyQuota, referrerHostFilter, utmSourceFilter);
  }

  public boolean isIncludeBots() {
    return filter.isIncludeBots();
  }

  public int getSampleRate() {
    return filter.getSampleRate();
  }

  public boolean isBatchEnabled() {
    return filter.isBatchEnabled();
  }

  public Integer getDailyQuota() {
    return filter.getDailyQuota();
  }

  public String getReferrerHostFilter() {
    return filter.getReferrerHostFilter();
  }

  public String getUtmSourceFilter() {
    return filter.getUtmSourceFilter();
  }

  /** 새 전송 모드에서 사용하지 않는 설정은 지워 다음 모드 전환에 남지 않게 한다. */
  public void changeDeliveryMode(
      WebhookDeliveryMode mode,
      Integer summaryHourOfDay,
      Integer spikeThreshold,
      Integer spikeWindowMinutes) {
    if (mode == null) return;
    validateDeliveryMode(mode, summaryHourOfDay, spikeThreshold, spikeWindowMinutes);
    this.deliveryMode = mode;
    if (mode.sendsDailySummary()) {
      this.summaryHourOfDay = summaryHourOfDay;
    } else {
      this.summaryHourOfDay = null;
      this.summaryLastSentDate = null;
    }
    if (mode.sendsSpikeAlert()) {
      this.spikeThreshold = spikeThreshold;
      this.spikeWindowMinutes = spikeWindowMinutes;
    } else {
      this.spikeThreshold = null;
      this.spikeWindowMinutes = null;
      this.spikeLastFiredAt = null;
    }
  }

  private static void validateDeliveryMode(
      WebhookDeliveryMode mode,
      Integer summaryHourOfDay,
      Integer spikeThreshold,
      Integer spikeWindowMinutes) {
    if (mode.sendsDailySummary()) {
      if (summaryHourOfDay == null) {
        throw new IllegalArgumentException("summaryHourOfDay required for " + mode);
      }
      if (summaryHourOfDay < 0 || summaryHourOfDay > 23) {
        throw new IllegalArgumentException("summaryHourOfDay must be 0..23");
      }
    }
    if (mode.sendsSpikeAlert()) {
      if (spikeThreshold == null || spikeThreshold < 1) {
        throw new IllegalArgumentException("spikeThreshold must be >= 1 for " + mode);
      }
      if (spikeWindowMinutes == null || spikeWindowMinutes < 1) {
        throw new IllegalArgumentException("spikeWindowMinutes must be >= 1 for " + mode);
      }
    }
  }

  public void markSummarySent(LocalDate date) {
    this.summaryLastSentDate = date;
  }

  public void markSpikeFired(Instant at) {
    this.spikeLastFiredAt = at;
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
