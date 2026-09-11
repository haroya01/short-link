package com.example.short_link.link.webhook.domain;

import java.util.Arrays;
import java.util.List;

/**
 * How a webhook fires:
 *
 * <ul>
 *   <li>{@link #PER_EVENT} — POST on every matching click. Combine with {@code batchEnabled} for
 *       5-second buffering of raw events.
 *   <li>{@link #DAILY_SUMMARY} — one POST per day at the hook's {@code summaryHourOfDay}, carrying
 *       yesterday's aggregate stats.
 *   <li>{@link #THRESHOLD_SPIKE} — fires once when clicks within {@code spikeWindowMinutes} cross
 *       {@code spikeThreshold}. Cooldown via {@code spikeLastFiredAt} so a sustained spike doesn't
 *       spam.
 *   <li>{@link #BOTH} — DAILY_SUMMARY + THRESHOLD_SPIKE on the same hook.
 * </ul>
 *
 * <p>현재 단건 클릭 발송은 모든 모드에서 공통으로 수행한다. 이 모드는 요약·급증 알림의 추가 구독을 결정한다.
 */
public enum WebhookDeliveryMode {
  PER_EVENT,
  DAILY_SUMMARY,
  THRESHOLD_SPIKE,
  BOTH;

  public boolean sendsDailySummary() {
    return this == DAILY_SUMMARY || this == BOTH;
  }

  public boolean sendsSpikeAlert() {
    return this == THRESHOLD_SPIKE || this == BOTH;
  }

  public static List<WebhookDeliveryMode> dailySummaryModes() {
    return Arrays.stream(values()).filter(WebhookDeliveryMode::sendsDailySummary).toList();
  }
}
